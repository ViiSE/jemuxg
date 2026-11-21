package ru.viise.jemuxg.hardware.audio.sn76489;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.AudioProcessor;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.console.sg1000.Sg1000Constants;

/**
 * Трёхканальный PSG SN76489AN с каналом шума.
 *
 * <p>Чип тактируется от мастер-такта SG-1000. Внутренний делитель
 * формирует частоту тона {@code clock / (32 * N)}, канал шума использует
 * 15-битный LFSR с отводами 0 и 1. Выход микшируется в 44,1 кГц и
 * отправляется в {@link AudioSink} по мере заполнения буфера.</p>
 *
 * <p>Реализация следует документации SN76489 и поведению SN76489AN:
 * младшие 4 бита периода записываются защёлкой, старшие 6 — следующим
 * байтом; аттенюация нелинейная, шаг около 2 дБ.</p>
 */
public final class Sn76489 implements AudioProcessor {

    public static final int CLOCK_HZ = Sg1000Constants.CPU_CLOCK_HZ;
    public static final int SAMPLE_RATE = Sg1000Constants.AUDIO_SAMPLE_RATE;
    public static final int CHANNELS = 4;
    public static final int NOISE_CHANNEL = 3;

    private static final int STATE_MAGIC = 0x534E3839;
    private static final int STATE_VERSION = 1;
    private static final int CHIP_DIVIDER = 16;
    private static final int PERIOD_MASK = 0x03FF;
    private static final int LFSR_FEEDBACK = 0x4000;
    private static final int[] VOLUME = volumeTable();

    private final int[] registers = new int[8];
    private final int[] period = new int[CHANNELS];
    private final int[] counter = new int[CHANNELS];
    private final int[] volume = new int[CHANNELS];
    private final boolean[] output = new boolean[CHANNELS];
    private final short[] buffer = new short[1024];

    private AudioSink sink;
    private int lastRegister;
    private int lfsr;
    private long chipPhase;
    private long sampleAccumulator;
    private int bufferPosition;

    public Sn76489() {
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
        return "SN76489";
    }

    /**
     * Записывает байт в PSG: защёлку канала/регистра или данные.
     */
    public void write(int value) {
        value &= 0xFF;
        int register;
        if ((value & 0x80) != 0) {
            register = (value >> 4) & 0x07;
            lastRegister = register;
            registers[register] = (registers[register] & 0x3F0) | (value & 0x0F);
        } else {
            register = lastRegister;
            if (register == 0 || register == 2 || register == 4) {
                registers[register] =
                        (registers[register] & 0x00F) | ((value & 0x3F) << 4);
            } else if (register == 6) {
                registers[register] = (registers[register] & 0x3F0) | (value & 0x0F);
            } else {
                registers[register] = (registers[register] & 0x3F0) | (value & 0x0F);
            }
        }
        update(register);
    }

