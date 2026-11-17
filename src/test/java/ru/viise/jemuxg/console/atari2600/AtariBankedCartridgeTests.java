package ru.viise.jemuxg.console.atari2600;

import java.util.Arrays;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariCartridge;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariMapper;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Тесты банковых картриджей Atari 2600: F8, F6, F4.
 */
public final class AtariBankedCartridgeTests {

    private static int checks;

    public static void main(String[] args) {
        f8();
        f6();
        f4();
        boundsAndDetection();
        System.out.println("Atari banked cartridge tests OK, checks=" + checks);
    }

    private static void f8() {
        AtariCartridge cartridge = cartridge(2);
        check("F8".equals(cartridge.mapper().name()), "F8 mapper name");
        check(cartridge.mapper().bankCount() == 2, "F8 has two banks");
        check(cartridge.mapper().bank() == 0, "F8 starts in bank 0");
        check(cartridge.read(0x1000) == 0x10, "F8 bank 0 visible");
        check(cartridge.resetVector() == 0x2001, "F8 reset vector from bank 0");

        check(cartridge.read(0x1FF9) == 0x11, "F8 read hotspot selects bank 1");
        check(cartridge.mapper().bank() == 1, "F8 read hotspot changes bank");
        check(cartridge.read(0x1000) == 0x11, "F8 bank 1 visible");
        check(cartridge.resetVector() == 0x2002, "F8 reset vector from bank 1");

        cartridge.write(0x1FF8, 0);
        check(cartridge.mapper().bank() == 0, "F8 write hotspot selects bank 0");
        check(cartridge.read(0x1000) == 0x10, "F8 bank 0 restored");
    }

    private static void f6() {
        AtariCartridge cartridge = cartridge(4);
        check("F6".equals(cartridge.mapper().name()), "F6 mapper name");
        check(cartridge.mapper().bankCount() == 4, "F6 has four banks");
        for (int bank = 0; bank < 4; bank++) {
            cartridge.write(0x1FF6 + bank, 0);
            check(cartridge.mapper().bank() == bank, "F6 selects bank " + bank);
            check(cartridge.read(0x1000) == 0x10 + bank, "F6 bank " + bank + " visible");
        }
        check(cartridge.resetVector() == 0x2004, "F6 reset vector from bank 3");
    }

    private static void f4() {
        AtariCartridge cartridge = cartridge(8);
        check("F4".equals(cartridge.mapper().name()), "F4 mapper name");
        check(cartridge.mapper().bankCount() == 8, "F4 has eight banks");
        for (int bank = 0; bank < 8; bank++) {
            cartridge.write(0x1FF4 + bank, 0);
            check(cartridge.mapper().bank() == bank, "F4 selects bank " + bank);
            check(cartridge.read(0x1000) == 0x10 + bank, "F4 bank " + bank + " visible");
        }
        check(cartridge.resetVector() == 0x2008, "F4 reset vector from bank 7");
    }

    private static void boundsAndDetection() {
        AtariCartridge cartridge = cartridge(2);
        AtariMapper mapper = cartridge.mapper();
        check(mapper.map(0x0FFF) == -1, "F8 ignores address below cartridge window");
        check(mapper.map(0x2000) == -1, "F8 ignores address above cartridge window");

        cartridge.write(0x1000, 0xFF);
        check(mapper.bank() == 0, "normal write does not switch F8 bank");
        cartridge.read(0x1FFF);
        check(mapper.bank() == 0, "normal read does not switch F8 bank");

        check(cartridge(2).mapper().name().equals("F8"), "8K detected as F8");
        check(cartridge(4).mapper().name().equals("F6"), "16K detected as F6");
        check(cartridge(8).mapper().name().equals("F4"), "32K detected as F4");

        try {
            cartridge(3);
            check(false, "12K cartridge rejected");
        } catch (IllegalArgumentException e) {
            check(true, "12K cartridge rejected");
        }
    }

    private static AtariCartridge cartridge(int banks) {
        byte[] data = new byte[banks * 0x1000];
        for (int bank = 0; bank < banks; bank++) {
            Arrays.fill(data, bank * 0x1000, (bank + 1) * 0x1000, (byte) (0x10 + bank));
            data[bank * 0x1000 + 0x0FFC] = (byte) (bank + 1);
            data[bank * 0x1000 + 0x0FFD] = 0x20;
        }
        return new AtariCartridge(new ByteArrayMedia(
                "banked-" + banks + ".a26", MediaKind.CARTRIDGE, data));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }

    private AtariBankedCartridgeTests() {
    }
}
