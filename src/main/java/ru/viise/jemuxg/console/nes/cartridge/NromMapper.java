package ru.viise.jemuxg.console.nes.cartridge;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Mirroring;

/**
 * NROM (mapper 0): без переключения банков.
 * 16 KiB PRG зеркалируется в $C000, 32 KiB используется целиком.
 */
public final class NromMapper implements NesMapper {

    private final byte[] prgRom;
    private final byte[] chr;
    private final boolean chrRam;
    private final Mirroring mirroring;
    private final boolean fourScreen;

    public NromMapper(
            byte[] prgRom,
            byte[] chr,
            boolean chrRam,
            Mirroring mirroring,
            boolean fourScreen) {
        this.prgRom = Objects.requireNonNull(prgRom, "prgRom");
        this.chr = Objects.requireNonNull(chr, "chr");
        this.chrRam = chrRam;
        this.mirroring = Objects.requireNonNull(mirroring, "mirroring");
        this.fourScreen = fourScreen;
    }

    @Override
    public int id() {
        return 0;
    }

    @Override
    public String name() {
        return "NROM";
    }

    @Override
    public Mirroring mirroring() {
        return fourScreen ? Mirroring.FOUR_SCREEN : mirroring;
    }

    @Override
    public long map(int address) {
        if (address < 0x8000) {
            return -1;
        }
        int offset = address - 0x8000;
        if (prgRom.length == 0x4000) {
            offset &= 0x3FFF;
        } else {
            offset = Math.floorMod(offset, prgRom.length);
        }
        return offset;
    }

    @Override
    public long mapPpu(int address) {
        if (address < 0 || address > 0x1FFF || chr.length == 0) {
            return -1;
        }
        return Math.floorMod(address, chr.length);
    }

    @Override
    public void writePpu(int address, int value) {
        if (!chrRam) {
            return;
        }
        long offset = mapPpu(address);
        if (offset >= 0 && offset < chr.length) {
            chr[(int) offset] = (byte) value;
        }
    }
}
