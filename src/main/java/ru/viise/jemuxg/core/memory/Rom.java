package ru.viise.jemuxg.core.memory;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Memory;

/**
 * Неизменяемая ROM.
 */
public final class Rom implements Memory {

    private final byte[] data;

    public Rom(byte[] data) {
        Objects.requireNonNull(data, "data");
        this.data = data.clone();
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public int read(int address) {
        return data[Math.floorMod(address, data.length)] & 0xFF;
    }

    @Override
    public void write(int address, int value) {
        // ROM игнорирует запись.
    }
}
