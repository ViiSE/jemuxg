package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.core.memory.Ram;

/**
 * Тесты карты памяти Atari 2600 без сторонних библиотек.
 */
public final class Atari2600BusTests {

    private static int checks;

    private Atari2600BusTests() {
    }

    public static void main(String[] args) {
        tiaAndMirror();
        riotRam();
        riotRamMirrors();
        riotRegisters();
        cartridge();
        unmappedAddresses();
        addressMasking();
        System.out.println("Atari 2600 bus tests OK, checks=" + checks);
    }

    private static void tiaAndMirror() {
        ProbeBus tia = new ProbeBus();
        Atari2600Bus bus = newBus(tia);

        tia.readValue = 0x11;
        check(bus.read(0x0000) == 0x11 && tia.lastRead == 0x00,
                "TIA direct read at $0000");
        tia.readValue = 0x22;
        check(bus.read(0x003F) == 0x22 && tia.lastRead == 0x3F,
                "TIA direct read at $003F");

        tia.readValue = 0x33;
        check(bus.read(0x0040) == 0x33 && tia.lastRead == 0x00,
                "TIA mirror at $0040 maps to register 0");
        tia.readValue = 0x44;
        check(bus.read(0x007F) == 0x44 && tia.lastRead == 0x3F,
                "TIA mirror at $007F maps to register $3F");

        bus.write(0x0041, 0x5A);
        check(tia.lastWriteAddress == 0x01 && tia.lastWriteValue == 0x5A,
                "TIA mirror write maps to register 1");
    }

    private static void riotRam() {
        Ram ram = new Ram(Atari2600Constants.RIOT_RAM_SIZE);
        Atari2600Bus bus = newBus(new ProbeBus(), ram, new ProbeBus(), new ProbeBus());

        bus.write(0x0080, 0x12);
        bus.write(0x00FF, 0x34);
        check(bus.read(0x0080) == 0x12, "RIOT RAM at $0080");
        check(bus.read(0x00FF) == 0x34, "RIOT RAM at $00FF");
    }

    private static void riotRamMirrors() {
        Ram ram = new Ram(Atari2600Constants.RIOT_RAM_SIZE);
        Atari2600Bus bus = newBus(new ProbeBus(), ram, new ProbeBus(), new ProbeBus());

        bus.write(0x0180, 0x5A);
        check(bus.read(0x0080) == 0x5A, "RIOT RAM mirror $0180 maps to $0080");
        bus.write(0x01FF, 0x6B);
        check(bus.read(0x00FF) == 0x6B, "RIOT RAM mirror $01FF maps to $00FF");
        bus.write(0x0100, 0x7C);
        check(bus.read(0x0080) == 0x7C, "stack page $0100 mirrors RIOT RAM");
    }

    private static void riotRegisters() {
        ProbeBus riot = new ProbeBus();
        Atari2600Bus bus = newBus(new ProbeBus(), new Ram(128), riot, new ProbeBus());

        riot.readValue = 0x55;
        check(bus.read(0x0280) == 0x55 && riot.lastRead == 0x00,
                "RIOT register at $0280 maps to offset 0");
        riot.readValue = 0x66;
        check(bus.read(0x029F) == 0x66 && riot.lastRead == 0x1F,
                "RIOT register at $029F maps to offset $1F");

        bus.write(0x0281, 0x77);
        check(riot.lastWriteAddress == 0x01 && riot.lastWriteValue == 0x77,
                "RIOT write at $0281 maps to offset 1");
    }

    private static void cartridge() {
        ProbeBus cartridge = new ProbeBus();
        Atari2600Bus bus = newBus(new ProbeBus(), new Ram(128), new ProbeBus(), cartridge);

        cartridge.readValue = 0x88;
        check(bus.read(0x1000) == 0x88 && cartridge.lastRead == 0x1000,
                "cartridge read at $1000 gets absolute address");
        cartridge.readValue = 0x99;
        check(bus.read(0x1FFF) == 0x99 && cartridge.lastRead == 0x1FFF,
                "cartridge read at $1FFF gets absolute address");

        bus.write(0x1ABC, 0xAA);
        check(cartridge.lastWriteAddress == 0x1ABC && cartridge.lastWriteValue == 0xAA,
                "cartridge write at $1ABC gets absolute address");
    }

    private static void unmappedAddresses() {
        ProbeBus tia = new ProbeBus();
        ProbeBus riot = new ProbeBus();
        ProbeBus cartridge = new ProbeBus();
        Atari2600Bus bus = newBus(tia, new Ram(128), riot, cartridge);

        check(bus.read(0x027F) == 0xFF, "unmapped $027F reads $FF");
        check(bus.read(0x02A0) == 0xFF, "unmapped $02A0 reads $FF");
        check(bus.read(0x0FFF) == 0xFF, "unmapped $0FFF reads $FF");

        bus.write(0x02A0, 0xCD);
        check(tia.lastWriteAddress == -1 && riot.lastWriteAddress == -1
                        && cartridge.lastWriteAddress == -1,
                "unmapped writes are ignored");
    }

    private static void addressMasking() {
        ProbeBus tia = new ProbeBus();
        ProbeBus cartridge = new ProbeBus();
        Atari2600Bus bus = newBus(tia, new Ram(128), new ProbeBus(), cartridge);

        tia.readValue = 0x5A;
        check(bus.read(0x2000) == 0x5A && tia.lastRead == 0x00,
                "13-bit mask: $2000 maps to TIA $0000");

        cartridge.readValue = 0x6B;
        check(bus.read(0xFFFF) == 0x6B && cartridge.lastRead == 0x1FFF,
                "13-bit mask: $FFFF maps to cartridge $1FFF");

        bus.write(0x2000, 0x7C);
        check(tia.lastWriteAddress == 0x00 && tia.lastWriteValue == 0x7C,
                "13-bit mask applies to writes");
    }

    private static Atari2600Bus newBus(ProbeBus tia) {
        return newBus(tia, new Ram(Atari2600Constants.RIOT_RAM_SIZE),
                new ProbeBus(), new ProbeBus());
    }

    private static Atari2600Bus newBus(
            ProbeBus tia, Ram ram, ProbeBus riot, ProbeBus cartridge) {
        return new Atari2600Bus(tia, ram, riot, cartridge);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }

    private static final class ProbeBus implements Bus {

        private int readValue;
        private int lastRead = -1;
        private int lastWriteAddress = -1;
        private int lastWriteValue;

        @Override
        public int read(int address) {
            lastRead = address;
            return readValue;
        }

        @Override
        public void write(int address, int value) {
            lastWriteAddress = address;
            lastWriteValue = value;
        }
    }
}
