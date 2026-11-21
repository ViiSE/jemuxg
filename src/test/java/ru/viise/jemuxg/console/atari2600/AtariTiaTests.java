package ru.viise.jemuxg.console.atari2600;

import java.util.ArrayList;
import java.util.List;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.hardware.video.tia.StellaTia;
import ru.viise.jemuxg.hardware.video.tia.StellaTiaVideo;
import ru.viise.jemuxg.hardware.video.tia.TiaPalette;

/**
 * Контрактные проверки TIA Atari 2600 на Stella-совместимом слое.
 */
public final class AtariTiaTests {

    private static int checks;

    private AtariTiaTests() {
    }

    public static void main(String[] args) {
        paletteTable();
        initialAndResetState();
        registerMirrorsAndInputs();
        wsyncStopsCpu();
        frameDelivery();
        negativeTickRejected();
        machineUsesStellaLayer();
        System.out.println("Atari TIA tests OK, checks=" + checks);
    }

    private static void paletteTable() {
        check(TiaPalette.size() == 128, "palette has 128 colors");
        check(TiaPalette.argb(0) == 0xFF000000, "palette index 0 is black");
        check((TiaPalette.argb(0x0F) >>> 24) == 0xFF, "palette colors are opaque");
        check(TiaPalette.argb(0x80) == TiaPalette.argb(0x00),
                "palette index wraps at 128");
    }

    private static void initialAndResetState() {
        StellaTia tia = new StellaTia();
        check(tia.colorClock() == 0 && tia.hctr() == 0 && tia.scanline() == 0,
                "TIA starts at the first clock");
        check(tia.hblank(), "TIA starts in HBLANK");
        tia.tick(228 * 10);
        tia.reset();
        check(tia.colorClock() == 0 && tia.hctr() == 0 && tia.scanline() == 0,
                "reset restores the first clock");
    }

    private static void registerMirrorsAndInputs() {
        StellaTia tia = new StellaTia();
        for (int i = 0; i < 8; i++) {
            check(tia.registers().read(0x30 + i) == tia.registers().read(i),
                    "collision register " + i + " is mirrored");
        }
        for (int i = 0x08; i <= 0x0D; i++) {
            check(tia.registers().read(i) == 0x80,
                    "input register " + Integer.toHexString(i) + " is open");
        }
    }

    private static void wsyncStopsCpu() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.WSYNC, 0);
        check(tia.cpuHalted(), "WSYNC stops the CPU");
        tia.tick(StellaTia.H_CLOCKS);
        check(!tia.cpuHalted(), "CPU resumes at the next line");
    }

    private static void frameDelivery() {
        StellaTiaVideo tia = new StellaTiaVideo();
        List<VideoFrame> frames = new ArrayList<>();
        tia.connect(frames::add);
        tia.registers().write(StellaTia.COLUBK, 0x1E);
        tia.tick(StellaTia.H_CLOCKS * StellaTia.SCANLINES);
        check(frames.size() == 1, "one frame is emitted per TIA frame");
        VideoFrame frame = frames.get(0);
        check(frame.width() == 160 && frame.height() == 192,
                "frame has TIA dimensions");
        check(tia.framebufferIndices().length == 160 * 192,
                "indexed framebuffer has 160x192 pixels");
        check(tia.framebufferArgb().length == 160 * 192,
                "ARGB framebuffer has 160x192 pixels");
    }

    private static void negativeTickRejected() {
        StellaTia tia = new StellaTia();
        boolean rejected = false;
        try {
            tia.tick(-1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "negative tick is rejected");
    }

    private static void machineUsesStellaLayer() {
        Atari2600Machine machine = new Atari2600Machine();
        check(machine.tia() instanceof StellaTiaVideo,
                "machine uses the Stella TIA layer by default");
        machine.runFrame();
        check(machine.debugSnapshot().tiaRegisters().length == 64,
                "debug snapshot exposes 64 TIA registers");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
