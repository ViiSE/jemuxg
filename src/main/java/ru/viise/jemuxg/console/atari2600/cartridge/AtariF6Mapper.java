package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер F6: 16 КБ ROM, четыре банка по 4 КБ.
 *
 * <p>Банк выбирается обращением к {@code $1FF6..$1FF9}. Переключение
 * срабатывает и при чтении, и при записи.</p>
 */
public final class AtariF6Mapper implements AtariMapper {

    private static final int BANK_SIZE = 0x1000;
    private static final int BANK_COUNT = 4;
    private static final int HOTSPOT_BASE = 0x1FF6;

    private int bank;

    public AtariF6Mapper() {
        this(0);
    }

    public AtariF6Mapper(int initialBank) {
        if (initialBank < 0 || initialBank >= BANK_COUNT) {
            throw new IllegalArgumentException("F6 bank must be 0..3, got " + initialBank);
        }
        bank = initialBank;
    }

    @Override
    public long map(int address) {
        if (!inWindow(address)) {
            return -1;
        }
        int hotspot = address - HOTSPOT_BASE;
        if (hotspot >= 0 && hotspot < BANK_COUNT) {
            bank = hotspot;
        }
        return (long) bank * BANK_SIZE + (address & 0x0FFF);
    }

    @Override
    public void write(int address, int value) {
        int hotspot = address - HOTSPOT_BASE;
        if (inWindow(address) && hotspot >= 0 && hotspot < BANK_COUNT) {
            bank = hotspot;
        }
    }

    @Override
    public String name() {
        return "F6";
    }

    @Override
    public int bank() {
        return bank;
    }

    @Override
    public int bankCount() {
        return BANK_COUNT;
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeByte(bank);
    }

    @Override
    public void restore(StateReader reader) {
        int value = reader.readByte();
        if (value >= BANK_COUNT) {
            throw new IllegalArgumentException("invalid F6 bank in save state: " + value);
        }
        bank = value;
    }

    private static boolean inWindow(int address) {
        return address >= Atari2600Constants.CARTRIDGE_BASE
                && address <= Atari2600Constants.CARTRIDGE_END;
    }
}
