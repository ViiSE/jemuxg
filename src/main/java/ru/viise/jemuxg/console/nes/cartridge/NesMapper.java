package ru.viise.jemuxg.console.nes.cartridge;

import ru.viise.jemuxg.api.hardware.Mirroring;
import ru.viise.jemuxg.api.storage.Mapper;

/**
 * Маппер NES-картриджа. Кроме банковской логики хранит состояние
 * зеркалирования и доступности PRG RAM.
 */
public interface NesMapper extends Mapper {

    /**
     * @return номер маппера в формате iNES
     */
    int id();

    String name();

    Mirroring mirroring();

    /**
     * @return включена ли PRG RAM в окне $6000..$7FFF
     */
    default boolean prgRamEnabled() {
        return true;
    }

    /**
     * @return можно ли писать в PRG RAM
     */
    default boolean prgRamWritable() {
        return true;
    }

    /**
     * Такт счетчика IRQ (MMC3 использует подъём A12 PPU).
     */
    default void clockIrq() {
    }

    default boolean irqPending() {
        return false;
    }

    default void clearIrq() {
    }
}
