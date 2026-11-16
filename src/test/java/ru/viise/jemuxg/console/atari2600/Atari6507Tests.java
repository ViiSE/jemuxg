package ru.viise.jemuxg.console.atari2600;

import java.util.ArrayList;
import java.util.List;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.InterruptType;
import ru.viise.jemuxg.hardware.cpu.mos6507.Mos6507;

/**
 * Тесты адаптера MOS 6507 без сторонних библиотек.
 */
public final class Atari6507Tests {

    private static int checks;

    private Atari6507Tests() {
    }

    public static void main(String[] args) {
        resetVectorUses13BitBus();
        addressMasking();
        resetVectorAfterReset();
        noFalseIrqNmi();
        System.out.println("Atari 6507 tests OK, checks=" + checks);
    }

    private static void resetVectorUses13BitBus() {
        RecordingBus bus = new RecordingBus();
        bus.memory[0x1FFC] = 0x00;
        bus.memory[0x1FFD] = 0x10;

        Mos6507 cpu = new Mos6507(bus);

        check(cpu.registers().pc() == 0x1000,
                "6507 reset reads vector at $1FFC/$1FFD");
        check(bus.lastRead == 0x1FFD,
                "6507 reset bus address is masked to 13 bits");
        check(!bus.sawRead(0xFFFC) && !bus.sawRead(0xFFFD),
                "6507 never reads CPU addresses $FFFC/$FFFD directly");
    }

    private static void addressMasking() {
        RecordingBus bus = new RecordingBus();
        bus.memory[0x1FFF] = 0x5A;

        Mos6507 cpu = new Mos6507(bus);
        int value = cpu.readMemory(0xFFFF);

        check(value == 0x5A, "6507 read from $FFFF sees $1FFF");
        check(bus.lastRead == 0x1FFF, "6507 read address is masked");

        cpu.bus().write(0x2000, 0x77);
        check(bus.memory[0] == 0x77, "6507 write to $2000 sees $0000");
        check(bus.lastWrite == 0x0000, "6507 write address is masked");
    }

    private static void resetVectorAfterReset() {
        RecordingBus bus = new RecordingBus();
        bus.memory[0x1FFC] = 0x34;
        bus.memory[0x1FFD] = 0x12;

        Mos6507 cpu = new Mos6507(bus);
        cpu.interrupt(InterruptType.RESET);

        check(cpu.registers().pc() == 0x1234,
                "6507 RESET re-reads masked reset vector");
    }

    private static void noFalseIrqNmi() {
        RecordingBus bus = new RecordingBus();
        bus.memory[0x1FFC] = 0x00;
        bus.memory[0x1FFD] = 0x10;
        for (int i = 0; i < 8; i++) {
            bus.memory[0x1000 + i] = (byte) 0xEA; // NOP
        }

        Mos6507 cpu = new Mos6507(bus);
        cpu.setProgramCounter(0x1000);
        cpu.interrupt(InterruptType.NMI);
        cpu.interrupt(InterruptType.IRQ);

        for (int i = 0; i < 4; i++) {
            cpu.stepInstruction();
        }

        check(cpu.registers().pc() == 0x1004,
                "6507 ignores external IRQ/NMI");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }

    private static final class RecordingBus implements Bus {

        private final byte[] memory = new byte[0x2000];
        private final List<Integer> reads = new ArrayList<>();
        private int lastRead;
        private int lastWrite;

        @Override
        public int read(int address) {
            lastRead = address;
            reads.add(address);
            return memory[address] & 0xFF;
        }

        @Override
        public void write(int address, int value) {
            lastWrite = address;
            memory[address] = (byte) value;
        }

        private boolean sawRead(int address) {
            return reads.contains(address);
        }
    }
}
