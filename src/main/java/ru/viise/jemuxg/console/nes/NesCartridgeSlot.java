package ru.viise.jemuxg.console.nes;

import java.util.Optional;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Mirroring;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.api.storage.Storage;
import ru.viise.jemuxg.api.storage.StorageSlot;
import ru.viise.jemuxg.console.nes.cartridge.NesCartridge;
import ru.viise.jemuxg.console.nes.cartridge.NesMapper;

/**
 * Слот картриджа NES. Сам слот тоже является шиной: пока картриджа нет,
 * CPU читает 0xFF.
 */
public final class NesCartridgeSlot implements StorageSlot, Bus {

    private static final Bus EMPTY_CHR = new Bus() {
        @Override
        public int read(int address) {
            return 0;
        }

        @Override
        public void write(int address, int value) {
        }
    };

    private NesCartridge cartridge;

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
        if (media == null) {
            throw new IllegalArgumentException("media must not be null");
        }
        if (media.kind() != MediaKind.CARTRIDGE) {
            throw new IllegalArgumentException("NES accepts cartridge media, got " + media.kind());
        }
        cartridge = new NesCartridge(media);
    }

    @Override
    public Media eject() {
        if (cartridge == null) {
            return null;
        }
        Media media = cartridge.media();
        cartridge = null;
        return media;
    }

    /**
     * CHR-шина для PPU. Без картриджа возвращает пустую шину.
     */
    public Bus chrBus() {
        return cartridge == null ? EMPTY_CHR : cartridge.chrBus();
    }

    public Mirroring mirroring() {
        return cartridge == null ? Mirroring.HORIZONTAL : cartridge.mirroring();
    }

    public NesMapper mapper() {
        return cartridge == null ? null : cartridge.mapper();
    }

    public NesCartridge cartridge() {
        return cartridge;
    }

    /**
     * Копия PRG RAM текущего картриджа; пустой массив, если картриджа нет.
     */
    public byte[] prgRamSnapshot() {
        return cartridge == null ? new byte[0] : cartridge.prgRamSnapshot();
    }

    @Override
    public int read(int address) {
        return cartridge == null ? 0xFF : cartridge.bus().read(address);
    }

    @Override
    public void write(int address, int value) {
        if (cartridge != null) {
            cartridge.bus().write(address, value);
        }
    }
}
