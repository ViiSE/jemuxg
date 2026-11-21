package ru.viise.jemuxg.console.sg1000;

import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.sg1000.cartridge.Sg1000Cartridge;
import ru.viise.jemuxg.console.sg1000.cartridge.Sg1000CartridgeSlot;
import ru.viise.jemuxg.hardware.cpu.z80.Z80Registers;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Проверки карты памяти и картриджей Sega SG-1000.
 */
public final class Sg1000MemoryTests {

    private static int checks;

    private Sg1000MemoryTests() {
    }

    public static void main(String[] args) {
        memoryMap();
        ramMirrors();
        cartridgeSizes();
        invalidCartridges();
        resetBehavior();
        cpuRunsFromRom();
        reinsertAndEject();
        System.out.println("SG-1000 memory tests OK, checks=" + checks);
    }

    private static void memoryMap() {
        Sg1000CartridgeSlot slot = new Sg1000CartridgeSlot();
        Sg1000Bus bus = new Sg1000Bus(slot);

        check(bus.read(0x0000) == 0xFF, "empty slot reads 0xFF at 0x0000");
        check(bus.read(0xBFFF) == 0xFF, "empty slot reads 0xFF at 0xBFFF");
        bus.write(0x0000, 0x55);
        check(bus.read(0x0000) == 0xFF, "write to empty ROM is ignored");

        slot.insert(rom(0x2000, 0x12, 0x34));
        check(bus.read(0x0000) == 0x12, "ROM is mapped from 0x0000");
        check(bus.read(0x1FFF) == 0x34, "last ROM byte is mapped");
        check(bus.read(0x2000) == 0xFF, "ROM gap is filled with 0xFF");
        check(bus.read(0xBFFF) == 0xFF, "top of ROM window is 0xFF");
        bus.write(0x0000, 0x99);
        check(bus.read(0x0000) == 0x12, "write to ROM is ignored");

        bus.write(0xC000, 0x11);
        check(bus.read(0xC000) == 0x11, "RAM starts at 0xC000");
        bus.write(0xC3FF, 0x22);
        check(bus.read(0xC3FF) == 0x22, "RAM ends at 0xC3FF");
        check(bus.read(0x10000) == 0xFF, "reads above 0xFFFF return 0xFF");
        bus.write(0x10000, 0x33);
        check(bus.read(0xC3FF) == 0x22, "writes above 0xFFFF are ignored");
    }

    private static void ramMirrors() {
        Sg1000CartridgeSlot slot = new Sg1000CartridgeSlot();
        Sg1000Bus bus = new Sg1000Bus(slot);
        byte[] rom = new byte[Sg1000Constants.MIN_ROM_SIZE];
        rom[0] = 0x12;
        slot.insert(new ByteArrayMedia("ram.sg", MediaKind.CARTRIDGE, rom));

        for (int block = 0; block < 16; block++) {
            int address = 0xC000 + block * Sg1000Constants.RAM_SIZE + 0x123;
            bus.write(address, block + 1);
            check(bus.read(0xC123) == block + 1,
                    "RAM block " + block + " mirrors 0xC123");
        }

        bus.write(0xC400, 0x22);
        check(bus.read(0xC000) == 0x22, "0xC400 mirrors 0xC000");
        bus.write(0xFFFF, 0x44);
        check(bus.read(0xC3FF) == 0x44, "0xFFFF mirrors 0xC3FF");
        check(bus.read(0xD000) == 0x22, "0xD000 mirrors 0xC000");
        check(bus.read(0xC7FF) == 0x44, "0xC7FF mirrors 0xC3FF");
        check(bus.read(0xC401) == 0x00, "0xC401 mirrors 0xC001");

        bus.reset();
        check(bus.read(0xC000) == 0x00 && bus.read(0xC3FF) == 0x00,
                "RAM reset clears all cells");
        check(bus.read(0x0000) == 0x12, "RAM reset does not unplug the cartridge");
    }

    private static void cartridgeSizes() {
        int[] sizes = {
            Sg1000Constants.MIN_ROM_SIZE,
            2 * Sg1000Constants.ROM_BANK_SIZE,
            3 * Sg1000Constants.ROM_BANK_SIZE,
            Sg1000Constants.MY_CARD_ROM_SIZE,
            5 * Sg1000Constants.ROM_BANK_SIZE,
            Sg1000Constants.MAX_ROM_SIZE
        };
        for (int size : sizes) {
            Sg1000Cartridge cartridge =
                    new Sg1000Cartridge(rom(size, 0x11, 0x22));
            check(cartridge.size() == size,
                    "cartridge size " + size + " is accepted");
            check(cartridge.read(0) == 0x11,
                    "cartridge " + size + " maps its first byte");
            check(cartridge.read(size - 1) == 0x22,
                    "cartridge " + size + " maps its last byte");
            check(cartridge.read(size) == 0xFF,
                    "cartridge " + size + " fills addresses past ROM with 0xFF");
        }

        Sg1000Cartridge myCard = new Sg1000Cartridge(
                rom(Sg1000Constants.MY_CARD_ROM_SIZE, 0x5A, 0xA5));
        check(myCard.size() == 0x8000, "Sega My Card is a 32 KB linear ROM");
        check(myCard.read(0xBFFF) == 0xFF,
                "32 KB My Card does not fill the whole 48 KB window");
    }

