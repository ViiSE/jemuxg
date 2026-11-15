package ru.viise.jemuxg.storage.media;

import java.util.Objects;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;

/**
 * Носитель, полностью загруженный в память.
 */
public final class ByteArrayMedia implements Media {

    private final String name;
    private final MediaKind kind;
    private final byte[] data;

    public ByteArrayMedia(String name, MediaKind kind, byte[] data) {
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.data = Objects.requireNonNull(data, "data").clone();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public long size() {
        return data.length;
    }

    @Override
    public MediaKind kind() {
        return kind;
    }

    @Override
    public int read(long offset, byte[] destination, int destinationOffset, int length) {
        Objects.requireNonNull(destination, "destination");
        if (destinationOffset < 0 || length < 0 || destinationOffset + length > destination.length) {
            throw new IndexOutOfBoundsException("invalid destination range");
        }
        if (offset < 0 || offset >= data.length || length == 0) {
            return 0;
        }
        int count = (int) Math.min(length, data.length - offset);
        System.arraycopy(data, (int) offset, destination, destinationOffset, count);
        return count;
    }
}
