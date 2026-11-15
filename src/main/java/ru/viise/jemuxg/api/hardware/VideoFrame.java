package ru.viise.jemuxg.api.hardware;

import java.util.Objects;

/**
 * Кадр в формате ARGB (0xAARRGGBB), строка за строкой.
 */
public record VideoFrame(int width, int height, int[] pixels) {

    public VideoFrame {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("frame size must be positive: " + width + "x" + height);
        }
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.length < width * height) {
            throw new IllegalArgumentException(
                    "pixels length " + pixels.length + " is less than " + width + "x" + height);
        }
    }
}