    private static void invalidCartridges() {
        int[] invalidSizes = {0, 0x1000, 0x2001, 0x3000, 0xC001, 0x10000};
        for (int size : invalidSizes) {
            boolean rejected = false;
            try {
                new Sg1000Cartridge(new ByteArrayMedia(
                        "bad.sg", MediaKind.CARTRIDGE, new byte[size]));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "ROM size " + size + " is rejected");
        }

        boolean rejected = false;
        try {
            new Sg1000Cartridge(new ByteArrayMedia(
                    "bad.cd", MediaKind.CD, new byte[Sg1000Constants.MIN_ROM_SIZE]));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "non-cartridge media is rejected");
    }

    private static void resetBehavior() {
        Sg1000Machine machine = new Sg1000Machine();
        machine.bus().write(0xC000, 0xAA);
        Z80Registers regs = machine.cpu().registers();
        regs.pc = 0x1234;
        regs.sp = 0x1111;
        regs.i = 0x12;
        regs.r = 0x34;
        regs.iff1 = true;
        regs.iff2 = true;
        regs.im = 2;

        machine.reset();
        check(regs.pc == 0, "reset starts the CPU at PC=0");
        check(regs.sp == 0xFFFF, "reset sets SP=0xFFFF");
        check(regs.i == 0 && regs.r == 0, "reset clears I and R");
        check(!regs.iff1 && !regs.iff2, "reset disables interrupts");
        check(regs.im == 0, "reset selects interrupt mode 0");
        check(machine.bus().read(0xC000) == 0x00, "reset clears RAM");
        check(machine.frameCount() == 0, "reset clears the frame counter");
    }

    private static void cpuRunsFromRom() {
        Sg1000Machine machine = new Sg1000Machine();
        byte[] program = new byte[Sg1000Constants.MIN_ROM_SIZE];
        program[0] = 0x3E; // LD A, 0x5A
        program[1] = 0x5A;
        program[2] = 0x32; // LD (0xC000), A
        program[3] = 0x00;
        program[4] = (byte) 0xC0;
        program[5] = 0x76; // HALT
        machine.insert(0, new ByteArrayMedia(
                "program.sg", MediaKind.CARTRIDGE, program));

        check(machine.cpu().registers().pc == 0,
                "CPU starts with the cartridge at 0x0000");
        machine.runFrame();
        check(machine.bus().read(0xC000) == 0x5A,
                "Z80 executes code from the cartridge and writes RAM");
        check(machine.cpu().halted(),
                "Z80 stops at HALT after the program finishes");

        machine.reset();
        check(machine.bus().read(0xC000) == 0x00,
                "reset clears RAM written by the previous frame");
        machine.runFrame();
        check(machine.bus().read(0xC000) == 0x5A,
                "the same cartridge runs again after reset");
    }

    private static void reinsertAndEject() {
        Sg1000Machine machine = new Sg1000Machine();
        Media first = rom(Sg1000Constants.MIN_ROM_SIZE, 0x11, 0x22);
        Media second = rom(2 * Sg1000Constants.ROM_BANK_SIZE, 0x33, 0x44);

        machine.insert(0, first);
        machine.bus().write(0xC000, 0xAA);
        check(machine.bus().read(0) == 0x11, "first cartridge is visible");

        machine.insert(0, second);
        check(machine.bus().read(0) == 0x33, "reinserted cartridge is visible");
        check(machine.bus().read(0xC000) == 0x00,
                "reinsert resets RAM for determinism");
        check(machine.cartridgeSlot().cartridge().size() == second.size(),
                "reinsert replaces the cartridge board");

        Media ejected = machine.eject(0);
        check(ejected == second, "eject returns the current media");
        check(!machine.cartridgeSlot().isPresent(), "slot is empty after eject");
        check(machine.bus().read(0) == 0xFF,
                "empty slot reads 0xFF after eject");
        check(machine.bus().read(0xC000) == 0x00,
                "RAM remains addressable after eject");
    }

    private static Media rom(int size, int first, int last) {
        byte[] data = new byte[size];
        data[0] = (byte) first;
        data[size - 1] = (byte) last;
        return new ByteArrayMedia("rom.sg", MediaKind.CARTRIDGE, data);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
