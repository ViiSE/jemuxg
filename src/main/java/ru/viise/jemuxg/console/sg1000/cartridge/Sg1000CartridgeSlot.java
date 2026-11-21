package ru.viise.jemuxg.console.sg1000.cartridge;

import java.util.Optional;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.Storage;
import ru.viise.jemuxg.api.storage.StorageSlot;

/**
 * Слот картриджа SG-1000.
 *
 * <p>Принимает только {@link ru.viise.jemuxg.api.storage.MediaKind#CARTRIDGE}.
 * Пустой слот читается как {@code 0xFF}, запись игнорируется.</p>
 */
public final class Sg1000CartridgeSlot implements StorageSlot, Bus {

    private Sg1000Cartridge cartridge;

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
        cartridge = new Sg1000Cartridge(media);
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

    public Sg1000Cartridge cartridge() {
        return cartridge;
    }
}
