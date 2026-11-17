package ru.viise.jemuxg.console.atari2600;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleConfig;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.debug.AtariDebugger;
import ru.viise.jemuxg.core.debug.FileDebugSession;
import ru.viise.jemuxg.hardware.video.tia.Tia;
import ru.viise.jemuxg.hardware.video.tia.TiaPalette;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Проверки отладочного снимка Atari 2600.
 */
public final class AtariDebuggerTests {

    private static int checks;

    private AtariDebuggerTests() {
    }

    public static void main(String[] args) throws Exception {
        captureSnapshot();
        superChipRamCaptured();
        consoleProvidesDebugger();
        System.out.println("Atari debugger tests OK, checks=" + checks);
    }

    private static void captureSnapshot() throws Exception {
        byte[] rom = eightKilobytes(debugProgram());
        Atari2600Machine machine = new Atari2600Machine();
        machine.insert(0, new ByteArrayMedia("test.a26", MediaKind.CARTRIDGE, rom));
        machine.runFrame();

        Path root = Files.createTempDirectory("jemuxg-atari-debug");
        FileDebugSession session = new FileDebugSession(root, "test.a26", 42);
        AtariDebugger debugger = new AtariDebugger(machine);
        debugger.capture(session);

        check(debugger.consoleId().equals("atari2600"), "debugger id is atari2600");
        check(session.directory().getFileName().toString().equals("frame-42"),
                "snapshot directory uses frame number");
        check(session.directory().getParent().getFileName().toString().equals("test.a26"),
                "snapshot directory uses ROM name");

        byte[] argb = read(session, "framebuffer.argb");
        byte[] indices = read(session, "framebuffer.idx");
        byte[] riotRam = read(session, "riot-ram.bin");
        byte[] tiaRegisters = read(session, "tia-registers.bin");
        byte[] riotRegisters = read(session, "riot-registers.bin");
        byte[] mapperRam = read(session, "mapper-ram.bin");
        check(argb.length == 160 * 192 * 4, "ARGB buffer has 4 bytes per pixel");
        check(indices.length == 160 * 192, "index buffer has one byte per pixel");
        check(riotRam.length == 128, "RIOT RAM snapshot is 128 bytes");
        check(tiaRegisters.length == 64, "TIA register snapshot is 64 bytes");
        check(riotRegisters.length == 32, "RIOT register snapshot is 32 bytes");
        check(mapperRam.length == 0, "F8 mapper has no RAM");

        int center = 100 * 160 + 80;
        check((indices[center] & 0xFF) == 0x46, "index framebuffer contains COLUBK");
        check(allIndicesInRange(indices), "index framebuffer stays within 0..127");
        int centerArgb = (argb[center * 4] & 0xFF)
                | ((argb[center * 4 + 1] & 0xFF) << 8)
                | ((argb[center * 4 + 2] & 0xFF) << 16)
                | ((argb[center * 4 + 3] & 0xFF) << 24);
        check(centerArgb == TiaPalette.ARGB[0x46], "ARGB buffer contains TIA palette color");

        check((riotRam[0x00] & 0xFF) == 0xAA, "RIOT RAM byte captured");
        check((tiaRegisters[Tia.COLUBK] & 0xFF) == 0x46, "TIA COLUBK register captured");
        check((tiaRegisters[Tia.AUDC0] & 0xFF) == 0x0F, "TIA AUDC0 register captured");
        check((tiaRegisters[Tia.AUDF0] & 0xFF) == 0x05, "TIA AUDF0 register captured");
        check((tiaRegisters[Tia.AUDV0] & 0xFF) == 0x08, "TIA AUDV0 register captured");
        check((riotRegisters[0x02] & 0xFF) == 0x3F, "RIOT SWCHB register captured");
        check((riotRegisters[0x14] & 0xFF) > 0, "RIOT timer captured");

        BufferedImage image = ImageIO.read(session.directory().resolve("screenshot.png").toFile());
        check(image != null && image.getWidth() == 160 && image.getHeight() == 192,
                "screenshot is 160x192");

        String registers = Files.readString(session.directory().resolve("registers.txt"));
        check(registers.contains("PC="), "registers.txt contains CPU registers");
        check(registers.contains("mapper=F8 bank=1/2"), "registers.txt contains mapper bank");
        check(registers.contains("scanline=0 colorClock=0"), "registers.txt contains TIA timing");

        String hashes = Files.readString(session.directory().resolve("hashes.txt"));
        check(hashes.lines().filter(line -> !line.isBlank()).count() == 6,
                "hashes.txt covers all binary artifacts");
        check(hashes.contains("tia-registers.bin") && hashes.contains("mapper-ram.bin"),
                "hashes.txt lists TIA and mapper artifacts");

        String meta = Files.readString(session.directory().resolve("meta.json"));
        check(meta.contains("\"console\": \"atari2600\""), "meta.json has console id");
        check(meta.contains("\"mapper\": \"F8\""), "meta.json has mapper name");
        check(meta.contains("\"width\": 160") && meta.contains("\"height\": 192"),
                "meta.json has frame size");
    }

