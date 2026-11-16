package ru.viise.jemuxg.hardware.cpu.mos6507;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Bus;

/**
 * 13-битная адресная шина MOS 6507.
 *
 * <p>Процессор формирует 16-битный адрес, но наружу выходят только младшие
 * 13 бит. Поэтому {@code $FFFC} на стороне CPU соответствует {@code $1FFC}
 * в адресном пространстве Atari 2600.</p>
 */
public final class Mos6507Bus implements Bus {

    /** Разрядность адресной шины 6507. */
    public static final int ADDRESS_BITS = 13;

    /** Маска 13-битного адреса. */
    public static final int ADDRESS_MASK = (1 << ADDRESS_BITS) - 1;

    private final Bus delegate;

    public Mos6507Bus(Bus delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public int read(int address) {
        return delegate.read(address & ADDRESS_MASK);
    }

    @Override
    public void write(int address, int value) {
        delegate.write(address & ADDRESS_MASK, value);
    }
}
