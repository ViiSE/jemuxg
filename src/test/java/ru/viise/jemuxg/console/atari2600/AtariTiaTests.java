package ru.viise.jemuxg.console.atari2600;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.hardware.video.tia.Tia;
import ru.viise.jemuxg.hardware.video.tia.TiaPalette;

/**
 * Тесты каркаса развёртки TIA.
 */
public final class AtariTiaTests {

    private static int checks;

    public static void main(String[] args) {
        initialState();
        paletteTable();
        indexFramebuffer();
        playfieldPatternAndBackground();
        playfieldReflect();
        playfieldScoreMode();
        playfieldVblank();
        playfieldControlBits();
        playfieldSaveRestore();
        lineBoundary();
        frameDelivery();
        wsyncStopsCpu();
        syncRegisters();
        resetState();
        saveRestore();
        machineRunFrame();
        negativeTickRejected();
        System.out.println("Atari TIA tests OK, checks=" + checks);
    }

    private static void initialState() {
        Tia tia = new Tia();
        check(tia.width() == Atari2600Constants.VISIBLE_WIDTH, "TIA width");
        check(tia.height() == Atari2600Constants.VISIBLE_HEIGHT, "TIA height");
        check(tia.scanline() == 0, "TIA starts at scanline 0");
        check(tia.colorClock() == 0, "TIA starts at color clock 0");
        check(!tia.cpuHalted(), "TIA starts not halted");
        check(tia.frameCount() == 0, "TIA starts at frame 0");
        check("TIA".equals(tia.name()), "TIA name");
    }

    private static void paletteTable() {
        check(TiaPalette.size() == 128, "TIA palette has 128 colors");
        check(TiaPalette.ARGB[0x00] == 0xFF000000, "TIA palette 0x00 black");
        check(TiaPalette.ARGB[0x07] == 0xFFECECEC, "TIA palette 0x07 white");
        check(TiaPalette.ARGB[0x08] == 0xFF484800, "TIA palette 0x08 yellow");
        check(TiaPalette.ARGB[0x0F] == 0xFFFCFC54, "TIA palette 0x0F bright yellow");
        check(TiaPalette.ARGB[0x20] == 0xFF940000, "TIA palette 0x20 red");
        check(TiaPalette.ARGB[0x27] == 0xFFFC9090, "TIA palette 0x27 bright red");
        check(TiaPalette.ARGB[0x40] == 0xFF000094, "TIA palette 0x40 blue");
        check(TiaPalette.ARGB[0x7F] == 0xFFFCE070, "TIA palette 0x7F light brown");

        boolean alpha = true;
        for (int color : TiaPalette.ARGB) {
            alpha &= (color >>> 24) == 0xFF;
        }
        check(alpha, "TIA palette colors are opaque");
    }

    private static void indexFramebuffer() {
        Tia tia = new Tia();
        byte[] indices = tia.framebufferIndices();
        check(indices.length == Atari2600Constants.VISIBLE_WIDTH
                * Atari2600Constants.VISIBLE_HEIGHT, "TIA index framebuffer size");

        boolean zero = true;
        for (byte index : indices) {
            zero &= index == 0;
        }
        check(zero, "TIA index framebuffer starts at color 0");
        check(tia.framebufferArgb()[0] == TiaPalette.ARGB[0],
                "TIA framebuffer starts with palette color 0");

        indices[0] = 127;
        check(tia.framebufferIndices()[0] == 0,
                "TIA index framebuffer returns a copy");
    }

