package ru.viise.jemuxg.console.atari2600;

import java.util.Arrays;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.hardware.riot.riot6532.RiotRam;

/**
 * Тесты RAM RIOT 6532.
 */
public final class AtariRiotRamTests {

    private static int checks;

    private AtariRiotRamTests() {
    }

    public static void main(String[] args) {
        sizeIs128();
        directReadWrite();
        addressMirroring();
        snapshotIsCopy();
        clearZeroesMemory();
        saveRestore();
        System.out.println("Atari RIOT RAM tests OK, checks=" + checks);
    }

    private static void sizeIs128() {
        RiotRam ram = new RiotRam();
        check(ram.size() == 128, "RIOT RAM size is 128 bytes");
    }

    private static void directReadWrite() {
        RiotRam ram = new RiotRam();
        ram.write(0x00, 0x12);
        ram.write(0x7F, 0x34);
        check(ram.read(0x00) == 0x12, "direct read/write at $00");
        check(ram.read(0x7F) == 0x34, "direct read/write at $7F");
    }

    private static void addressMirroring() {
        RiotRam ram = new RiotRam();
        ram.write(0x80, 0xAB);
        check(ram.read(0x00) == 0xAB, "write $80 mirrors to $00");
        ram.write(0xFFFF, 0xCD);
        check(ram.read(0x7F) == 0xCD, "address is masked to 7 bits");
        ram.write(0x100, 0xEF);
        check(ram.read(0x00) == 0xEF, "address $100 mirrors to $00");
    }

    private static void snapshotIsCopy() {
        RiotRam ram = new RiotRam();
        ram.write(0x10, 0x42);
        byte[] snapshot = ram.snapshot();
        check(snapshot.length == 128, "snapshot length is 128");
        check((snapshot[0x10] & 0xFF) == 0x42, "snapshot contains RAM value");
        snapshot[0x10] = 0x55;
        check(ram.read(0x10) == 0x42, "snapshot does not alias RAM");
    }

    private static void clearZeroesMemory() {
        RiotRam ram = new RiotRam();
        ram.write(0x00, 0x11);
        ram.write(0x7F, 0x22);
        ram.clear();
        check(ram.read(0x00) == 0, "clear zeroes first byte");
        check(ram.read(0x7F) == 0, "clear zeroes last byte");
    }

    private static void saveRestore() {
        RiotRam original = new RiotRam();
        for (int i = 0; i < original.size(); i++) {
            original.write(i, i * 3 + 1);
        }

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);

        RiotRam restored = new RiotRam();
        restored.restore(new BinaryStateReader(writer.toByteArray()));
        check(Arrays.equals(original.snapshot(), restored.snapshot()),
                "RIOT RAM save/restore round-trips");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
