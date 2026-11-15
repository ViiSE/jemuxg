package ru.viise.jemuxg.core.bus;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Bus;

/**
 * Зеркалирование окна устройства внутри большего диапазона.
 */
public final class Mirror implements Bus {

    private final Bus delegate;
    private final int windowSize;

    public Mirror(Bus delegate, int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0, got " + windowSize);
        }
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.windowSize = windowSize;
    }

    @Override
    public int read(int address) {
        return delegate.read(Math.floorMod(address, windowSize));
    }

    @Override
    public void write(int address, int value) {
        delegate.write(Math.floorMod(address, windowSize), value);
    }
}
