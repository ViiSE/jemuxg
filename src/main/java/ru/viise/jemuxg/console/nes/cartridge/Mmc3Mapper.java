package ru.viise.jemuxg.console.nes.cartridge;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Mirroring;

/**
 * MMC3 (mapper 4): 8 KiB PRG-банки, 2+4 KiB CHR-банки, переключаемое
 * зеркалирование и счетчик IRQ по подъёму A12.
 */
public final class Mmc3Mapper implements NesMapper {

    private static final int PRG_BANK_SIZE = 0x2000;
    private static final int CHR_BANK_SIZE = 0x0400;
    private static final int CHR_BANK_SIZE_2K = 0x0800;

    private final byte[] prgRom;
    private final byte[] chr;
    private final boolean chrRam;
    private final boolean fourScreen;

    private final int[] registers = new int[8];
    private int bankSelect;
    private int mirroringMode;
    private int prgRamProtect;

    private int irqLatch;
    private int irqCounter;
    private boolean irqReload;
    private boolean irqEnabled;
    private boolean irqPending;

    public Mmc3Mapper(
            byte[] prgRom,
            byte[] chr,
            boolean chrRam,
            Mirroring mirroring,
            boolean fourScreen) {
        this.prgRom = Objects.requireNonNull(prgRom, "prgRom");
        this.chr = Objects.requireNonNull(chr, "chr");
        this.chrRam = chrRam;
        this.fourScreen = fourScreen;
        this.mirroringMode = mirroringBits(Objects.requireNonNull(mirroring, "mirroring"));
    }

    @Override
    public int id() {
        return 4;
    }

    @Override
    public String name() {
        return "MMC3";
    }

    @Override
    public Mirroring mirroring() {
        if (fourScreen) {
            return Mirroring.FOUR_SCREEN;
        }
        return switch (mirroringMode & 0x03) {
            case 0 -> Mirroring.VERTICAL;
            case 1 -> Mirroring.HORIZONTAL;
            case 2 -> Mirroring.SINGLE_SCREEN_LOWER;
            default -> Mirroring.SINGLE_SCREEN_UPPER;
        };
    }

    @Override
    public void write(int address, int value) {
        if (address < 0x8000) {
            return;
        }
        if (address < 0xA000) {
            if ((address & 0x01) == 0) {
                bankSelect = value;
            } else {
                registers[bankSelect & 0x07] = value;
            }
            return;
        }
        if (address < 0xC000) {
            if ((address & 0x01) == 0) {
                mirroringMode = value & 0x03;
            } else {
                prgRamProtect = value;
            }
            return;
        }
        if (address < 0xE000) {
            if ((address & 0x01) == 0) {
                irqLatch = value;
            } else {
                irqCounter = 0;
                irqReload = true;
            }
            return;
        }
        if ((address & 0x01) == 0) {
            irqEnabled = false;
            irqPending = false;
        } else {
            irqEnabled = true;
        }
    }

    @Override
    public long map(int address) {
        if (address < 0x8000) {
            return -1;
        }
        int bankCount = prgRom.length / PRG_BANK_SIZE;
        if (bankCount <= 0) {
            return -1;
        }
        int lastBank = bankCount - 1;
        int secondLastBank = Math.max(0, bankCount - 2);
        boolean swapped = (bankSelect & 0x40) != 0;
        int bank;
        if (address < 0xA000) {
            bank = swapped ? secondLastBank : registers[6];
        } else if (address < 0xC000) {
            bank = registers[7];
        } else if (address < 0xE000) {
            bank = swapped ? registers[6] : secondLastBank;
        } else {
            bank = lastBank;
        }
        return MapperMath.offset(bank, PRG_BANK_SIZE, address, bankCount);
    }

    @Override
    public long mapPpu(int address) {
        if (address < 0 || address > 0x1FFF) {
            return -1;
        }
        int bankCount1K = chr.length / CHR_BANK_SIZE;
        int bankCount2K = chr.length / CHR_BANK_SIZE_2K;
        if (bankCount1K <= 0) {
            return -1;
        }

        boolean chrSwapped = (bankSelect & 0x80) != 0;
        if (!chrSwapped) {
            if (address < 0x0800) {
                return MapperMath.offset(registers[0] >> 1, CHR_BANK_SIZE_2K, address, bankCount2K);
            }
            if (address < 0x1000) {
                return MapperMath.offset(registers[1] >> 1, CHR_BANK_SIZE_2K, address, bankCount2K);
            }
            int index = 2 + ((address - 0x1000) >> 10);
            return MapperMath.offset(registers[index], CHR_BANK_SIZE, address, bankCount1K);
        }

        if (address < 0x1000) {
            int index = 2 + (address >> 10);
            return MapperMath.offset(registers[index], CHR_BANK_SIZE, address, bankCount1K);
        }
        if (address < 0x1800) {
            return MapperMath.offset(registers[0] >> 1, CHR_BANK_SIZE_2K, address, bankCount2K);
        }
        return MapperMath.offset(registers[1] >> 1, CHR_BANK_SIZE_2K, address, bankCount2K);
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
        return (prgRamProtect & 0x80) != 0;
    }

    @Override
    public boolean prgRamWritable() {
        return prgRamEnabled() && (prgRamProtect & 0x40) == 0;
    }

    @Override
    public void clockIrq() {
        if (irqReload) {
            irqCounter = irqLatch;
            irqReload = false;
            return;
        }
        if (irqCounter == 0) {
            irqCounter = irqLatch;
            return;
        }
        irqCounter--;
        if (irqCounter == 0 && irqEnabled) {
            irqPending = true;
        }
    }

    @Override
    public boolean irqPending() {
        return irqPending;
    }

    @Override
    public void clearIrq() {
        irqPending = false;
    }

    private static int mirroringBits(Mirroring mirroring) {
        return switch (mirroring) {
            case HORIZONTAL -> 1;
            case VERTICAL -> 0;
            case SINGLE_SCREEN_LOWER -> 2;
            case SINGLE_SCREEN_UPPER -> 3;
            case FOUR_SCREEN -> 0;
        };
    }
}
