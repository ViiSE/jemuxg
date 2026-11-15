package ru.viise.jemuxg.console.nes.debug;

import java.util.Objects;
import ru.viise.jemuxg.api.debug.CpuRegisters;

/**
 * Полный снимок NES на границе кадра.
 * <p>
 * Массивы уже являются копиями: их можно безопасно писать в файлы,
 * пока эмуляция продолжается.
 */
public record NesDebugSnapshot(
        long frame,
        int width,
        int height,
        int[] framebufferArgb,
        byte[] framebufferIndices,
        byte[] cpuRam,
        byte[] prgRam,
        byte[] vram,
        byte[] palette,
        byte[] oam,
        CpuRegisters registers,
        String mapperName,
        int mapperId) {

    public NesDebugSnapshot {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("frame size must be positive: " + width + "x" + height);
        }
        Objects.requireNonNull(framebufferArgb, "framebufferArgb");
        Objects.requireNonNull(framebufferIndices, "framebufferIndices");
        if (framebufferArgb.length != width * height) {
            throw new IllegalArgumentException("ARGB frame size mismatch");
        }
        if (framebufferIndices.length != width * height) {
            throw new IllegalArgumentException("index frame size mismatch");
        }
        Objects.requireNonNull(cpuRam, "cpuRam");
        Objects.requireNonNull(prgRam, "prgRam");
        Objects.requireNonNull(vram, "vram");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(oam, "oam");
        Objects.requireNonNull(registers, "registers");
        Objects.requireNonNull(mapperName, "mapperName");
    }
}
