package ru.viise.jemuxg.hardware.audio.ricoh2a03;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Шумовой канал.
 */
final class NoiseChannel {

    private static final int[] PERIODS = {
        4, 8, 16, 32, 64, 96, 128, 160,
        202, 254, 380, 508, 762, 1016, 2034, 4068
    };

    private final Envelope envelope = new Envelope();
    private final LengthCounter length = new LengthCounter();

    private boolean mode;
    private int periodIndex;
    private int phase;
    private int shiftRegister = 1;

    void write(int register, int value) {
        switch (register) {
            case 0 -> {
                envelope.write(value);
                length.setHalt((value & 0x20) != 0);
            }
            case 2 -> {
                mode = (value & 0x80) != 0;
                periodIndex = value & 0x0F;
            }
            case 3 -> {
                length.load(value >> 3);
                envelope.restart();
            }
            default -> {
            }
        }
    }

    void tick() {
        phase++;
        int period = PERIODS[periodIndex];
        while (phase >= period) {
            phase -= period;
            int feedback = (shiftRegister & 1)
                    ^ ((shiftRegister >> (mode ? 6 : 1)) & 1);
            shiftRegister = (shiftRegister >> 1) | (feedback << 14);
        }
    }

    int output() {
        if (!length.active() || (shiftRegister & 1) != 0) {
            return 0;
        }
        return envelope.output();
    }

    void clockLength() {
        length.clock();
    }

    void clockEnvelope() {
        envelope.clock();
    }

    void setEnabled(boolean enabled) {
        length.setEnabled(enabled);
    }

    boolean lengthActive() {
        return length.active();
    }

    void reset() {
        envelope.reset();
        length.reset();
        mode = false;
        periodIndex = 0;
        phase = 0;
        shiftRegister = 1;
    }

    void save(StateWriter writer) {
        envelope.save(writer);
        length.save(writer);
        writer.writeByte(mode ? 1 : 0);
        writer.writeInt(periodIndex);
        writer.writeInt(phase);
        writer.writeInt(shiftRegister);
    }

    void restore(StateReader reader) {
        envelope.restore(reader);
        length.restore(reader);
        mode = reader.readByte() != 0;
        periodIndex = reader.readInt();
        phase = reader.readInt();
        shiftRegister = reader.readInt();
    }
}
