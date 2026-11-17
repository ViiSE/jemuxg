package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariCartridge;
import ru.viise.jemuxg.core.memory.Ram;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Тесты картриджей и слота Atari 2600: 2K, 4K, reset-вектор и пустой слот.
 */
public final class AtariCartridgeTests {

    private static int checks;

    private AtariCartridgeTests() {
    }

    public static void main(String[] args) {
        twoKilobyteCartridge();
        fourKilobyteCartridge();
        cartridgeThroughBus();
        emptySlot();
        invalidMedia();
        unsupportedSize();
        System.out.println("Atari cartridge tests OK, checks=" + checks);
    }

    private static void twoKilobyteCartridge() {
        byte[] data = mediaBytes(2048);
        data[0x7FC] = 0x00;
        data[0x7FD] = 0x10;

        Atari2600CartridgeSlot slot = new Atari2600CartridgeSlot();
        Media media = new ByteArrayMedia("game.a26", MediaKind.CARTRIDGE, data);
        slot.insert(media);

        check(slot.isPresent(), "2K cartridge is present");
        check(slot.storage().isPresent(), "2K cartridge exposes storage");
        check(slot.storage().orElseThrow() instanceof AtariCartridge,
                "storage is AtariCartridge");

        AtariCartridge cartridge = slot.cartridge();
        check(cartridge != null, "slot exposes cartridge");
        check(cartridge.mapper().name().equals("2K"), "2K mapper name");
        check(cartridge.mapper().bank() == 0, "2K mapper bank");
        check(cartridge.mapper().bankCount() == 1, "2K mapper bank count");

        check(slot.read(0x1000) == (data[0x000] & 0xFF), "2K $1000 maps to ROM 0");
        check(slot.read(0x17FF) == (data[0x7FF] & 0xFF), "2K $17FF maps to ROM $7FF");
        check(slot.read(0x1800) == (data[0x000] & 0xFF), "2K $1800 mirrors ROM 0");
        check(slot.read(0x1FFF) == (data[0x7FF] & 0xFF), "2K $1FFF mirrors ROM $7FF");
        check(slot.read(0x0FFF) == 0xFF, "2K read outside window is $FF");
        check(cartridge.resetVector() == 0x1000, "2K reset vector from mirrored $1FFC");
    }

    private static void fourKilobyteCartridge() {
        byte[] data = mediaBytes(4096);
        data[0xFFC] = 0x34;
        data[0xFFD] = 0x12;

        Atari2600CartridgeSlot slot = new Atari2600CartridgeSlot();
        Media media = new ByteArrayMedia("game4k.a26", MediaKind.CARTRIDGE, data);
        slot.insert(media);

        AtariCartridge cartridge = slot.cartridge();
        check(cartridge.mapper().name().equals("4K"), "4K mapper name");
        check(slot.read(0x1000) == (data[0x000] & 0xFF), "4K $1000 maps to ROM 0");
        check(slot.read(0x1FFF) == (data[0xFFF] & 0xFF), "4K $1FFF maps to ROM $FFF");
        check(slot.read(0x17FF) == (data[0x7FF] & 0xFF), "4K $17FF maps directly");
        check(cartridge.resetVector() == 0x1234, "4K reset vector from $1FFC/$1FFD");

        slot.eject();
        check(!slot.isPresent(), "eject removes cartridge");
        check(slot.storage().isEmpty(), "ejected slot has no storage");
        check(slot.read(0x1000) == 0xFF, "ejected slot reads $FF");
    }

    private static void cartridgeThroughBus() {
        byte[] data = mediaBytes(2048);
        data[0x7FC] = 0x78;
        data[0x7FD] = 0x56;

        Atari2600CartridgeSlot slot = new Atari2600CartridgeSlot();
        slot.insert(new ByteArrayMedia("bus.a26", MediaKind.CARTRIDGE, data));
        Bus empty = new Bus() {
            @Override
            public int read(int address) {
                return 0xFF;
            }

            @Override
            public void write(int address, int value) {
            }
        };

        Atari2600Bus bus = new Atari2600Bus(empty, new Ram(128), empty, slot);
        check(bus.read(0x1000) == (data[0x000] & 0xFF), "bus $1000 reaches cartridge");
        check(bus.read(0x1800) == (data[0x000] & 0xFF), "bus $1800 reaches 2K mirror");
        check(bus.read(0x1FFC) == 0x78, "bus $1FFC reads reset vector low");
        check(bus.read(0x1FFD) == 0x56, "bus $1FFD reads reset vector high");
    }

    private static void emptySlot() {
        Atari2600CartridgeSlot slot = new Atari2600CartridgeSlot();
        check(!slot.isPresent(), "empty slot is not present");
        check(slot.storage().isEmpty(), "empty slot has no storage");
        check(slot.read(0x1000) == 0xFF, "empty slot reads $FF");
        slot.write(0x1000, 0x42);
        check(slot.read(0x1000) == 0xFF, "empty slot ignores writes");
        check(slot.eject() == null, "eject on empty slot returns null");
    }

    private static void invalidMedia() {
        Atari2600CartridgeSlot slot = new Atari2600CartridgeSlot();
        expectThrows(IllegalArgumentException.class, () -> slot.insert(
                new ByteArrayMedia("disc", MediaKind.CD, new byte[4096])),
                "non-cartridge media rejected");
    }

    private static void unsupportedSize() {
        Atari2600CartridgeSlot slot = new Atari2600CartridgeSlot();
        expectThrows(IllegalArgumentException.class, () -> slot.insert(
                new ByteArrayMedia("small", MediaKind.CARTRIDGE, new byte[1024])),
                "1K cartridge rejected");
        expectThrows(IllegalArgumentException.class, () -> slot.insert(
                new ByteArrayMedia("large", MediaKind.CARTRIDGE, new byte[12288])),
                "12K cartridge rejected");
    }

    private static byte[] mediaBytes(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ((i * 31 + 7) & 0xFF);
        }
        return data;
    }

    private static void expectThrows(
            Class<? extends Throwable> type, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable t) {
            check(type.isInstance(t), message);
            return;
        }
        throw new AssertionError("FAILED: " + message + " (no exception)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
