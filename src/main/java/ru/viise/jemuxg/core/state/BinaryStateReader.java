package ru.viise.jemuxg.core.state;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import ru.viise.jemuxg.api.debug.StateReader;

/**
 * Парный reader к {@link BinaryStateWriter}.
 */
public final class BinaryStateReader implements StateReader {

    private final DataInputStream in;

    public BinaryStateReader(byte[] data) {
        this.in = new DataInputStream(new ByteArrayInputStream(data));
    }

    @Override
    public int readByte() {
        try {
            return in.readUnsignedByte();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int readShort() {
        try {
            return in.readUnsignedShort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int readInt() {
        try {
            return in.readInt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long readLong() {
        try {
            return in.readLong();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] readBytes() {
        try {
            int length = in.readInt();
            if (length < 0) {
                throw new IllegalArgumentException("negative byte array length: " + length);
            }
            byte[] data = new byte[length];
            in.readFully(data);
            return data;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
