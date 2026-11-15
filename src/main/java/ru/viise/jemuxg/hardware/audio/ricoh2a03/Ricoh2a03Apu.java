package ru.viise.jemuxg.hardware.audio.ricoh2a03;

import java.util.Arrays;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.AudioProcessor;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Bus;

/**
 * APU NES (Ricoh 2A03).
 * <p>
 * Реализованы pulse1/pulse2, triangle, noise, DMC, frame counter,
 * чтение статуса $4015 и микширование в PCM. Точная эмуляция DMA-задержек
 * DMC и всех аналоговых цепей не является целью этого этапа.
 */
public final class Ricoh2a03Apu implements AudioProcessor {

    public static final int SAMPLE_RATE = 44_100;
    private static final int CPU_HZ = 1_789_773;
    private static final int SAMPLES_PER_BUFFER = 735;

    private final PulseChannel pulse1 = new PulseChannel(true);
    private final PulseChannel pulse2 = new PulseChannel(false);
    private final TriangleChannel triangle = new TriangleChannel();
    private final NoiseChannel noise = new NoiseChannel();
    private final DmcChannel dmc = new DmcChannel();

    private final short[] buffer = new short[SAMPLES_PER_BUFFER];
    private int bufferPosition;

    private final Bus registerBus = new Bus() {
        @Override
        public int read(int address) {
            return readRegister(address);
        }

        @Override
        public void write(int address, int value) {
            writeRegister(address, value & 0xFF);
        }
    };

    private AudioSink sink;
    private long sampleAccumulator;
    private long emittedSamples;
    private int previousMix;
    private int dcAccumulator;
    private int frameClock;
    private boolean frameIrq;
    private boolean frameInhibit;
    private int frameMode;

    public Bus registers() {
        return registerBus;
    }

    /**
     * Шина CPU нужна DMC для чтения сэмплов.
     */
    public void connectCpuBus(Bus bus) {
        dmc.setCpuBus(bus);
    }

    /**
     * Отправить накопленные сэмплы в sink. Вызывается в конце кадра.
     */
    public void flush() {
        if (bufferPosition == 0) {
            return;
        }
        if (sink != null) {
            sink.samplesReady(buffer, bufferPosition);
            emittedSamples += bufferPosition;
        }
        bufferPosition = 0;
    }

    @Override
    public String name() {
        return "Ricoh 2A03 APU";
    }

