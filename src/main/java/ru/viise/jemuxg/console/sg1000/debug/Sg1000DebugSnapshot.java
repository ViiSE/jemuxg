package ru.viise.jemuxg.console.sg1000.debug;

import java.util.Objects;
import ru.viise.jemuxg.console.sg1000.Sg1000Constants;
import ru.viise.jemuxg.hardware.video.tms9918.Tms9918;

/**
 * Полный снимок Sega SG-1000.
 *
 * <p>Все массивы уже являются копиями: их можно безопасно писать в файлы,
 * пока эмуляция продолжается.</p>
 */
public record Sg1000DebugSnapshot(
        long frame,
        int scanline,
        int dot,
        int width,
        int height,
        int[] framebufferArgb,
        byte[] framebufferIndices,
        byte[] ram,
        byte[] vram,
        int[] vdpRegisters,
        int vdpStatus,
        Tms9918.Mode vdpMode,
        int vdpLine,
        long vdpFrame,
        int[] psgRegisters,
        Z80RegisterSnapshot registers,
        String cartridgeName,
        long cartridgeSize) {

    private static final int VDP_REGISTER_COUNT = 8;
    private static final int PSG_REGISTER_COUNT = 8;

    public Sg1000DebugSnapshot {
        if (frame < 0) {
            throw new IllegalArgumentException("frame must be non-negative: " + frame);
        }
        if (scanline < 0 || scanline >= Sg1000Constants.SCREEN_LINES) {
            throw new IllegalArgumentException("invalid VDP scanline: " + scanline);
        }
        if (dot < 0 || dot >= Tms9918.DOTS_PER_LINE) {
            throw new IllegalArgumentException("invalid VDP dot: " + dot);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "frame size must be positive: " + width + "x" + height);
        }
        Objects.requireNonNull(framebufferArgb, "framebufferArgb");
        Objects.requireNonNull(framebufferIndices, "framebufferIndices");
        if (framebufferArgb.length != width * height) {
            throw new IllegalArgumentException("ARGB frame size mismatch");
        }
        if (framebufferIndices.length != width * height) {
            throw new IllegalArgumentException("index frame size mismatch");
        }
        Objects.requireNonNull(ram, "ram");
        if (ram.length != Sg1000Constants.RAM_SIZE) {
            throw new IllegalArgumentException(
                    "RAM must be " + Sg1000Constants.RAM_SIZE + " bytes");
        }
        Objects.requireNonNull(vram, "vram");
        if (vram.length != Sg1000Constants.VDP_VRAM_SIZE) {
            throw new IllegalArgumentException(
                    "VRAM must be " + Sg1000Constants.VDP_VRAM_SIZE + " bytes");
        }
        Objects.requireNonNull(vdpRegisters, "vdpRegisters");
        if (vdpRegisters.length != VDP_REGISTER_COUNT) {
            throw new IllegalArgumentException(
                    "VDP registers must be " + VDP_REGISTER_COUNT + " bytes");
        }
        Objects.requireNonNull(vdpMode, "vdpMode");
        if (vdpLine < 0 || vdpLine >= Sg1000Constants.SCREEN_LINES) {
            throw new IllegalArgumentException("invalid VDP line: " + vdpLine);
        }
        if (vdpFrame < 0) {
            throw new IllegalArgumentException("VDP frame must be non-negative: " + vdpFrame);
        }
        Objects.requireNonNull(psgRegisters, "psgRegisters");
        if (psgRegisters.length != PSG_REGISTER_COUNT) {
            throw new IllegalArgumentException(
                    "PSG registers must be " + PSG_REGISTER_COUNT + " bytes");
        }
        Objects.requireNonNull(registers, "registers");
        Objects.requireNonNull(cartridgeName, "cartridgeName");
        if (cartridgeSize < 0) {
            throw new IllegalArgumentException(
                    "cartridge size must be non-negative: " + cartridgeSize);
        }
    }
}
