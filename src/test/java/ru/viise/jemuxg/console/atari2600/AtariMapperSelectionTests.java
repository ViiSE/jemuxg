package ru.viise.jemuxg.console.atari2600;

import java.util.Map;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleConfig;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariCartridge;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariMapperSelector;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Тесты автоопределения схемы картриджа Atari 2600 и явного выбора.
 */
public final class AtariMapperSelectionTests {

    private static int checks;

    private AtariMapperSelectionTests() {
    }

    public static void main(String[] args) {
        autoBySize();
        autoByName();
        explicitSchemes();
        validation();
        cartridgeAndConsoleConfig();
        System.out.println("Atari mapper selection tests OK, checks=" + checks);
    }

    private static void autoBySize() {
        check(mapperName(rom(2048), null).equals("2K"), "auto 2K");
        check(mapperName(rom(4096), null).equals("4K"), "auto 4K");
        check(mapperName(rom(8192), null).equals("F8"), "auto 8K defaults to F8");
        check(mapperName(rom(10240), null).equals("DPC"), "auto 10K selects DPC");
        check(mapperName(rom(10495), null).equals("DPC"), "auto padded Pitfall selects DPC");
        check(mapperName(rom(12288), null).equals("E7"), "auto 12K selects E7");
        check(mapperName(rom(16384), null).equals("F6"), "auto 16K selects F6");
        check(mapperName(rom(32768), null).equals("F4"), "auto 32K selects F4");
    }

    private static void autoByName() {
        check(mapperName(rom(8192, "maze_e0.a26"), null).equals("E0"),
                "name hint e0");
        check(mapperName(rom(8192, "fishing_fe.a26"), null).equals("FE"),
                "name hint fe");
        check(mapperName(rom(8192, "f8sc_game.a26"), null).equals("F8SC"),
                "name hint f8sc");
        check(mapperName(rom(16384, "e7_game.a26"), null).equals("E7"),
                "name hint e7 for 16K");
        check(mapperName(rom(16384, "f6sc_game.a26"), null).equals("F6SC"),
                "name hint f6sc");
        check(mapperName(rom(32768, "f4sc_game.a26"), null).equals("F4SC"),
                "name hint f4sc");
        check(mapperName(rom(8192, "plain.a26"), null).equals("F8"),
                "plain 8K name keeps F8");
        check(mapperName(rom(8192, "Defender.a26"), null).equals("F8"),
                "substring fe does not force FE");
        check(mapperName(rom(8192, "maze(e0).a26"), null).equals("E0"),
                "parenthesized e0 hint");
    }

    private static void explicitSchemes() {
        check(mapperName(rom(2048), "2k").equals("2K"), "explicit 2K");
        check(mapperName(rom(4096), "4k").equals("4K"), "explicit 4K");
        check(mapperName(rom(8192), "f8").equals("F8"), "explicit F8");
        check(mapperName(rom(16384), "f6").equals("F6"), "explicit F6");
        check(mapperName(rom(32768), "f4").equals("F4"), "explicit F4");
        check(mapperName(rom(8192), "f8sc").equals("F8SC"), "explicit F8SC");
        check(mapperName(rom(16384), "f6sc").equals("F6SC"), "explicit F6SC");
        check(mapperName(rom(32768), "f4sc").equals("F4SC"), "explicit F4SC");
        check(mapperName(rom(8192), "3f").equals("3F"), "explicit 3F");
        check(mapperName(rom(8192), "3e").equals("3E"), "explicit 3E");
        check(mapperName(rom(8192), "e0").equals("E0"), "explicit E0");
        check(mapperName(rom(12288), "e7").equals("E7"), "explicit E7");
        check(mapperName(rom(8192), "fe").equals("FE"), "explicit FE");
        check(mapperName(rom(10240), "dpc").equals("DPC"), "explicit DPC");
        check(mapperName(rom(10495), "DPC").equals("DPC"), "explicit DPC padded");
        check(mapperName(rom(8192), "AUTO").equals("F8"), "explicit AUTO");
        check(mapperName(rom(8192), " F8 ").equals("F8"), "scheme is trimmed");
    }

    private static void validation() {
        expectFailure(() -> new AtariCartridge(rom(8192), "bogus"),
                "unknown mapper rejected");
        expectFailure(() -> new AtariCartridge(rom(8192), "f6"),
                "F6 size mismatch rejected");
        expectFailure(() -> new AtariCartridge(rom(16384), "e0"),
                "E0 size mismatch rejected");
        expectFailure(() -> new AtariCartridge(rom(32768), "e7"),
                "E7 size mismatch rejected");
        expectFailure(() -> new AtariCartridge(rom(6144)),
                "auto ambiguous size rejected");
        expectFailure(() -> new AtariCartridge(rom(3072), "3f"),
                "3F non-2K size rejected");
        expectFailure(() -> new AtariCartridge(rom(4096), "2k"),
                "2K strict size rejected");
    }

    private static void cartridgeAndConsoleConfig() {
        AtariCartridge cartridge = new AtariCartridge(rom(8192), "e0");
        check(cartridge.mapper().name().equals("E0"),
                "cartridge uses explicit mapper");

        Atari2600Provider provider = new Atari2600Provider();
        Console console = provider.create(new ConsoleConfig(Map.of("mapper", "3f")));
        Media media = rom(8192);
        console.machine().insert(0, media);
        Atari2600Machine machine = (Atari2600Machine) console.machine();
        Atari2600CartridgeSlot slot = (Atari2600CartridgeSlot) machine.slots().get(0);
        check(slot.cartridge() != null && slot.cartridge().mapper().name().equals("3F"),
                "ConsoleConfig mapper reaches cartridge slot");
        check(AtariMapperSelector.OPTION.equals("mapper"), "config option key");
    }

    private static String mapperName(Media media, String scheme) {
        return AtariMapperSelector.select(media, scheme).name();
    }

    private static Media rom(int size) {
        return rom(size, "game.a26");
    }

    private static Media rom(int size, String name) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i & 0xFF);
        }
        return new ByteArrayMedia(name, MediaKind.CARTRIDGE, data);
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            check(true, message);
            return;
        }
        check(false, message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
