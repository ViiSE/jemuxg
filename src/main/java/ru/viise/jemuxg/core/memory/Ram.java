package ru.viise.jemuxg.core.memory;

import java.util.Arrays;
import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Memory;

/**
 * RAM с зеркалированием по размеру блока.
 */
public final class Ram implements Memory, Stateful {

    private final byte[] data;

    public Ram(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0, got " + size);
        }
        this.data = new byte[size];
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
        data[Math.floorMod(address, data.length)] = (byte) value;
    }

    public void clear() {
        Arrays.fill(data, (byte) 0);
    }

    /**
     * Копия содержимого для отладочных снимков.
     */
    public byte[] snapshot() {
        return data.clone();
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeBytes(data);
    }

    @Override
    public void restore(StateReader reader) {
        byte[] saved = reader.readBytes();
        if (saved.length != data.length) {
            throw new IllegalArgumentException(
                    "RAM state size mismatch: expected " + data.length + ", got " + saved.length);
        }
        System.arraycopy(saved, 0, data, 0, data.length);
    }
}
