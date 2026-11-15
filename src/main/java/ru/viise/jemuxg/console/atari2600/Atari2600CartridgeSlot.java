package ru.viise.jemuxg.console.atari2600;

import java.util.Optional;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.api.storage.Storage;
import ru.viise.jemuxg.api.storage.StorageSlot;

/**
 * Слот картриджа Atari 2600.
 *
 * <p>Пока это каркас: картридж хранится как носитель, а окно
 * {@code $1000..$1FFF} отвечает {@code 0xFF}. Разбор 2K/4K и банковских
 * схем появится в этапе 6.</p>
 */
public final class Atari2600CartridgeSlot implements StorageSlot, Bus {

    private Media media;
    private Storage storage;

    @Override
    public String id() {
        return "cartridge";
    }

    @Override
    public boolean isPresent() {
        return media != null;
    }

    @Override
    public Optional<Storage> storage() {
        return Optional.ofNullable(storage);
    }

    @Override
    public void insert(Media media) {
        if (media == null) {
            throw new IllegalArgumentException("media must not be null");
        }
        if (media.kind() != MediaKind.CARTRIDGE) {
            throw new IllegalArgumentException(
                    "Atari 2600 accepts cartridge media, got " + media.kind());
        }
        this.media = media;
        this.storage = new SlotStorage(media, this);
    }

    @Override
    public Media eject() {
        if (media == null) {
            return null;
        }
        Media ejected = media;
        media = null;
        storage = null;
        return ejected;
    }

    @Override
    public int read(int address) {
        return 0xFF;
    }

    @Override
    public void write(int address, int value) {
        // Банковские регистры появятся вместе с мапперами в этапе 6.
    }

    private record SlotStorage(Media media, Bus bus) implements Storage {
    }
}