    @Override
    public void tick(long cycles) {
        if (cycles < 0) {
            throw new IllegalArgumentException("cycles must be non-negative");
        }
        while (cycles > 0) {
            long toChip = CHIP_DIVIDER - chipPhase;
            long toSample = (CLOCK_HZ - sampleAccumulator + SAMPLE_RATE - 1) / SAMPLE_RATE;
            long step = Math.min(cycles, Math.min(toChip, toSample));
            if (step <= 0) {
                step = 1;
            }
            cycles -= step;
            chipPhase += step;
            sampleAccumulator += step * SAMPLE_RATE;

            if (chipPhase >= CHIP_DIVIDER) {
                chipPhase = 0;
                stepChip();
            }
            if (sampleAccumulator >= CLOCK_HZ) {
                sampleAccumulator -= CLOCK_HZ;
                pushSample();
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
        for (int i = 0; i < registers.length; i++) {
            registers[i] = 0;
        }
        registers[1] = 0x0F;
        registers[3] = 0x0F;
        registers[5] = 0x0F;
        registers[7] = 0x0F;
        lastRegister = 0;
        for (int i = 0; i < CHANNELS; i++) {
            period[i] = i == NOISE_CHANNEL ? 1 << 5 : 0x400;
            counter[i] = 0;
            volume[i] = 0;
            output[i] = false;
        }
        lfsr = LFSR_FEEDBACK;
        chipPhase = 0;
        sampleAccumulator = 0;
        bufferPosition = 0;
    }

    public int register(int index) {
        return index >= 0 && index < registers.length ? registers[index] : 0;
    }

    public int period(int channel) {
        return channel >= 0 && channel < CHANNELS ? period[channel] : 0;
    }

    public int volume(int channel) {
        return channel >= 0 && channel < CHANNELS ? volume[channel] : 0;
    }

    public boolean output(int channel) {
        return channel >= 0 && channel < CHANNELS && output[channel];
    }

    public int lfsr() {
        return lfsr;
    }

    public int bufferPosition() {
        return bufferPosition;
    }

    public int lastRegister() {
        return lastRegister;
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeInt(STATE_MAGIC);
        writer.writeByte(STATE_VERSION);
        for (int register : registers) {
            writer.writeByte(register);
        }
        writer.writeByte(lastRegister);
        for (int i = 0; i < CHANNELS; i++) {
            writer.writeInt(period[i]);
            writer.writeInt(counter[i]);
            writer.writeByte(output[i] ? 1 : 0);
        }
        writer.writeInt(lfsr);
        writer.writeLong(chipPhase);
        writer.writeLong(sampleAccumulator);
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
        int magic = reader.readInt();
        if (magic != STATE_MAGIC) {
            throw new IllegalArgumentException("invalid SN76489 state");
        }
        int version = reader.readByte();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported SN76489 state version: " + version);
        }
        for (int i = 0; i < registers.length; i++) {
            registers[i] = reader.readByte();
        }
        lastRegister = reader.readByte();
        for (int i = 0; i < CHANNELS; i++) {
            period[i] = reader.readInt();
            counter[i] = reader.readInt();
            output[i] = reader.readByte() != 0;
        }
        lfsr = reader.readInt();
        chipPhase = reader.readLong();
        sampleAccumulator = reader.readLong();
        bufferPosition = reader.readInt();
        if (bufferPosition < 0 || bufferPosition > buffer.length) {
            throw new IllegalArgumentException(
                    "invalid SN76489 buffer position: " + bufferPosition);
        }
        byte[] samples = reader.readBytes();
        if (samples.length != bufferPosition * 2) {
            throw new IllegalArgumentException(
                    "invalid SN76489 buffer length: " + samples.length);
        }
        for (int i = 0; i < bufferPosition; i++) {
            buffer[i] = (short) ((samples[i * 2] & 0xFF) | (samples[i * 2 + 1] << 8));
        }
        for (int i = 0; i < CHANNELS; i++) {
            volume[i] = VOLUME[registers[i * 2 + 1] & 0x0F];
        }
    }

    private void update(int register) {
        if (register == 0 || register == 2 || register == 4) {
            int channel = register >> 1;
            int value = registers[register] & PERIOD_MASK;
            period[channel] = value == 0 ? 0x400 : value;
            if (register == 4 && (registers[6] & 0x03) == 0x03) {
                period[NOISE_CHANNEL] = period[2] * 2;
            }
        } else if ((register & 1) == 1) {
            volume[register >> 1] = VOLUME[registers[register] & 0x0F];
        } else if (register == 6) {
            int value = registers[6];
            period[NOISE_CHANNEL] = (value & 0x03) == 0x03
                    ? period[2] * 2
                    : (1 << (5 + (value & 0x03)));
            lfsr = LFSR_FEEDBACK;
            output[NOISE_CHANNEL] = false;
            counter[NOISE_CHANNEL] = period[NOISE_CHANNEL];
        }
    }

    private void stepChip() {
        for (int channel = 0; channel < 3; channel++) {
            if (--counter[channel] <= 0) {
                output[channel] = !output[channel];
                counter[channel] = period[channel];
            }
        }
        if (--counter[NOISE_CHANNEL] <= 0) {
            boolean tap1 = (lfsr & 0x01) != 0;
            boolean tap2 = (lfsr & 0x02) != 0 && (registers[6] & 0x04) != 0;
            boolean feedback = tap1 ^ tap2;
            lfsr >>= 1;
            if (feedback) {
                lfsr |= LFSR_FEEDBACK;
            }
            output[NOISE_CHANNEL] = (lfsr & 0x01) != 0;
            counter[NOISE_CHANNEL] = period[NOISE_CHANNEL];
        }
    }

    private void pushSample() {
        int mixed = 0;
        for (int channel = 0; channel < CHANNELS; channel++) {
            int amplitude = volume[channel];
            mixed += output[channel] ? amplitude : -amplitude;
        }
        mixed = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
        buffer[bufferPosition++] = (short) mixed;
        if (bufferPosition == buffer.length) {
            flush();
        }
    }

    private static int[] volumeTable() {
        int[] table = new int[16];
        int value = 0x7FFF / 4;
        for (int i = 0; i < 15; i++) {
            table[i] = value;
            value = (int) (value / 1.258925412);
        }
        table[15] = 0;
        return table;
    }
}
