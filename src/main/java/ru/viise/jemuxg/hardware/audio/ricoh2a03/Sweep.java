package ru.viise.jemuxg.hardware.audio.ricoh2a03;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Sweep-блок импульсных каналов.
 */
final class Sweep {

    private boolean enabled;
    private boolean negate;
    private int period;
    private int shift;
    private int divider;
    private boolean reload;

    void write(int value) {
        enabled = (value & 0x80) != 0;
        period = (value >> 4) & 0x07;
        negate = (value & 0x08) != 0;
        shift = value & 0x07;
        reload = true;
    }

    int clock(int timer, boolean pulse1) {
        int target = target(timer, pulse1);
        if (divider == 0) {
            if (enabled && shift > 0 && timer >= 8 && target <= 0x7FF) {
                timer = target;
            }
            divider = period;
        } else {
            divider--;
        }
        if (reload) {
            divider = period;
            reload = false;
        }
        return timer;
    }

    boolean muted(int timer, boolean pulse1) {
        return timer < 8 || target(timer, pulse1) > 0x7FF;
    }

    private int target(int timer, boolean pulse1) {
        if (shift == 0) {
            return timer;
        }
        int change = timer >> shift;
        if (negate) {
            change = pulse1 ? -change - 1 : -change;
        }
        return timer + change;
    }

    void reset() {
        enabled = false;
        negate = false;
        period = 0;
        shift = 0;
        divider = 0;
        reload = false;
    }

    void save(StateWriter writer) {
        writer.writeByte(enabled ? 1 : 0);
        writer.writeByte(negate ? 1 : 0);
        writer.writeInt(period);
        writer.writeInt(shift);
        writer.writeInt(divider);
        writer.writeByte(reload ? 1 : 0);
    }

    void restore(StateReader reader) {
        enabled = reader.readByte() != 0;
        negate = reader.readByte() != 0;
        period = reader.readInt();
        shift = reader.readInt();
        divider = reader.readInt();
        reload = reader.readByte() != 0;
    }
}
