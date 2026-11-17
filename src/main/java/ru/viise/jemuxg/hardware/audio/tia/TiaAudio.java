package ru.viise.jemuxg.hardware.audio.tia;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.AudioProcessor;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Два аудиоканала TIA (NTSC Atari 2600).
 *
 * <p>Алгоритм полиномиальных счётчиков повторяет Stella. Частота
 * дискретизации — 44 100 Гц, выход пропускается через простой
 * high-pass фильтр для удаления постоянной составляющей.</p>
 */
public final class TiaAudio implements AudioProcessor {

    public static final int AUDC0 = 0x15;
    public static final int AUDC1 = 0x16;
    public static final int AUDF0 = 0x17;
    public static final int AUDF1 = 0x18;
    public static final int AUDV0 = 0x19;
    public static final int AUDV1 = 0x1A;

    private static final int SAMPLE_RATE = Atari2600Constants.AUDIO_SAMPLE_RATE;
    private static final int COLOR_CLOCKS_PER_LINE = Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE;
    private static final int[] MIX = mixTable();

    private final Channel channel0 = new Channel();
    private final Channel channel1 = new Channel();
    private final short[] buffer = new short[1024];

    private AudioSink sink;
    private int audioClock;
    private long sampleAccumulator;
    private long mixAccumulator;
    private int mixCount;
    private int previousMix;
    private int dcAccumulator;
    private int bufferPosition;

    public TiaAudio() {
        reset();
    }

    @Override
    public void connect(AudioSink sink) {
        this.sink = sink;
    }

    @Override
    public int sampleRate() {
        return SAMPLE_RATE;
    }

    @Override
    public String name() {
        return "TIA Audio";
    }

    /**
     * Записывает регистр аудиоканала по адресу TIA.
     */
    public void writeRegister(int register, int value) {
        switch (register) {
            case AUDC0 -> channel0.audc = value & 0x0F;
            case AUDC1 -> channel1.audc = value & 0x0F;
            case AUDF0 -> channel0.audf = value & 0x1F;
            case AUDF1 -> channel1.audf = value & 0x1F;
            case AUDV0 -> channel0.audv = value & 0x0F;
            case AUDV1 -> channel1.audv = value & 0x0F;
            default -> {
            }
        }
    }

    @Override
    public void tick(long colorClocks) {
        if (colorClocks < 0) {
            throw new IllegalArgumentException("colorClocks must be non-negative");
        }
        for (long i = 0; i < colorClocks; i++) {
            audioClock++;
            if (audioClock >= COLOR_CLOCKS_PER_LINE) {
                audioClock = 0;
            }

            if (audioClock == 9 || audioClock == 81) {
                channel0.phase0();
                channel1.phase0();
            }
            if (audioClock == 37 || audioClock == 149) {
                channel0.phase1();
                channel1.phase1();
            }

            mixAccumulator += channel0.volume() + channel1.volume();
            mixCount++;
            sampleAccumulator += SAMPLE_RATE;
            while (sampleAccumulator >= Atari2600Constants.COLOR_CLOCK_HZ) {
                sampleAccumulator -= Atari2600Constants.COLOR_CLOCK_HZ;
                int average = mixCount == 0 ? 0
                        : (int) Math.round((double) mixAccumulator / mixCount);
                mixAccumulator = 0;
                mixCount = 0;
                pushSample(average);
            }
        }
    }

    /**
     * Отправляет накопленные сэмплы в AudioSink.
     */
    public void flush() {
        if (sink != null && bufferPosition > 0) {
            sink.samplesReady(buffer, bufferPosition);
        }
        bufferPosition = 0;
    }

    @Override
    public void reset() {
        channel0.reset();
        channel1.reset();
        audioClock = 0;
        sampleAccumulator = 0;
        mixAccumulator = 0;
        mixCount = 0;
        previousMix = 0;
        dcAccumulator = 0;
        bufferPosition = 0;
    }

    @Override
    public void save(StateWriter writer) {
        channel0.save(writer);
        channel1.save(writer);
        writer.writeInt(audioClock);
        writer.writeLong(sampleAccumulator);
        writer.writeLong(mixAccumulator);
        writer.writeInt(mixCount);
        writer.writeInt(previousMix);
        writer.writeInt(dcAccumulator);
        writer.writeInt(bufferPosition);
        byte[] samples = new byte[bufferPosition * 2];
        for (int i = 0; i < bufferPosition; i++) {
            samples[i * 2] = (byte) (buffer[i] & 0xFF);
            samples[i * 2 + 1] = (byte) ((buffer[i] >> 8) & 0xFF);
        }
        writer.writeBytes(samples);
    }

