package ru.viise.jemuxg.console.sg1000.cartridge;

import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.storage.Cartridge;
import ru.viise.jemuxg.api.storage.Mapper;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.sg1000.Sg1000Constants;

/**
 * Картридж Sega SG-1000.
 *
 * <p>Поддерживается линейный ROM размером от 8 до 48 КБ кратно 8 КБ,
 * включая 8, 16, 24, 32, 40 и 48 КБ. ROM отображается с {@code $0000};
 * адреса за его пределами читаются как {@code $FF}. Sega My Card (32 КБ)
 * ничем не отличается от обычного картриджа. BIOS и заголовка нет.</p>
 */
public final class Sg1000Cartridge implements Cartridge, Bus {

    private static final Mapper LINEAR_ROM = new Mapper() {
        @Override
        public long map(int address) {
            return address;
        }
    };

    private final Media media;

    public Sg1000Cartridge(Media media) {
        if (media.kind() != MediaKind.CARTRIDGE) {
            throw new IllegalArgumentException(
                    "SG-1000 accepts cartridge media, got " + media.kind());
        }
        long size = media.size();
        if (!isSupportedSize(size)) {
            throw new IllegalArgumentException(
                    "SG-1000 ROM size must be 8..48 KB in 8 KB steps, got " + size);
        }
        this.media = media;
    }

    private static boolean isSupportedSize(long size) {
        return size >= Sg1000Constants.MIN_ROM_SIZE
                && size <= Sg1000Constants.MAX_ROM_SIZE
                && size % Sg1000Constants.ROM_BANK_SIZE == 0;
    }

    /**
     * Размер ROM в байтах.
     */
    public long size() {
        return media.size();
    }

    @Override
    public Media media() {
        return media;
    }

    @Override
    public Mapper mapper() {
        return LINEAR_ROM;
    }

    @Override
    public Bus bus() {
        return this;
    }

    @Override
    public int read(int address) {
        if (address < 0 || address >= media.size()) {
            return 0xFF;
        }
        int value = media.readByte(address);
        return value < 0 ? 0xFF : value;
    }

    @Override
    public void write(int address, int value) {
        // Линейный ROM не имеет регистров.
    }
}
