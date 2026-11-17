package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер F4: 32 КБ ROM, восемь банков по 4 КБ.
 *
 * <p>Банк выбирается обращением к {@code $1FF4..$1FFB}. Переключение
 * срабатывает и при чтении, и при записи.</p>
 */
public final class AtariF4Mapper implements AtariMapper {

    private static final int BANK_SIZE = 0x1000;
    private static final int BANK_COUNT = 8;
    private static final int HOTSPOT_BASE = 0x1FF4;

    private int bank;

    public AtariF4Mapper() {
        this(0);
    }

    public AtariF4Mapper(int initialBank) {
        if (initialBank < 0 || initialBank >= BANK_COUNT) {
            throw new IllegalArgumentException("F4 bank must be 0..7, got " + initialBank);
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
        return "F4";
    }

    @Override
    public int bank() {
        return bank;
    }

    @Override
    public int bankCount() {
        return BANK_COUNT;
    }

    private static boolean inWindow(int address) {
        return address >= Atari2600Constants.CARTRIDGE_BASE
                && address <= Atari2600Constants.CARTRIDGE_END;
    }
}
