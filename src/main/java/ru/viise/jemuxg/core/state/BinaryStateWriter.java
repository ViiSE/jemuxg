package ru.viise.jemuxg.core.state;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Бинарный writer: байты, short, int, long, length-prefixed byte[].
 */
public final class BinaryStateWriter implements StateWriter {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(buffer);

    @Override
    public void writeByte(int value) {
        try {
            out.writeByte(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeShort(int value) {
        try {
            out.writeShort(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeInt(int value) {
        try {
            out.writeInt(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeLong(long value) {
        try {
            out.writeLong(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBytes(byte[] bytes) {
        try {
            out.writeInt(bytes.length);
            out.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] toByteArray() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }
}
