package ru.viise.jemuxg.storage.cartridge;

import ru.viise.jemuxg.api.storage.Mapper;

/**
 * Линейный маппер: адрес минус база = смещение в носителе.
 */
public final class LinearMapper implements Mapper {

    private final int baseAddress;

    public LinearMapper(int baseAddress) {
        this.baseAddress = baseAddress;
    }

    @Override
    public long map(int address) {
        long offset = (long) address - baseAddress;
        return offset >= 0 ? offset : -1;
    }
}
