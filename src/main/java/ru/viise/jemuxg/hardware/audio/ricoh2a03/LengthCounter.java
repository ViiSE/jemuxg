package ru.viise.jemuxg.hardware.audio.ricoh2a03;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Счётчик длины канала APU.
 */
final class LengthCounter {

    private static final int[] LENGTHS = {
        10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
        12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30
    };

    private int value;
    private boolean halt;
    private boolean enabled;

    void setHalt(boolean halt) {
        this.halt = halt;
    }

    void load(int index) {
        if (enabled) {
            value = LENGTHS[index & 0x1F];
        }
    }

    void clock() {
        if (!halt && value > 0) {
            value--;
        }
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            value = 0;
        }
    }

    boolean active() {
        return enabled && value > 0;
    }

    void reset() {
        value = 0;
        halt = false;
        enabled = false;
    }

    void save(StateWriter writer) {
        writer.writeInt(value);
        writer.writeByte(halt ? 1 : 0);
        writer.writeByte(enabled ? 1 : 0);
    }

    void restore(StateReader reader) {
        value = reader.readInt();
        halt = reader.readByte() != 0;
        enabled = reader.readByte() != 0;
    }
}
