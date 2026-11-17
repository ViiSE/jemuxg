package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер E7 (M-Network): два 2K-сегмента, ROM 8/12/16K и 2K RAM.
 *
 * <p>Младший 2K-сегмент выбирает ROM-банк или 1K RAM. Верхний сегмент
 * содержит последний 2K ROM-банк, поверх которого расположены порты
 * 256-байтного RAM-блока.</p>
 */
public final class AtariE7Mapper implements AtariMapper {

    private static final int BANK_SIZE = 0x800;
    private static final int RAM_LOWER_SIZE = 0x400;
    private static final int RAM_UPPER_SIZE = 0x100;
    private static final int RAM_SIZE = RAM_LOWER_SIZE + 4 * RAM_UPPER_SIZE;

    private static final int[] BANKS_6 = {0, 1, 0, 1, 2, 3, 4, 5};

    private final int romBankCount;
    private final byte[] ram = new byte[RAM_SIZE];

    private int lowerBank;
    private int ramBank;

    public AtariE7Mapper(long romSize) {
        if (romSize != 8192 && romSize != 12288 && romSize != 16384) {
            throw new IllegalArgumentException(
                    "E7 ROM must be 8K, 12K or 16K, got " + romSize);
        }
        romBankCount = (int) (romSize / BANK_SIZE);
        for (int i = 0; i < ram.length; i++) {
            ram[i] = (byte) 0xFF;
        }
    }

    @Override
    public long map(int address) {
        int offset = cartridgeOffset(address);
        if (offset < 0) {
            return -1;
        }
        if (offset < BANK_SIZE) {
            if (lowerBank == romBankCount - 1) {
                return -1;
            }
            return (long) lowerBank * BANK_SIZE + offset;
        }
        if (offset < 0xA00) {
            return -1;
        }
        return (long) (romBankCount - 1) * BANK_SIZE + (offset - BANK_SIZE);
    }

    @Override
    public int readByte(int address) {
        int offset = cartridgeOffset(address);
        if (offset < 0) {
            return -1;
        }
        if (offset < BANK_SIZE && lowerBank == romBankCount - 1) {
            return ram[offset & (RAM_LOWER_SIZE - 1)] & 0xFF;
        }
        if (offset >= 0x800 && offset < 0xA00) {
            int block = offset & 0xFF;
            return ram[RAM_LOWER_SIZE + ramBank * RAM_UPPER_SIZE + block] & 0xFF;
        }
        return -1;
    }

    @Override
    public void write(int address, int value) {
        if (address >= 0x1FE0 && address <= 0x1FE7) {
            selectLowerBank(address);
            return;
        }
        if (address >= 0x1FE8 && address <= 0x1FEB) {
            ramBank = address & 0x03;
            return;
        }

        int offset = cartridgeOffset(address);
        if (offset < 0) {
            return;
        }
        if (offset < RAM_LOWER_SIZE && lowerBank == romBankCount - 1) {
            ram[offset] = (byte) value;
        } else if (offset >= 0x800 && offset < 0x900) {
            int block = offset & 0xFF;
            ram[RAM_LOWER_SIZE + ramBank * RAM_UPPER_SIZE + block] = (byte) value;
        }
    }

    private void selectLowerBank(int address) {
        int index = address & 0x07;
        lowerBank = switch (romBankCount) {
            case 4 -> address >= 0x1FE4 ? (address & 0x03) : lowerBank;
            case 6 -> BANKS_6[index];
            case 8 -> index;
            default -> lowerBank;
        };
    }

    @Override
    public String name() {
        return "E7";
    }

    @Override
    public int bank() {
        return lowerBank;
    }

    @Override
    public int bankCount() {
        return romBankCount;
    }

    @Override
    public boolean writable() {
        return true;
    }

    public byte[] ramSnapshot() {
        return ram.clone();
    }

    private static int cartridgeOffset(int address) {
        if (address < Atari2600Constants.CARTRIDGE_BASE
                || address > Atari2600Constants.CARTRIDGE_END) {
            return -1;
        }
        return address - Atari2600Constants.CARTRIDGE_BASE;
    }
}
