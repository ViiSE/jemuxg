package ru.viise.jemuxg.core.debug;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;
import ru.viise.jemuxg.api.debug.DebugSession;

/**
 * Файловая сессия отладки.
 * <p>
 * Структура: {@code debug/<rom>/frame-<номер>/<артефакты>}.
 * Родительский каталог задаётся относительно рабочего каталога запуска.
 */
public final class FileDebugSession implements DebugSession {

    private final Path directory;
    private final String romName;
    private final long frameNumber;

    public FileDebugSession(Path root, String romName, long frameNumber) throws IOException {
        Objects.requireNonNull(root, "root");
        this.romName = sanitize(romName);
        this.frameNumber = frameNumber;
        this.directory = root.resolve(this.romName)
                .resolve("frame-" + frameNumber)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(directory);
    }

    @Override
    public Path directory() {
        return directory;
    }

    @Override
    public String romName() {
        return romName;
    }

    @Override
    public long frameNumber() {
        return frameNumber;
    }

    @Override
    public void writeBytes(String name, byte[] data) throws IOException {
        Objects.requireNonNull(data, "data");
        Files.write(resolve(name), data);
    }

    @Override
    public void writeText(String name, String text) throws IOException {
        Objects.requireNonNull(text, "text");
        Files.writeString(resolve(name), text, StandardCharsets.UTF_8);
    }

    @Override
    public void writePng(String name, int width, int height, int[] argb) throws IOException {
        Objects.requireNonNull(argb, "argb");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("frame size must be positive: " + width + "x" + height);
        }
        if (argb.length < width * height) {
            throw new IllegalArgumentException(
                    "pixel buffer is too small: " + argb.length + " < " + width * height);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        if (!ImageIO.write(image, "png", resolve(name).toFile())) {
            throw new IOException("PNG writer is not available");
        }
    }

    private Path resolve(String name) throws IOException {
        Objects.requireNonNull(name, "name");
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory)) {
            throw new IOException("debug artifact escapes session directory: " + name);
        }
        return path;
    }

    private static String sanitize(String romName) {
        String value = romName == null ? "" : romName;
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return value.isBlank() ? "rom" : value;
    }
}
