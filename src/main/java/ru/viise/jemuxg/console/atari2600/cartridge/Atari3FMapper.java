package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер 3F (Tigervision): два 2K-сегмента, нижний переключается
 * записью в TIA-страницу, верхний фиксирован на последнем банке ROM.
 */
public final class Atari3FMapper implements AtariMapper {

    private static final int BANK_SIZE = 0x800;
    private final int bankCount;
    private int bank;

    public Atari3FMapper(int romSize) {
        if (romSize <= 0 || romSize % BANK_SIZE != 0) {
            throw new IllegalArgumentException("3F ROM size must be a multiple of 2K, got " + romSize);
        }
        bankCount = romSize / BANK_SIZE;
    }

    @Override
    public long map(int address) {
        if (!inWindow(address)) {
            return -1;
        }
        int selected = address < 0x1800 ? bank : bankCount - 1;
        return (long) selected * BANK_SIZE + (address & 0x07FF);
    }

    @Override
    public void write(int address, int value) {
        if (address >= 0 && address <= 0x3F) {
            bank = Math.floorMod(value, bankCount);
        }
    }

    @Override
    public String name() {
        return "3F";
    }

    @Override
    public int bank() {
        return bank;
    }

    @Override
    public int bankCount() {
        return bankCount;
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeByte(bank);
    }

    @Override
    public void restore(StateReader reader) {
        int value = reader.readByte();
        if (value >= bankCount) {
            throw new IllegalArgumentException("invalid 3F bank in save state: " + value);
        }
        bank = value;
    }

    private static boolean inWindow(int address) {
        return address >= Atari2600Constants.CARTRIDGE_BASE
                && address <= Atari2600Constants.CARTRIDGE_END;
    }
}
