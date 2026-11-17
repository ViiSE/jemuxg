package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.console.atari2600.cartridge.Atari3EMapper;
import ru.viise.jemuxg.console.atari2600.cartridge.Atari3FMapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariE0Mapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariE7Mapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariF8Mapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariFeMapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariSuperChipMapper;

/**
 * Тесты дополнительных схем картриджей Atari 2600.
 */
public final class AtariExtraMapperTests {

    private static int checks;

    public static void main(String[] args) {
        mapper3F();
        mapper3E();
        mapperE0();
        mapperE7();
        mapperFE();
        mapperSuperChip();
        System.out.println("Atari extra mapper tests OK, checks=" + checks);
    }

    private static void mapper3F() {
        Atari3FMapper mapper = new Atari3FMapper(8192);
        check(mapper.name().equals("3F"), "3F name");
        check(mapper.bankCount() == 4, "3F has four 2K banks");
        check(mapper.bank() == 0, "3F starts at bank 0");
        check(mapper.map(0x1000) == 0, "3F lower segment bank 0");
        check(mapper.map(0x1800) == 0x1800, "3F upper segment fixed last");
        mapper.write(0x3F, 1);
        check(mapper.bank() == 1, "3F hotspot selects bank 1");
        check(mapper.map(0x1000) == 0x800, "3F selected lower bank visible");
        mapper.write(0x3F, 5);
        check(mapper.bank() == 1, "3F bank wraps modulo count");
    }

    private static void mapper3E() {
        Atari3EMapper mapper = new Atari3EMapper(8192);
        check(mapper.name().equals("3E"), "3E name");
        check(mapper.bankCount() == 36, "3E has 4 ROM + 32 RAM banks");
        check(mapper.map(0x1000) == 0, "3E ROM lower bank");
        mapper.write(0x3F, 1);
        check(mapper.map(0x1000) == 0x800, "3E ROM bank switches");
        mapper.write(0x3E, 2);
        check(mapper.bank() == 6, "3E RAM bank selected");
        check(mapper.map(0x1000) == -1, "3E RAM replaces lower ROM");
        mapper.write(0x1400, 0x5A);
        check(mapper.readByte(0x1000) == 0x5A, "3E RAM write high port/read low port");
        check(mapper.map(0x1800) == 0x1800, "3E upper segment stays fixed");
    }

    private static void mapperE0() {
        AtariE0Mapper mapper = new AtariE0Mapper(8192);
        check(mapper.name().equals("E0"), "E0 name");
        check(mapper.bankCount() == 8, "E0 has 8 one-K banks");
        check(mapper.bank() == 4, "E0 initial segment 0 bank 4");
        check(mapper.map(0x1000) == 0x1000, "E0 segment 0 bank 4");
        check(mapper.map(0x1400) == 0x1400, "E0 segment 1 bank 5");
        check(mapper.map(0x1800) == 0x1800, "E0 segment 2 bank 6");
        check(mapper.map(0x1C00) == 0x1C00, "E0 last segment fixed bank 7");
        mapper.write(0x1FE0, 0);
        check(mapper.map(0x1000) == 0, "E0 segment 0 switches");
        mapper.write(0x1FE9, 0);
        check(mapper.map(0x1400) == 0x400, "E0 segment 1 switches");
        mapper.write(0x1FF2, 0);
        check(mapper.map(0x1800) == 0x800, "E0 segment 2 switches");
        check(mapper.map(0x1C00) == 0x1C00, "E0 last segment remains fixed");
    }

    private static void mapperE7() {
        AtariE7Mapper mapper = new AtariE7Mapper(8192);
        check(mapper.name().equals("E7"), "E7 name");
        check(mapper.bankCount() == 4, "E7 has four 2K banks");
        check(mapper.map(0x1000) == 0, "E7 lower ROM bank 0");
        check(mapper.map(0x1A00) == 0x1A00, "E7 upper fixed last bank");
        check(mapper.map(0x1800) == -1, "E7 upper RAM overlay");
        mapper.write(0x1FE7, 0);
        check(mapper.bank() == 3, "E7 selects lower RAM bank");
        check(mapper.map(0x1000) == -1, "E7 lower RAM replaces ROM");
        mapper.write(0x1000, 0x42);
        check(mapper.readByte(0x1400) == 0x42, "E7 lower RAM write/read ports");
        mapper.write(0x1FE8, 0);
        mapper.write(0x1800, 0x77);
        check(mapper.readByte(0x1900) == 0x77, "E7 upper RAM block 0");
        mapper.write(0x1FE9, 0);
        mapper.write(0x1800, 0x66);
        check(mapper.readByte(0x1900) == 0x66, "E7 upper RAM block 1");
    }

    private static void mapperFE() {
        AtariFeMapper mapper = new AtariFeMapper(8192);
        check(mapper.name().equals("FE"), "FE name");
        check(mapper.bankCount() == 2, "FE has two 4K banks");
        check(mapper.map(0x1000) == 0, "FE starts at bank 0");
        mapper.access(0x01FE, 0);
        mapper.access(0x0000, 0x00);
        check(mapper.map(0x1000) == 0x1000, "FE delayed latch selects bank 1");
        mapper.access(0x01FE, 0);
        mapper.access(0x0000, 0x20);
        check(mapper.map(0x1000) == 0, "FE delayed latch selects bank 0");
    }

    private static void mapperSuperChip() {
        AtariSuperChipMapper mapper = new AtariSuperChipMapper(new AtariF8Mapper());
        check(mapper.name().equals("F8SC"), "SuperChip name");
        check(mapper.writable(), "SuperChip is writable");
        check(mapper.map(0x1000) == -1, "SuperChip RAM replaces ROM window");
        mapper.write(0x1000, 0x5A);
        check(mapper.readByte(0x1080) == 0x5A, "SuperChip RAM write/read ports");
        mapper.write(0x107F, 0x7F);
        check(mapper.readByte(0x10FF) == 0x7F, "SuperChip RAM address mask");
        check(mapper.map(0x1800) == 0x800, "SuperChip base banking preserved");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }

    private AtariExtraMapperTests() {
    }
}
