package ru.viise.jemuxg.console.nes.cartridge;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Mirroring;

/**
 * MMC1 (mapper 1): последовательный сдвиговый регистр и 16 KiB PRG-банки.
 * Поддерживаются все четыре режима PRG и оба режима CHR.
 */
public final class Mmc1Mapper implements NesMapper {

    private static final int PRG_BANK_SIZE = 0x4000;
    private static final int CHR_BANK_SIZE = 0x1000;
    private static final int CHR_BANK_SIZE_8K = 0x2000;

    private final byte[] prgRom;
    private final byte[] chr;
    private final boolean chrRam;
    private final boolean fourScreen;

    private int shiftRegister;
    private int shiftCount;
    private int control;
    private int chrBank0;
    private int chrBank1;
    private int prgBank;

    public Mmc1Mapper(
            byte[] prgRom,
            byte[] chr,
            boolean chrRam,
            Mirroring mirroring,
            boolean fourScreen) {
        this.prgRom = Objects.requireNonNull(prgRom, "prgRom");
        this.chr = Objects.requireNonNull(chr, "chr");
        this.chrRam = chrRam;
        this.fourScreen = fourScreen;
        this.control = 0x0C | mirroringBits(Objects.requireNonNull(mirroring, "mirroring"));
    }

    @Override
    public int id() {
        return 1;
    }

    @Override
    public String name() {
        return "MMC1";
    }

    @Override
    public Mirroring mirroring() {
        if (fourScreen) {
            return Mirroring.FOUR_SCREEN;
        }
        return switch (control & 0x03) {
            case 0 -> Mirroring.SINGLE_SCREEN_LOWER;
            case 1 -> Mirroring.SINGLE_SCREEN_UPPER;
            case 2 -> Mirroring.VERTICAL;
            default -> Mirroring.HORIZONTAL;
        };
    }

    @Override
    public void write(int address, int value) {
        if (address < 0x8000) {
            return;
        }
        if ((value & 0x80) != 0) {
            shiftRegister = 0;
            shiftCount = 0;
            control |= 0x0C;
            return;
        }
        shiftRegister |= (value & 0x01) << shiftCount;
        shiftCount++;
        if (shiftCount < 5) {
            return;
        }

        int data = shiftRegister & 0x1F;
        switch ((address >> 13) & 0x03) {
            case 0 -> control = data;
            case 1 -> chrBank0 = data;
            case 2 -> chrBank1 = data;
            case 3 -> prgBank = data;
            default -> {
            }
        }
        shiftRegister = 0;
        shiftCount = 0;
    }

    @Override
    public long map(int address) {
        if (address < 0x8000) {
            return -1;
        }
        int prgMode = (control >> 2) & 0x03;
        int bankCount = prgRom.length / PRG_BANK_SIZE;
        if (prgMode == 0 || prgMode == 1) {
            int firstBank = prgBank & 0x0E;
            if (bankCount <= 0) {
                return -1;
            }
            firstBank = Math.floorMod(firstBank, bankCount);
            return (long) firstBank * PRG_BANK_SIZE + (address - 0x8000);
        }
        int bank;
        if (prgMode == 2) {
            bank = address < 0xC000 ? 0 : (prgBank & 0x0F);
        } else {
            bank = address < 0xC000 ? (prgBank & 0x0F) : bankCount - 1;
        }
        return MapperMath.offset(bank, PRG_BANK_SIZE, address, bankCount);
    }

    @Override
    public long mapPpu(int address) {
        if (address < 0 || address > 0x1FFF) {
            return -1;
        }
        int chrMode = (control >> 4) & 0x01;
        if (chrMode == 0) {
            int bankCount8K = chr.length / CHR_BANK_SIZE_8K;
            return MapperMath.offset(chrBank0 >> 1, CHR_BANK_SIZE_8K, address, bankCount8K);
        }
        int bankCount4K = chr.length / CHR_BANK_SIZE;
        int bank = address < 0x1000 ? chrBank0 : chrBank1;
        return MapperMath.offset(bank, CHR_BANK_SIZE, address, bankCount4K);
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

    @Override
    public boolean prgRamEnabled() {
        return (prgBank & 0x10) == 0;
    }

    @Override
    public boolean prgRamWritable() {
        return prgRamEnabled();
    }

    private static int mirroringBits(Mirroring mirroring) {
        return switch (mirroring) {
            case SINGLE_SCREEN_LOWER, FOUR_SCREEN -> 0;
            case SINGLE_SCREEN_UPPER -> 1;
            case VERTICAL -> 2;
            case HORIZONTAL -> 3;
        };
    }
}
