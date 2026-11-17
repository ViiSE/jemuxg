package ru.viise.jemuxg.console.atari2600.debug;

import java.util.Objects;
import ru.viise.jemuxg.api.debug.CpuRegisters;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Полный снимок Atari 2600 на границе кадра.
 *
 * <p>Все массивы уже являются копиями: их можно безопасно писать в файлы,
 * пока эмуляция продолжается.</p>
 */
public record AtariDebugSnapshot(
        long frame,
        int scanline,
        int colorClock,
        boolean wsync,
        int width,
        int height,
        int[] framebufferArgb,
        byte[] framebufferIndices,
        byte[] riotRam,
        byte[] tiaRegisters,
        byte[] riotRegisters,
        CpuRegisters registers,
        String mapperName,
        String mapperState,
        int mapperBank,
        int mapperBankCount,
        boolean mapperWritable,
        byte[] mapperRam) {

    public AtariDebugSnapshot {
        if (frame < 0) {
            throw new IllegalArgumentException("frame must be non-negative: " + frame);
        }
        if (scanline < 0 || scanline >= Atari2600Constants.SCANLINES_PER_FRAME) {
            throw new IllegalArgumentException("invalid TIA scanline: " + scanline);
        }
        if (colorClock < 0 || colorClock >= Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE) {
            throw new IllegalArgumentException("invalid TIA color clock: " + colorClock);
        }
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
        Objects.requireNonNull(riotRam, "riotRam");
        if (riotRam.length != Atari2600Constants.RIOT_RAM_SIZE) {
            throw new IllegalArgumentException(
                    "RIOT RAM must be " + Atari2600Constants.RIOT_RAM_SIZE + " bytes");
        }
        Objects.requireNonNull(tiaRegisters, "tiaRegisters");
        if (tiaRegisters.length != Atari2600Constants.TIA_REGISTER_COUNT) {
            throw new IllegalArgumentException(
                    "TIA registers must be " + Atari2600Constants.TIA_REGISTER_COUNT + " bytes");
        }
        Objects.requireNonNull(riotRegisters, "riotRegisters");
        if (riotRegisters.length != Atari2600Constants.RIOT_REGISTER_COUNT) {
            throw new IllegalArgumentException(
                    "RIOT registers must be " + Atari2600Constants.RIOT_REGISTER_COUNT + " bytes");
        }
        Objects.requireNonNull(registers, "registers");
        Objects.requireNonNull(mapperName, "mapperName");
        Objects.requireNonNull(mapperState, "mapperState");
        Objects.requireNonNull(mapperRam, "mapperRam");
        if (mapperBank < -1 || mapperBankCount < 0) {
            throw new IllegalArgumentException(
                    "invalid mapper bank state: " + mapperBank + "/" + mapperBankCount);
        }
    }
}