    @Override
    public void restore(StateReader reader) {
        channel0.restore(reader);
        channel1.restore(reader);
        audioClock = reader.readInt();
        sampleAccumulator = reader.readLong();
        mixAccumulator = reader.readLong();
        mixCount = reader.readInt();
        previousMix = reader.readInt();
        dcAccumulator = reader.readInt();
        bufferPosition = reader.readInt();
        if (bufferPosition < 0 || bufferPosition > buffer.length) {
            throw new IllegalArgumentException("invalid TIA audio buffer position: " + bufferPosition);
        }
        byte[] samples = reader.readBytes();
        if (samples.length != bufferPosition * 2) {
            throw new IllegalArgumentException("invalid TIA audio buffer length: " + samples.length);
        }
        for (int i = 0; i < bufferPosition; i++) {
            buffer[i] = (short) ((samples[i * 2] & 0xFF) | (samples[i * 2 + 1] << 8));
        }
    }

    private void pushSample(int volume) {
        int mixed = MIX[Math.max(0, Math.min(30, volume))];
        int difference = mixed - previousMix;
        previousMix = mixed;
        dcAccumulator += difference - (dcAccumulator >> 10);
        int sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, dcAccumulator));
        buffer[bufferPosition++] = (short) sample;
        if (bufferPosition == buffer.length) {
            flush();
        }
    }

    private static int[] mixTable() {
        int[] table = new int[31];
        for (int i = 1; i < table.length; i++) {
            table[i] = (int) Math.floor(32767.0 * i / 30.0 * 60.0 / (30.0 + i));
        }
        return table;
    }

    private static final class Channel {

        private int audc;
        private int audf;
        private int audv;
        private boolean clockEnable;
        private boolean noiseFeedback;
        private boolean noiseCounterBit4;
        private boolean pulseCounterHold;
        private int dividerCounter;
        private int pulseCounter;
        private int noiseCounter;

        private void reset() {
            audc = 0;
            audf = 0;
            audv = 0;
            clockEnable = false;
            noiseFeedback = false;
            noiseCounterBit4 = false;
            pulseCounterHold = false;
            dividerCounter = 0;
            pulseCounter = 0;
            noiseCounter = 0;
        }

        private void phase0() {
            if (clockEnable) {
                noiseCounterBit4 = (noiseCounter & 1) != 0;
                switch (audc & 0x03) {
                    case 0, 1 -> pulseCounterHold = false;
                    case 2 -> pulseCounterHold = (noiseCounter & 0x1E) != 0x02;
                    default -> pulseCounterHold = !noiseCounterBit4;
                }
                if ((audc & 0x03) == 0) {
                    noiseFeedback = ((pulseCounter ^ noiseCounter) & 1) != 0
                            || !(noiseCounter != 0 || pulseCounter != 0x0A)
                            || (audc & 0x0C) == 0;
                } else {
                    noiseFeedback = (((noiseCounter & 0x04) != 0 ? 1 : 0)
                            ^ (noiseCounter & 1)) != 0
                            || noiseCounter == 0;
                }
            }

            clockEnable = dividerCounter == audf;
            if (dividerCounter == audf || dividerCounter == 0x1F) {
                dividerCounter = 0;
            } else {
                dividerCounter++;
            }
        }

        private void phase1() {
            if (clockEnable) {
                boolean pulseFeedback = false;
                switch (audc >> 2) {
                    case 0 -> pulseFeedback = (((pulseCounter & 0x02) != 0 ? 1 : 0)
                            ^ (pulseCounter & 1)) != 0
                            && pulseCounter != 0x0A
                            && (audc & 0x03) != 0;
                    case 1 -> pulseFeedback = (pulseCounter & 0x08) == 0;
                    case 2 -> pulseFeedback = !noiseCounterBit4;
                    default -> pulseFeedback = !((pulseCounter & 0x02) != 0
                            || (pulseCounter & 0x0E) == 0);
                }

                noiseCounter >>= 1;
                if (noiseFeedback) {
                    noiseCounter |= 0x10;
                }
                if (!pulseCounterHold) {
                    pulseCounter = (~(pulseCounter >> 1)) & 0x07;
                    if (pulseFeedback) {
                        pulseCounter |= 0x08;
                    }
                }
            }
        }

        private int volume() {
            return (pulseCounter & 1) * audv;
        }

        private void save(StateWriter writer) {
            writer.writeByte(audc);
            writer.writeByte(audf);
            writer.writeByte(audv);
            writer.writeByte(clockEnable ? 1 : 0);
            writer.writeByte(noiseFeedback ? 1 : 0);
            writer.writeByte(noiseCounterBit4 ? 1 : 0);
            writer.writeByte(pulseCounterHold ? 1 : 0);
            writer.writeByte(dividerCounter);
            writer.writeByte(pulseCounter);
            writer.writeByte(noiseCounter);
        }

        private void restore(StateReader reader) {
            audc = reader.readByte();
            audf = reader.readByte();
            audv = reader.readByte();
            clockEnable = reader.readByte() != 0;
            noiseFeedback = reader.readByte() != 0;
            noiseCounterBit4 = reader.readByte() != 0;
            pulseCounterHold = reader.readByte() != 0;
            dividerCounter = reader.readByte();
            pulseCounter = reader.readByte();
            noiseCounter = reader.readByte();
        }
    }
}