    private static void playfieldPatternAndBackground() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.COLUBK, 0x08);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.PF0, 0x10);
        registers.write(Tia.PF1, 0x80);
        registers.write(Tia.PF2, 0x01);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        int[] pixels = tia.framebufferArgb();
        int pf = TiaPalette.ARGB[0x0F];
        int bk = TiaPalette.ARGB[0x08];

        check(pixels[0] == pf, "playfield PF0 bit renders");
        check(pixels[3] == pf, "playfield PF0 spans four pixels");
        check(pixels[4] == bk, "playfield PF0 ends after four pixels");
        check(pixels[16] == pf, "playfield PF1 bit renders");
        check(pixels[20] == bk, "playfield PF1 ends after four pixels");
        check(pixels[48] == pf, "playfield PF2 bit renders");
        check(pixels[52] == bk, "playfield PF2 ends after four pixels");
        check(pixels[80] == pf, "playfield repeats PF0 on right half");
        check(pixels[96] == pf, "playfield repeats PF1 on right half");
        check(pixels[128] == pf, "playfield repeats PF2 on right half");
        byte[] indices = tia.framebufferIndices();
        check((indices[0] & 0xFF) == 0x0F, "playfield index framebuffer PF color");
        check((indices[4] & 0xFF) == 0x08, "playfield index framebuffer background color");
    }

    private static void playfieldReflect() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.CTRLPF, 0x01);
        registers.write(Tia.COLUPF, 0x20);
        registers.write(Tia.PF2, 0x80);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        int[] pixels = tia.framebufferArgb();
        int pf = TiaPalette.ARGB[0x20];

        check(pixels[80] == pf, "reflected playfield starts with PF2 bit 7");
        check(pixels[83] == pf, "reflected playfield spans four pixels");
        check(pixels[84] == TiaPalette.ARGB[0], "reflected playfield ends after four pixels");
        check(tia.controlPf() == 0x01, "CTRLPF reflect bit stored");
    }

    private static void playfieldScoreMode() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.CTRLPF, 0x02);
        registers.write(Tia.COLUP0, 0x20);
        registers.write(Tia.COLUP1, 0x40);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.COLUBK, 0x08);
        registers.write(Tia.PF0, 0x10);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        int[] pixels = tia.framebufferArgb();

        check(pixels[0] == TiaPalette.ARGB[0x20], "score mode uses COLUP0 on left");
        check(pixels[80] == TiaPalette.ARGB[0x40], "score mode uses COLUP1 on right");
        check(pixels[4] == TiaPalette.ARGB[0x08], "score mode keeps background color");
    }

    private static void playfieldVblank() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.VBLANK, 0x02);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.PF0, 0x10);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        check(tia.framebufferArgb()[0] == 0xFF000000, "VBLANK hides playfield");
    }

    private static void playfieldControlBits() {
        Tia tia = new Tia();
        tia.registers().write(Tia.CTRLPF, 0x34);
        check(tia.controlPf() == 0x34, "CTRLPF priority and ball size bits stored");
        tia.registers().write(Tia.CTRLPF, 0x00);
        check(tia.controlPf() == 0x00, "CTRLPF bits cleared");
    }

    private static void playfieldSaveRestore() {
        Tia original = new Tia();
        Bus registers = original.registers();
        registers.write(Tia.COLUBK, 0x08);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.PF0, 0x10);
        original.tick(500);

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);
        Tia restored = new Tia();
        restored.restore(new BinaryStateReader(writer.toByteArray()));

        long remaining = Atari2600Constants.COLOR_CLOCKS_PER_FRAME - 500;
        original.tick(remaining);
        restored.tick(remaining);
        check(Arrays.equals(original.framebufferArgb(), restored.framebufferArgb()),
                "restored playfield renders identically");
        check(restored.framebufferArgb()[0] == TiaPalette.ARGB[0x0F],
                "restored playfield uses restored color");
    }

    private static void lineBoundary() {
        Tia tia = new Tia();
        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE - 1);
        check(tia.scanline() == 0, "line boundary: last clock stays on line 0");
        check(tia.colorClock() == 227, "line boundary: color clock 227");
        tia.tick(1);
        check(tia.scanline() == 1, "line boundary: next tick starts line 1");
        check(tia.colorClock() == 0, "line boundary: color clock resets");
    }

    private static void frameDelivery() {
        Tia tia = new Tia();
        List<VideoFrame> frames = new ArrayList<>();
        tia.connect(frames::add);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME - 1);
        check(frames.isEmpty(), "no frame before full frame");
        check(tia.frameCount() == 0, "frame count before full frame");
        check(tia.scanline() == 261, "last scanline before frame end");
        check(tia.colorClock() == 227, "last color clock before frame end");

        tia.tick(1);
        check(frames.size() == 1, "exactly one frame per full frame");
        check(tia.frameCount() == 1, "frame count after full frame");
        check(tia.scanline() == 0, "frame ends at scanline 0");
        check(tia.colorClock() == 0, "frame ends at color clock 0");

        VideoFrame frame = frames.get(0);
        check(frame.width() == 160 && frame.height() == 192, "frame is 160x192");
        check(frame.pixels().length == 160 * 192, "frame pixel count");
        check(frame.pixels()[0] == 0xFF000000, "empty TIA frame is black");

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        check(frames.size() == 2, "second full frame delivered");
        check(tia.frameCount() == 2, "second frame count");
    }

    private static void wsyncStopsCpu() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.WSYNC, 0);
        check(tia.cpuHalted(), "WSYNC write halts CPU");

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE - 1);
        check(tia.scanline() == 0, "WSYNC keeps current scanline");
        check(tia.colorClock() == 227, "WSYNC keeps last color clock");
        check(tia.cpuHalted(), "CPU still halted before line end");

        tia.tick(1);
        check(tia.scanline() == 1, "WSYNC releases at next line");
        check(tia.colorClock() == 0, "WSYNC releases at color clock 0");
        check(!tia.cpuHalted(), "CPU released after line end");
    }

    private static void syncRegisters() {
        Tia tia = new Tia();
        Bus registers = tia.registers();

        registers.write(Tia.VSYNC, 0x02);
        check(tia.vsync(), "VSYNC bit set");
        check(registers.read(Tia.VSYNC) == 0x02, "VSYNC readable");
        registers.write(Tia.VSYNC, 0);
        check(!tia.vsync(), "VSYNC bit cleared");

        registers.write(Tia.VBLANK, 0x02);
        check(tia.vblank(), "VBLANK bit set");
        check(registers.read(Tia.VBLANK) == 0x02, "VBLANK readable");
        registers.write(Tia.VBLANK, 0);
        check(!tia.vblank(), "VBLANK bit cleared");
    }

    private static void resetState() {
        Tia tia = new Tia();
        tia.tick(500);
        Bus registers = tia.registers();
        registers.write(Tia.VSYNC, 0x02);
        registers.write(Tia.WSYNC, 0);
        tia.reset();
        check(tia.scanline() == 0, "reset scanline");
        check(tia.colorClock() == 0, "reset color clock");
        check(!tia.vsync(), "reset VSYNC");
        check(!tia.cpuHalted(), "reset WSYNC");
        check(tia.frameCount() == 0, "reset frame count");
    }

    private static void saveRestore() {
        Tia original = new Tia();
        original.tick(500);
        Bus registers = original.registers();
        registers.write(Tia.VSYNC, 0x02);
        registers.write(Tia.VBLANK, 0x02);
        registers.write(Tia.WSYNC, 0);

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);

        Tia restored = new Tia();
        restored.restore(new BinaryStateReader(writer.toByteArray()));
        check(restored.scanline() == original.scanline(), "restore scanline");
        check(restored.colorClock() == original.colorClock(), "restore color clock");
        check(restored.vsync(), "restore VSYNC");
        check(restored.vblank(), "restore VBLANK");
        check(restored.cpuHalted(), "restore WSYNC");
        check(restored.frameCount() == original.frameCount(), "restore frame count");

        restored.tick(Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE - restored.colorClock());
        check(restored.scanline() == original.scanline() + 1, "restored TIA continues");
        check(!restored.cpuHalted(), "restored WSYNC releases");
    }

    private static void machineRunFrame() {
        Atari2600Machine machine = new Atari2600Machine();
        List<VideoFrame> frames = new ArrayList<>();
        machine.connectVideo(frames::add);

        machine.runFrame();
        check(frames.size() == 1, "machine runFrame delivers one frame");
        check(frames.get(0).width() == 160 && frames.get(0).height() == 192,
                "machine frame dimensions");
        machine.runFrame();
        check(frames.size() == 2, "machine second runFrame delivers one frame");
    }

    private static void negativeTickRejected() {
        try {
            new Tia().tick(-1);
            check(false, "negative TIA tick rejected");
        } catch (IllegalArgumentException e) {
            check(true, "negative TIA tick rejected");
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }

    private AtariTiaTests() {
    }
}
