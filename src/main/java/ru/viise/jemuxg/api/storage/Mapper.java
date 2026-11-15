package ru.viise.jemuxg.api.storage;

/**
 * Преобразование адреса CPU/PPU в смещение внутри памяти картриджа.
 * <p>
 * Банковские мапперы (NROM, MMC1, MMC3 и т.п.) реализуют это через состояние
 * своих регистров. Запись в регистры маппера приходит через {@link #write(int, int)}.
 */
public interface Mapper {

    /**
     * Отображение адреса CPU в смещение внутри PRG-памяти.
     *
     * @return смещение в байтах или -1, если адрес не отображён
     */
    long map(int address);

    /**
     * Отображение адреса PPU (0x0000..0x1FFF) в смещение внутри CHR-памяти.
     *
     * @return смещение в байтах или -1, если адрес не отображён
     */
    default long mapPpu(int address) {
        return -1;
    }

    /**
     * Запись в регистры маппера. Для NROM это no-op.
     */
    default void write(int address, int value) {
    }

    /**
     * Запись в CHR RAM. Для CHR ROM и мапперов без CHR RAM — no-op.
     */
    default void writePpu(int address, int value) {
    }
}