    @Override
    public void reset() {
        pulse1.reset();
        pulse2.reset();
        triangle.reset();
        noise.reset();
        dmc.reset();
        Arrays.fill(buffer, (short) 0);
        bufferPosition = 0;
        sampleAccumulator = 0;
        emittedSamples = 0;
        previousMix = 0;
        dcAccumulator = 0;
        frameClock = 0;
        frameIrq = false;
        frameInhibit = false;
        frameMode = 0;
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
    public void tick(long delta) {
        for (long i = 0; i < delta; i++) {
            pulse1.tick();
            pulse2.tick();
            triangle.tick();
            noise.tick();
            dmc.tick();
            clockFrameCounter();

            sampleAccumulator += SAMPLE_RATE;
            if (sampleAccumulator >= CPU_HZ) {
                sampleAccumulator -= CPU_HZ;
                pushSample();
            }
        }
    }

    private void pushSample() {
        if (bufferPosition >= buffer.length) {
            flush();
        }
        int mixed = mix();
        // Убираем постоянную составляющую: выход микшера однополярный,
        // а звуковой тракт ожидает сигнал вокруг нуля.
        int diff = mixed - previousMix;
        previousMix = mixed;
        dcAccumulator += diff - (dcAccumulator >> 10);
        int value = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, dcAccumulator));
        buffer[bufferPosition++] = (short) value;
    }

    private short mix() {
        int pulseOut = pulse1.output() + pulse2.output();
        int triangleOut = triangle.output();
        int noiseOut = noise.output();
        int dmcOut = dmc.output();

        double pulse = pulseOut == 0
                ? 0.0
                : 95.88 / (8128.0 / pulseOut + 100.0);
        double tndBase = triangleOut / 8227.0 + noiseOut / 12241.0 + dmcOut / 22638.0;
        double tnd = tndBase == 0.0
                ? 0.0
                : 159.79 / (1.0 / tndBase + 100.0);

        int value = (int) Math.round((pulse + tnd) * 32767.0);
        if (value < 0) {
            value = 0;
        } else if (value > 32767) {
            value = 32767;
        }
        return (short) value;
    }

    private void clockFrameCounter() {
        frameClock++;
        if (frameMode == 0) {
            if (frameClock == 7457 || frameClock == 14913
                    || frameClock == 22371 || frameClock == 29829) {
                quarterFrame();
            }
            if (frameClock == 14913 || frameClock == 29829) {
                halfFrame();
                if (frameClock == 29829 && !frameInhibit) {
                    frameIrq = true;
                }
            }
            if (frameClock >= 29830) {
                frameClock = 0;
            }
        } else {
            if (frameClock == 7457 || frameClock == 14913
                    || frameClock == 22371 || frameClock == 29829
                    || frameClock == 37281) {
                quarterFrame();
            }
            if (frameClock == 14913 || frameClock == 29829 || frameClock == 37281) {
                halfFrame();
            }
            if (frameClock >= 37282) {
                frameClock = 0;
            }
        }
    }

    private void quarterFrame() {
        pulse1.clockEnvelope();
        pulse2.clockEnvelope();
        noise.clockEnvelope();
        triangle.clockLinear();
    }

    private void halfFrame() {
        pulse1.clockLength();
        pulse2.clockLength();
        triangle.clockLength();
        noise.clockLength();
        pulse1.clockSweep();
        pulse2.clockSweep();
    }

    public boolean irqPending() {
        return frameIrq || dmc.irqPending();
    }

    public long emittedSamples() {
        return emittedSamples;
    }

    private int readRegister(int address) {
        if ((address & 0x1F) == 0x15) {
            int result = (pulse1.lengthActive() ? 0x01 : 0)
                    | (pulse2.lengthActive() ? 0x02 : 0)
                    | (triangle.lengthActive() ? 0x04 : 0)
                    | (noise.lengthActive() ? 0x08 : 0)
                    | (dmc.active() ? 0x10 : 0)
                    | (dmc.irqPending() ? 0x80 : 0)
                    | (frameIrq ? 0x40 : 0);
            frameIrq = false;
            dmc.clearIrq();
            return result;
        }
        return 0;
    }

    private void writeRegister(int address, int value) {
        switch (address & 0x1F) {
            case 0x00, 0x01, 0x02, 0x03 -> pulse1.write(address & 0x03, value);
            case 0x04, 0x05, 0x06, 0x07 -> pulse2.write(address & 0x03, value);
            case 0x08, 0x0A, 0x0B -> triangle.write(address & 0x03, value);
            case 0x0C, 0x0E, 0x0F -> noise.write(address & 0x03, value);
            case 0x10, 0x11, 0x12, 0x13 -> dmc.write(address & 0x03, value);
            case 0x15 -> {
                pulse1.setEnabled((value & 0x01) != 0);
                pulse2.setEnabled((value & 0x02) != 0);
                triangle.setEnabled((value & 0x04) != 0);
                noise.setEnabled((value & 0x08) != 0);
                dmc.setEnabled((value & 0x10) != 0);
            }
            case 0x17 -> {
                frameMode = (value & 0x80) != 0 ? 1 : 0;
                frameInhibit = (value & 0x40) != 0;
                frameClock = 0;
                frameIrq = false;
                if (frameMode == 1) {
                    quarterFrame();
                    halfFrame();
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void save(StateWriter writer) {
        pulse1.save(writer);
        pulse2.save(writer);
        triangle.save(writer);
        noise.save(writer);
        dmc.save(writer);
        writer.writeLong(sampleAccumulator);
        writer.writeLong(emittedSamples);
        writer.writeInt(previousMix);
        writer.writeInt(dcAccumulator);
        writer.writeInt(bufferPosition);
        writer.writeInt(frameClock);
        writer.writeByte(frameIrq ? 1 : 0);
        writer.writeByte(frameInhibit ? 1 : 0);
        writer.writeInt(frameMode);
    }

    @Override
    public void restore(StateReader reader) {
        pulse1.restore(reader);
        pulse2.restore(reader);
        triangle.restore(reader);
        noise.restore(reader);
        dmc.restore(reader);
        sampleAccumulator = reader.readLong();
        emittedSamples = reader.readLong();
        previousMix = reader.readInt();
        dcAccumulator = reader.readInt();
        bufferPosition = reader.readInt();
        frameClock = reader.readInt();
        frameIrq = reader.readByte() != 0;
        frameInhibit = reader.readByte() != 0;
        frameMode = reader.readInt();
    }
}
