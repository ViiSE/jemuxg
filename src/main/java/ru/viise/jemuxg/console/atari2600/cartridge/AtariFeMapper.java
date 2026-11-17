package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер FE (Activision): 8K ROM, два 4K-банка.
 *
 * <p>Переключение использует отложенную защёлку: обращение к {@code $01FE}
 * запоминается, а следующий доступ выбирает банк по значению данных.</p>
 */
public final class AtariFeMapper implements AtariMapper {

    private static final int BANK_SIZE = 0x1000;
    private static final int BANK_COUNT = 2;

    private int bank;
    private boolean lastAccessWasFe;

    public AtariFeMapper(int romSize) {
        if (romSize != BANK_SIZE * BANK_COUNT) {
            throw new IllegalArgumentException("FE requires an 8K ROM, got " + romSize);
        }
    }

    /**
     * Сообщает мапперу о любом обращении CPU к адресному пространству.
     */
    public void access(int address, int value) {
        if (lastAccessWasFe) {
            bank = Math.floorMod(((value >> 5) ^ 7), BANK_COUNT);
            lastAccessWasFe = false;
        }
        lastAccessWasFe = (address & Atari2600Constants.ADDRESS_MASK) == 0x01FE;
    }

    @Override
    public long map(int address) {
        if (!inWindow(address)) {
            return -1;
        }
        return (long) bank * BANK_SIZE + (address & 0x0FFF);
    }

    @Override
    public void write(int address, int value) {
        access(address, value);
    }

    @Override
    public String name() {
        return "FE";
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
