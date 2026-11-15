package ru.viise.jemuxg.console.nes.cartridge;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Mirroring;

/**
 * CNROM (mapper 3): NROM-подобный PRG и переключаемые 8 KiB CHR-банки.
 * Запись в $8000..$FFFF выбирает CHR-банк.
 */
public final class CnromMapper implements NesMapper {

    private static final int CHR_BANK_SIZE = 0x2000;

    private final byte[] prgRom;
    private final byte[] chr;
    private final boolean chrRam;
    private final Mirroring mirroring;
    private final boolean fourScreen;
    private final int chrBankCount;
    private int chrBank;

    public CnromMapper(
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
        this.chrBankCount = Math.max(1, chr.length / CHR_BANK_SIZE);
    }

    @Override
    public int id() {
        return 3;
    }

    @Override
    public String name() {
        return "CNROM";
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
        if (chrRam) {
            return address;
        }
        return (long) chrBank * CHR_BANK_SIZE + address;
    }

    @Override
    public void write(int address, int value) {
        if (address < 0x8000 || chrRam) {
            return;
        }
        chrBank = Math.floorMod(value, chrBankCount);
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
