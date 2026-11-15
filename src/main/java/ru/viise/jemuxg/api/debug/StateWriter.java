package ru.viise.jemuxg.api.debug;

public interface StateWriter {

    void writeByte(int value);

    void writeShort(int value);

    void writeInt(int value);

    void writeLong(long value);

    void writeBytes(byte[] bytes);
}