    private static void superChipRamCaptured() throws Exception {
        byte[] rom = superChipRom();
        Atari2600Machine machine = new Atari2600Machine("f8sc");
        machine.insert(0, new ByteArrayMedia("superchip.a26", MediaKind.CARTRIDGE, rom));
        machine.runFrame();

        Path root = Files.createTempDirectory("jemuxg-atari-debug-sc");
        FileDebugSession session = new FileDebugSession(root, "superchip.a26", 7);
        new AtariDebugger(machine).capture(session);

        byte[] mapperRam = read(session, "mapper-ram.bin");
        check(mapperRam.length == 128, "SuperChip mapper RAM snapshot is 128 bytes");
        check((mapperRam[0] & 0xFF) == 0x5A, "SuperChip RAM value captured");
        String registers = Files.readString(session.directory().resolve("registers.txt"));
        check(registers.contains("mapper=F8SC"), "registers.txt contains F8SC mapper");
        check(registers.contains("superChipRam=128"), "mapper state reports SuperChip RAM");
    }

    private static void consoleProvidesDebugger() {
        Console console = new Atari2600Provider().create(ConsoleConfig.empty());
        check(console.debugger().isPresent(), "Atari console exposes debugger");
        check(console.debugger().orElseThrow().consoleId().equals("atari2600"),
                "console debugger has Atari id");
    }

    private static int[] debugProgram() {
        return new int[] {
                0xA9, 0x46, 0x85, 0x09,       // LDA #$46; STA COLUBK
                0xA9, 0x0F, 0x85, 0x15,       // LDA #$0F; STA AUDC0
                0xA9, 0x05, 0x85, 0x17,       // LDA #$05; STA AUDF0
                0xA9, 0x08, 0x85, 0x19,       // LDA #$08; STA AUDV0
                0xA9, 0xAA, 0x85, 0x80,       // LDA #$AA; STA $80
                0xA9, 0xC8, 0x8D, 0x97, 0x02, // LDA #200; STA TIM1024T
                0xAD, 0xF9, 0x1F,             // LDA $1FF9 (F8 bank 1)
                0x4C, 0x00, 0xF0              // JMP $F000
        };
    }

    private static byte[] eightKilobytes(int[] code) {
        byte[] rom = new byte[8192];
        for (int bank = 0; bank < 2; bank++) {
            int base = bank * 4096;
            for (int i = 0; i < code.length; i++) {
                rom[base + i] = (byte) code[i];
            }
            rom[base + 0x0FFC] = 0x00;
            rom[base + 0x0FFD] = (byte) 0xF0;
        }
        return rom;
    }

    private static byte[] superChipRom() {
        byte[] rom = new byte[8192];
        int[] code = {
                0xA9, 0x5A,       // LDA #$5A
                0x8D, 0x00, 0x10, // STA $1000 (SuperChip RAM)
                0x4C, 0x00, 0x1F  // JMP $1F00
        };
        for (int i = 0; i < code.length; i++) {
            rom[0x0F00 + i] = (byte) code[i];
        }
        rom[0x0FFC] = 0x00;
        rom[0x0FFD] = 0x1F;
        return rom;
    }

    private static byte[] read(FileDebugSession session, String name) throws Exception {
        return Files.readAllBytes(session.directory().resolve(name));
    }

    private static boolean allIndicesInRange(byte[] indices) {
        return Arrays.stream(toInts(indices)).allMatch(value -> value >= 0 && value <= 127);
    }

    private static int[] toInts(byte[] values) {
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] & 0xFF;
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
