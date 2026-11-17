package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер F8: 8 КБ ROM, два банка по 4 КБ.
 *
 * <p>Банк выбирается обращением к {@code $1FF8} (банк 0) или
 * {@code $1FF9} (банк 1). Переключение срабатывает и при чтении,
 * и при записи.</p>
 */
public final class AtariF8Mapper implements AtariMapper {

    private static final int BANK_SIZE = 0x1000;
    private static final int BANK_COUNT = 2;

    private int bank;

    public AtariF8Mapper() {
        this(0);
    }

    public AtariF8Mapper(int initialBank) {
        if (initialBank < 0 || initialBank >= BANK_COUNT) {
            throw new IllegalArgumentException("F8 bank must be 0..1, got " + initialBank);
        }
        bank = initialBank;
    }

    @Override
    public long map(int address) {
        if (!inWindow(address)) {
            return -1;
        }
        int hotspot = address - 0x1FF8;
        if (hotspot == 0 || hotspot == 1) {
            bank = hotspot;
        }
        return (long) bank * BANK_SIZE + (address & 0x0FFF);
    }

    @Override
    public void write(int address, int value) {
        int hotspot = address - 0x1FF8;
        if (inWindow(address) && (hotspot == 0 || hotspot == 1)) {
            bank = hotspot;
        }
    }

    @Override
    public String name() {
        return "F8";
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
