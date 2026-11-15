package ru.viise.jemuxg.hardware.audio.ricoh2a03;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Треугольный канал.
 */
final class TriangleChannel {

    private final LengthCounter length = new LengthCounter();

    private boolean control;
    private int linearReloadValue;
    private int linearCounter;
    private boolean linearReload;
    private int timer;
    private int phase;
    private int sequence;

    void write(int register, int value) {
        switch (register) {
            case 0 -> {
                control = (value & 0x80) != 0;
                linearReloadValue = value & 0x7F;
                length.setHalt(control);
                linearReload = true;
            }
            case 2 -> timer = (timer & 0x700) | (value & 0xFF);
            case 3 -> {
                timer = (timer & 0x0FF) | ((value & 0x07) << 8);
                length.load(value >> 3);
                linearReload = true;
            }
            default -> {
            }
        }
    }

    void tick() {
        phase++;
        int period = timer + 1;
        while (phase >= period) {
            phase -= period;
            sequence = (sequence + 1) & 0x1F;
        }
    }

    int output() {
        if (!length.active() || linearCounter == 0 || timer < 2) {
            return 0;
        }
        return sequence < 16 ? 15 - sequence : sequence - 16;
    }

    void clockLength() {
        length.clock();
    }

    void clockLinear() {
        if (linearReload) {
            linearCounter = linearReloadValue;
            if (!control) {
                linearReload = false;
            }
        } else if (linearCounter > 0) {
            linearCounter--;
        }
    }

    void setEnabled(boolean enabled) {
        length.setEnabled(enabled);
    }

    boolean lengthActive() {
        return length.active();
    }

    void reset() {
        length.reset();
        control = false;
        linearReloadValue = 0;
        linearCounter = 0;
        linearReload = false;
        timer = 0;
        phase = 0;
        sequence = 0;
    }

    void save(StateWriter writer) {
        length.save(writer);
        writer.writeByte(control ? 1 : 0);
        writer.writeInt(linearReloadValue);
        writer.writeInt(linearCounter);
        writer.writeByte(linearReload ? 1 : 0);
        writer.writeInt(timer);
        writer.writeInt(phase);
        writer.writeInt(sequence);
    }

    void restore(StateReader reader) {
        length.restore(reader);
        control = reader.readByte() != 0;
        linearReloadValue = reader.readInt();
        linearCounter = reader.readInt();
        linearReload = reader.readByte() != 0;
        timer = reader.readInt();
        phase = reader.readInt();
        sequence = reader.readInt();
    }
}
