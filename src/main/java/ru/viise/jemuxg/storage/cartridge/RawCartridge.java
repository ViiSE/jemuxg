package ru.viise.jemuxg.storage.cartridge;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.storage.Cartridge;
import ru.viise.jemuxg.api.storage.Mapper;
import ru.viise.jemuxg.api.storage.Media;

/**
 * Базовая плата картриджа: маппер решает, куда смотрит адрес.
 */
public final class RawCartridge implements Cartridge, Bus {

    private final Media media;
    private final Mapper mapper;

    public RawCartridge(Media media, Mapper mapper) {
        this.media = Objects.requireNonNull(media, "media");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Media media() {
        return media;
    }

    @Override
    public Mapper mapper() {
        return mapper;
    }

    @Override
    public Bus bus() {
        return this;
    }

    @Override
    public int read(int address) {
        long offset = mapper.map(address);
        if (offset < 0 || offset >= media.size()) {
            return 0xFF;
        }
        int value = media.readByte(offset);
        return value < 0 ? 0xFF : value;
    }

    @Override
    public void write(int address, int value) {
        mapper.write(address, value);
    }
}
