package ru.viise.jemuxg.api.debug;

public interface StateReader {

    int readByte();

    int readShort();

    int readInt();

    long readLong();

    byte[] readBytes();
}
