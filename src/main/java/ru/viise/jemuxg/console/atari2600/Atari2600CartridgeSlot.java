package ru.viise.jemuxg.console.atari2600;

import java.util.Optional;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.api.storage.Storage;
import ru.viise.jemuxg.api.storage.StorageSlot;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariCartridge;

/**
 * Слот картриджа Atari 2600.
 *
 * <p>Принимает только {@link MediaKind#CARTRIDGE}. Поддерживаются
 * фиксированные картриджи 2 КБ и 4 КБ; банковские схемы появятся
 * на этапе 6. Пустой слот читается как {@code 0xFF}, запись игнорируется.</p>
 */
public final class Atari2600CartridgeSlot implements StorageSlot, Bus {

    private AtariCartridge cartridge;

    @Override
    public String id() {
        return "cartridge";
    }

    @Override
    public boolean isPresent() {
        return cartridge != null;
    }

    @Override
    public Optional<Storage> storage() {
        return Optional.ofNullable(cartridge);
    }

    @Override
    public void insert(Media media) {
        cartridge = new AtariCartridge(media);
    }

    @Override
    public Media eject() {
        if (cartridge == null) {
            return null;
        }
        Media ejected = cartridge.media();
        cartridge = null;
        return ejected;
    }

    @Override
    public int read(int address) {
        return cartridge == null ? 0xFF : cartridge.read(address);
    }

    @Override
    public void write(int address, int value) {
        if (cartridge != null) {
            cartridge.write(address, value);
        }
    }

    public AtariCartridge cartridge() {
        return cartridge;
    }
}
