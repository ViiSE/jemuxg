package ru.viise.jemuxg.console.sg1000;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleConfig;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.sg1000.debug.Sg1000DebugSnapshot;
import ru.viise.jemuxg.console.sg1000.debug.Sg1000Debugger;
import ru.viise.jemuxg.core.debug.FileDebugSession;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Проверки отладочного снимка Sega SG-1000.
 */
public final class Sg1000DebuggerTests {

    private static int checks;

    private Sg1000DebuggerTests() {
    }

    public static void main(String[] args) throws Exception {
        captureSnapshot();
        consoleProvidesDebugger();
        System.out.println("SG-1000 debugger tests OK, checks=" + checks);
    }

    private static void captureSnapshot() throws Exception {
        byte[] rom = debugRom();
        Sg1000Machine machine = new Sg1000Machine();
        machine.insert(0, new ByteArrayMedia("test.sg", MediaKind.CARTRIDGE, rom));
        machine.runFrame();

        Sg1000DebugSnapshot snapshot = machine.debugSnapshot();
        check(snapshot.frame() == 1, "snapshot reports the completed machine frame");
        check(snapshot.scanline() == machine.vdp().line(),
                "snapshot scanline matches VDP");
        check(snapshot.scanline() >= 0
                        && snapshot.scanline() < Sg1000Constants.SCREEN_LINES,
                "snapshot scanline is inside the NTSC frame");
        check(snapshot.dot() >= 0
                        && snapshot.dot() < Sg1000Constants.VDP_DOTS_PER_LINE,
                "snapshot dot is inside the VDP line");
        check(snapshot.width() == 256 && snapshot.height() == 192,
                "snapshot frame is 256x192");
        check(snapshot.registers().pc() != 0,
                "Z80 program counter is captured");
        check(snapshot.ram().length == 1024
                        && snapshot.ram()[0] == (byte) 0xAA,
                "snapshot captures 1 KB RAM");
        check(snapshot.vram().length == 16 * 1024
                        && snapshot.vram()[0] == (byte) 0xA5,
                "snapshot captures 16 KB VRAM");
        check(snapshot.vdpRegisters().length == 8
                        && snapshot.vdpRegisters()[7] == 0x04,
                "snapshot captures VDP registers");
        check(snapshot.psgRegisters().length == 8
                        && snapshot.psgRegisters()[0] == 0x2E,
                "snapshot captures PSG registers");
        check(snapshot.cartridgeName().equals("test.sg")
                        && snapshot.cartridgeSize() == Sg1000Constants.MIN_ROM_SIZE,
                "snapshot captures cartridge identity");

        Path root = Files.createTempDirectory("jemuxg-sg1000-debug");
        FileDebugSession session = new FileDebugSession(root, "test.sg", 42);
        Sg1000Debugger debugger = new Sg1000Debugger(machine);
        debugger.capture(session);

        check(debugger.consoleId().equals("sg1000"), "debugger id is sg1000");
        check(session.directory().getFileName().toString().equals("frame-42"),
                "snapshot directory uses frame number");
        check(session.directory().getParent().getFileName().toString().equals("test.sg"),
                "snapshot directory uses ROM name");

        byte[] argb = read(session, "framebuffer.argb");
        byte[] indices = read(session, "framebuffer.idx");
        byte[] ram = read(session, "ram.bin");
        byte[] vram = read(session, "vram.bin");
        check(argb.length == 256 * 192 * 4, "ARGB buffer has 4 bytes per pixel");
        check(indices.length == 256 * 192, "index buffer has one byte per pixel");
        check(ram.length == 1024, "RAM snapshot is 1024 bytes");
        check(vram.length == 16 * 1024, "VRAM snapshot is 16 KB");
        check((ram[0] & 0xFF) == 0xAA, "RAM value captured");
        check((vram[0] & 0xFF) == 0xA5, "VRAM value captured");
        check(allIndicesInRange(indices), "index frame stays within palette 0..15");

        Path screenshot = session.directory().resolve("screenshot.png");
        check(Files.size(screenshot) > 0, "screenshot.png is not empty");
        BufferedImage image = ImageIO.read(screenshot.toFile());
        check(image != null && image.getWidth() == 256 && image.getHeight() == 192,
                "screenshot is 256x192");
        check(image.getRGB(10, 10) != 0xFF000000,
                "screenshot contains the configured backdrop color");

        String registers = Files.readString(session.directory().resolve("registers.txt"));
        check(registers.contains("PC="), "registers.txt contains Z80 registers");
        check(registers.contains("VDP line=") && registers.contains("status="),
                "registers.txt contains VDP status and line");
        check(registers.contains("VDP R0=") && registers.contains("R7=04"),
                "registers.txt contains VDP registers");
        check(registers.contains("PSG R0=2E"), "registers.txt contains PSG registers");
        check(registers.contains("scanline=") && registers.contains("dot="),
                "registers.txt contains machine timing");

        String hashes = Files.readString(session.directory().resolve("hashes.txt"));
        check(hashes.lines().filter(line -> !line.isBlank()).count() == 4,
                "hashes.txt covers all binary artifacts");
        check(hashes.contains("framebuffer.argb")
                        && hashes.contains("framebuffer.idx")
                        && hashes.contains("ram.bin")
                        && hashes.contains("vram.bin"),
                "hashes.txt lists frame, RAM and VRAM artifacts");

        String meta = Files.readString(session.directory().resolve("meta.json"));
        check(meta.contains("\"console\": \"sg1000\""), "meta.json has console id");
        check(meta.contains("\"rom\": \"test.sg\""), "meta.json has ROM name");
        check(meta.contains("\"width\": 256") && meta.contains("\"height\": 192"),
                "meta.json has frame size");
        check(meta.contains("\"vdpMode\": \"GRAPHICS_I\""),
                "meta.json has VDP mode");
        check(meta.contains("\"cartridgeSize\": 8192"),
                "meta.json has cartridge size");
    }

