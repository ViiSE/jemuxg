package ru.viise.jemuxg.storage.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;

/**
 * Носитель, читаемый напрямую из файла. Канал держится открытым.
 * Не забывайте закрывать (try-with-resources).
 */
public final class FileMedia implements Media, AutoCloseable {

    private final Path path;
    private final MediaKind kind;
    private final long size;
    private final FileChannel channel;

    private FileMedia(Path path, MediaKind kind) throws IOException {
        this.path = path;
        this.kind = kind;
        this.size = Files.size(path);
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
    }

    public static FileMedia open(Path path, MediaKind kind) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        return new FileMedia(path, kind);
    }

    @Override
    public String name() {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    @Override
    public long size() {
        return size;
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
        if (offset < 0 || offset >= size || length == 0) {
            return 0;
        }
        int count = (int) Math.min(length, size - offset);
        ByteBuffer buffer = ByteBuffer.wrap(destination, destinationOffset, count);
        try {
            int total = 0;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, offset + total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            return total;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
