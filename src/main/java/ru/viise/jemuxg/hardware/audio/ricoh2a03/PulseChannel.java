package ru.viise.jemuxg.hardware.audio.ricoh2a03;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Импульсный канал (pulse 1/2).
 */
final class PulseChannel {

    private static final int[] DUTY = {
        0, 1, 0, 0, 0, 0, 0, 0,
        0, 1, 1, 0, 0, 0, 0, 0,
        0, 1, 1, 1, 1, 0, 0, 0,
        1, 0, 0, 1, 1, 1, 1, 1
    };

    private final boolean pulse1;
    private final Envelope envelope = new Envelope();
    private final LengthCounter length = new LengthCounter();
    private final Sweep sweep = new Sweep();

    private int duty;
    private int timer;
    private int phase;
    private int dutyPosition;

    PulseChannel(boolean pulse1) {
        this.pulse1 = pulse1;
    }

    void write(int register, int value) {
        switch (register) {
            case 0 -> {
                duty = (value >> 6) & 0x03;
                envelope.write(value);
                length.setHalt((value & 0x20) != 0);
            }
            case 1 -> sweep.write(value);
            case 2 -> timer = (timer & 0x700) | (value & 0xFF);
            case 3 -> {
                timer = (timer & 0x0FF) | ((value & 0x07) << 8);
                length.load(value >> 3);
                envelope.restart();
                phase = 0;
                dutyPosition = 0;
            }
            default -> {
            }
        }
    }

    void tick() {
        phase++;
        int period = (timer + 1) * 2;
        while (phase >= period) {
            phase -= period;
            dutyPosition = (dutyPosition + 1) & 0x07;
        }
    }

    int output() {
        if (!length.active() || sweep.muted(timer, pulse1)) {
            return 0;
        }
        return DUTY[duty * 8 + dutyPosition] != 0 ? envelope.output() : 0;
    }

    void clockLength() {
        length.clock();
    }

    void clockSweep() {
        timer = sweep.clock(timer, pulse1);
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
        sweep.reset();
        duty = 0;
        timer = 0;
        phase = 0;
        dutyPosition = 0;
    }

    void save(StateWriter writer) {
        envelope.save(writer);
        length.save(writer);
        sweep.save(writer);
        writer.writeInt(duty);
        writer.writeInt(timer);
        writer.writeInt(phase);
        writer.writeInt(dutyPosition);
    }

    void restore(StateReader reader) {
        envelope.restore(reader);
        length.restore(reader);
        sweep.restore(reader);
        duty = reader.readInt();
        timer = reader.readInt();
        phase = reader.readInt();
        dutyPosition = reader.readInt();
    }
}