    private static void consoleProvidesDebugger() {
        Console console = new Sg1000Provider().create(ConsoleConfig.empty());
        check(console.debugger().isPresent(), "SG-1000 console exposes debugger");
        check(console.debugger().orElseThrow().consoleId().equals("sg1000"),
                "console debugger has SG-1000 id");
    }

    private static byte[] debugRom() {
        int[] code = {
                0x31, 0xF0, 0xDF,       // LD SP,$DFF0
                0x3E, 0xAA,             // LD A,$AA
                0x32, 0x00, 0xC0,       // LD ($C000),A
                0x3E, 0x00,             // LD A,$00
                0xD3, 0xBF,             // OUT ($BF),A: latch address low
                0x3E, 0x40,             // LD A,$40
                0xD3, 0xBF,             // OUT ($BF),A: VRAM address 0, write mode
                0x3E, 0xA5,             // LD A,$A5
                0xD3, 0xBE,             // OUT ($BE),A: VRAM[0]=$A5
                0x3E, 0x04,             // LD A,$04
                0xD3, 0xBF,             // OUT ($BF),A: latch value for R7
                0x3E, 0x87,             // LD A,$87
                0xD3, 0xBF,             // OUT ($BF),A: R7=4 (backdrop)
                0x3E, 0x8E,             // LD A,$8E
                0xD3, 0x7F,             // OUT ($7F),A: PSG channel 0 low nibble
                0x3E, 0x02,             // LD A,$02
                0xD3, 0x7F,             // OUT ($7F),A: PSG channel 0 high bits
                0x18, 0xFE              // JR $
        };
        byte[] rom = new byte[Sg1000Constants.MIN_ROM_SIZE];
        for (int i = 0; i < code.length; i++) {
            rom[i] = (byte) code[i];
        }
        return rom;
    }

    private static byte[] read(FileDebugSession session, String name) throws Exception {
        return Files.readAllBytes(session.directory().resolve(name));
    }

    private static boolean allIndicesInRange(byte[] indices) {
        return Arrays.stream(toInts(indices)).allMatch(value -> value >= 0 && value <= 15);
    }

    private static int[] toInts(byte[] values) {
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] & 0xFF;
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
