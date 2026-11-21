package ru.viise.jemuxg.hardware.riot.riot6532;

import ru.viise.jemuxg.api.Clocked;
import ru.viise.jemuxg.api.Component;
import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;

/**
 * Таймер RIOT 6532.
 *
 * <p>Регистры таймера занимают смещения {@code $14..$17} от базы RIOT
 * ({@code $0294..$0297} в адресном пространстве 6507). Запись в
 * {@code TIM1T/TIM8T/TIM64T/TIM1024T} загружает интервал и выбирает
 * делитель 1/8/64/1024 такта CPU. Чтение {@code INTIM} возвращает текущее
 * значение, чтение {@code INTFLG} — флаг underflow в бите 7.</p>
 *
 * <p>После загрузки первый тик таймера происходит через один такт CPU,
 * затем — с выбранным делителем. При переходе из 0 в {@code $FF}
 * устанавливается флаг underflow; таймер продолжает счёт.</p>
 */
public final class RiotTimer implements Component, Clocked, Stateful {

    public static final int TIM1T = 0x14;
    public static final int TIM8T = 0x15;
    public static final int TIM64T = 0x16;
    public static final int TIM1024T = 0x17;

    public static final int INTIM = 0x14;
    public static final int INTFLG = 0x15;
    /** Псевдонимы RIOT для чтения через {@code $0284/$0285}. */
    public static final int INTIM_MIRROR = 0x04;
    public static final int INTFLG_MIRROR = 0x05;

    private static final int REGISTER_MASK = 0x1F;

    private final Bus registerBus = new Bus() {
        @Override
        public int read(int address) {
            return readRegister(address);
        }

        @Override
        public void write(int address, int value) {
            writeRegister(address, value);
        }
    };

    private int timer;
    private int divider = 1;
    private int subTimer;
    private boolean underflow;

    /**
     * @return шина регистров таймера; принимает как смещения {@code $14..$17},
     *         так и абсолютные адреса {@code $0294..$0297}
     */
    public Bus registers() {
        return registerBus;
    }

    /**
     * @return текущее значение таймера
     */
    public int timer() {
        return timer;
    }

    /**
     * @return установлен ли флаг underflow
     */
    public boolean underflow() {
        return underflow;
    }

    @Override
    public String name() {
        return "RIOT 6532 Timer";
    }

    @Override
    public void reset() {
        timer = 0;
        divider = 1;
        subTimer = 0;
        underflow = false;
    }

    @Override
    public void tick(long cycles) {
        for (long i = 0; i < cycles; i++) {
            subTimer = (subTimer + 1) & (divider - 1);
            if (subTimer == 0) {
                if (timer == 0) {
                    timer = 0xFF;
                    underflow = true;
                } else {
                    timer--;
                }
            }
        }
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeByte(timer);
        writer.writeInt(divider);
        writer.writeInt(subTimer);
        writer.writeByte(underflow ? 1 : 0);
    }

    @Override
    public void restore(StateReader reader) {
        timer = reader.readByte();
        divider = reader.readInt();
        subTimer = reader.readInt();
        underflow = reader.readByte() != 0;
        if (divider != 1 && divider != 8 && divider != 64 && divider != 1024) {
            throw new IllegalArgumentException("Invalid RIOT timer divider: " + divider);
        }
    }

    private int readRegister(int address) {
        int register = address & REGISTER_MASK;
        return switch (register) {
            case INTIM, INTIM_MIRROR -> {
                int value = timer;
                underflow = false;
                yield value;
            }
            case INTFLG, INTFLG_MIRROR -> underflow ? 0x80 : 0x00;
            default -> 0;
        };
    }

    private void writeRegister(int address, int value) {
        int register = address & REGISTER_MASK;
        switch (register) {
            case TIM1T -> setTimer(value, 1);
            case TIM8T -> setTimer(value, 8);
            case TIM64T -> setTimer(value, 64);
            case TIM1024T -> setTimer(value, 1024);
            default -> {
            }
        }
    }

    private void setTimer(int value, int newDivider) {
        timer = value & 0xFF;
        divider = newDivider;
        subTimer = newDivider - 1;
        underflow = false;
    }
}
