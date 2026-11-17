package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер 3E (Tigervision): 2K ROM-банки и 32K RAM.
 *
 * <p>Младшие 2K адресного окна выбирают ROM-банк или 1K RAM-банк,
 * старшие 2K всегда указывают на последний ROM-банк. Запись в
 * {@code $003F} выбирает ROM-банк, запись в {@code $003E} — RAM-банк.</p>
 */
public final class Atari3EMapper implements AtariMapper {

    private static final int BANK_SIZE = 0x800;
    private static final int RAM_BANK_SIZE = 0x400;
    private static final int RAM_BANK_COUNT = 32;

    private final long romSize;
    private final int romBankCount;
    private final int[] ramBanks = new int[RAM_BANK_COUNT];
    private final byte[] ram = new byte[RAM_BANK_COUNT * RAM_BANK_SIZE];

    private int romBank;
    private int ramBank;
    private boolean ramSelected;

    public Atari3EMapper(long romSize) {
        if (romSize < BANK_SIZE * 2 || romSize % BANK_SIZE != 0) {
            throw new IllegalArgumentException(
                    "3E ROM size must be a multiple of 2K and at least 4K, got " + romSize);
        }
        this.romSize = romSize;
        this.romBankCount = (int) (romSize / BANK_SIZE);
        for (int i = 0; i < ram.length; i++) {
            ram[i] = (byte) 0xFF;
        }
        for (int i = 0; i < ramBanks.length; i++) {
            ramBanks[i] = i;
        }
    }

    @Override
    public long map(int address) {
        int offset = cartridgeOffset(address);
        if (offset < 0) {
            return -1;
        }
        if (offset >= BANK_SIZE) {
            return (long) (romBankCount - 1) * BANK_SIZE + (offset - BANK_SIZE);
        }
        if (ramSelected) {
            return -1;
        }
        return (long) romBank * BANK_SIZE + offset;
    }

    @Override
    public int readByte(int address) {
        int offset = cartridgeOffset(address);
        if (offset < 0 || offset >= BANK_SIZE || !ramSelected) {
            return -1;
        }
        if (offset >= RAM_BANK_SIZE) {
            return -1;
        }
        return ram[ramBank * RAM_BANK_SIZE + offset] & 0xFF;
    }

    @Override
    public void write(int address, int value) {
        if (address == 0x3F) {
            romBank = Math.floorMod(value, romBankCount);
            ramSelected = false;
            return;
        }
        if (address == 0x3E) {
            ramBank = value & 0x1F;
            ramSelected = true;
            return;
        }

        int offset = cartridgeOffset(address);
        if (offset < 0 || offset >= BANK_SIZE || !ramSelected) {
            return;
        }
        if (offset >= RAM_BANK_SIZE) {
            ram[ramBank * RAM_BANK_SIZE + (offset - RAM_BANK_SIZE)] = (byte) value;
        }
    }

    @Override
    public String name() {
        return "3E";
    }

    @Override
    public int bank() {
        return ramSelected ? romBankCount + ramBank : romBank;
    }

    @Override
    public int bankCount() {
        return romBankCount + RAM_BANK_COUNT;
    }

    @Override
    public boolean writable() {
        return true;
    }

    @Override
    public byte[] ramSnapshot() {
        return ram.clone();
    }

    @Override
    public String debugState() {
        return "romBank=" + romBank + " ramBank=" + ramBank
                + " ramSelected=" + ramSelected;
    }

    private static int cartridgeOffset(int address) {
        if (address < Atari2600Constants.CARTRIDGE_BASE
                || address > Atari2600Constants.CARTRIDGE_END) {
            return -1;
        }
        return address - Atari2600Constants.CARTRIDGE_BASE;
    }
}
