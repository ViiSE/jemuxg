package ru.viise.jemuxg.hardware.audio.ricoh2a03;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Генератор огибающей pulse/noise каналов.
 */
final class Envelope {

    private boolean loop;
    private boolean constant;
    private int period;
    private int divider;
    private int decay;
    private boolean start;

    void write(int value) {
        loop = (value & 0x20) != 0;
        constant = (value & 0x10) != 0;
        period = value & 0x0F;
    }

    void clock() {
        if (start) {
            start = false;
            decay = 15;
            divider = period;
        } else if (divider == 0) {
            divider = period;
            if (decay > 0) {
                decay--;
            } else if (loop) {
                decay = 15;
            }
        } else {
            divider--;
        }
    }

    int output() {
        return constant ? period : decay;
    }

    void restart() {
        start = true;
    }

    void reset() {
        loop = false;
        constant = false;
        period = 0;
        divider = 0;
        decay = 0;
        start = false;
    }

    void save(StateWriter writer) {
        writer.writeByte(loop ? 1 : 0);
        writer.writeByte(constant ? 1 : 0);
        writer.writeInt(period);
        writer.writeInt(divider);
        writer.writeInt(decay);
        writer.writeByte(start ? 1 : 0);
    }

    void restore(StateReader reader) {
        loop = reader.readByte() != 0;
        constant = reader.readByte() != 0;
        period = reader.readInt();
        divider = reader.readInt();
        decay = reader.readInt();
        start = reader.readByte() != 0;
    }
}
