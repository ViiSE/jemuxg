package ru.viise.jemuxg.console.sg1000;

import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleConfig;
import ru.viise.jemuxg.api.console.ConsoleProvider;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.app.Main;
import ru.viise.jemuxg.console.sg1000.cartridge.Sg1000CartridgeSlot;
import ru.viise.jemuxg.core.registry.ConsoleRegistry;
import ru.viise.jemuxg.platform.video.SwingVideoSink;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Контрактные проверки каркаса Sega SG-1000.
 */
public final class Sg1000FrameworkTests {

    private static int checks;

    private Sg1000FrameworkTests() {
    }

    public static void main(String[] args) throws Exception {
        constants();
        registryAndConsole();
        emptySlotFrame();
        cartridgeInsertAndEject();
        invalidCartridges();
        frontendExtensions();
        frontendWindow();
        System.out.println("SG-1000 framework tests OK, checks=" + checks);
    }

    private static void constants() {
        check(Sg1000Constants.CPU_CLOCK_HZ == 3_579_545,
                "SG-1000 Z80A runs at 3.579545 MHz");
        check(Sg1000Constants.CPU_CYCLES_PER_FRAME == 59_736,
                "SG-1000 frame is 262 * 228 CPU cycles");
        check(Sg1000Constants.VDP_DOTS_PER_LINE == 342,
                "TMS9918A line is 342 dots");
        check(Sg1000Constants.VISIBLE_WIDTH == 256
                        && Sg1000Constants.VISIBLE_HEIGHT == 192,
                "SG-1000 visible area is 256x192");
        check(Sg1000Constants.RAM_START == 0xC000
                        && Sg1000Constants.RAM_END == 0xC3FF
                        && Sg1000Constants.RAM_SIZE == 1024,
                "SG-1000 RAM is 1 KB at 0xC000-0xC3FF");
        check(Sg1000Constants.ROM_START == 0x0000
                        && Sg1000Constants.ROM_END == 0xBFFF,
                "SG-1000 ROM window is 0x0000-0xBFFF");
        check(Sg1000Constants.VDP_DATA == 0xBE
                        && Sg1000Constants.VDP_CONTROL == 0xBF,
                "VDP ports are 0xBE/0xBF");
        check(Sg1000Constants.PSG_PORT == 0x7F,
                "PSG is written through port 0x7F");
        check(Sg1000Constants.INPUT_P1 == 0xDC
                        && Sg1000Constants.INPUT_P2 == 0xDD,
                "input ports are 0xDC/0xDD");
        check(Sg1000Constants.IRQ_VECTOR == 0x0038,
                "VDP interrupt enters IM 1 vector 0x0038");
        check(Sg1000Constants.AUDIO_SAMPLE_RATE == 44_100
                        && Sg1000Constants.AUDIO_SAMPLES_PER_FRAME == 735,
                "audio placeholder produces 735 samples per frame");
    }

    private static void registryAndConsole() {
        ConsoleRegistry registry = ConsoleRegistry.load();
        check(registry.all().stream()
                        .anyMatch(provider -> "sg1000".equals(provider.info().id())),
                "ConsoleRegistry exposes sg1000");
        ConsoleProvider provider = registry.require("sg1000");
        check(provider.info().supportedMedia().contains(MediaKind.CARTRIDGE),
                "SG-1000 provider accepts cartridges");

        Console console = provider.create(ConsoleConfig.empty());
        check(console.controllerPorts() == 2, "SG-1000 has two controller ports");
        check(console.slots().size() == 1
                        && "cartridge".equals(console.slots().get(0).id()),
                "SG-1000 has one cartridge slot");
        check(console.machine().videoWidth() == 256
                        && console.machine().videoHeight() == 192,
                "SG-1000 machine reports 256x192 video");
        check(console.machine().audioSampleRate() == 44_100,
                "SG-1000 machine reports 44.1 kHz audio");
        check(console.debugger().isPresent(),
                "SG-1000 console exposes debugger");
        check(console.debugger().orElseThrow().consoleId().equals("sg1000"),
                "SG-1000 debugger has sg1000 id");
    }

    private static void emptySlotFrame() {
        FrameCapture video = new FrameCapture();
        AudioCapture audio = new AudioCapture();
        Machine machine = new Sg1000Machine();
        machine.connectVideo(video);
        machine.connectAudio(audio);

        machine.runFrame();
        machine.runFrame();

        check(video.frames.size() == 2, "empty slot emits one frame per runFrame");
        check(audio.samples >= 2L * 735 && audio.samples <= 2L * 736,
                "empty slot emits about 735 silent samples per frame");
        check(!audio.nonZero, "empty slot audio is silent");

        for (VideoFrame frame : video.frames) {
            check(frame.width() == 256 && frame.height() == 192,
                    "empty slot frame is 256x192");
            check(allBlack(frame), "empty slot frame is black");
        }
    }

    private static void cartridgeInsertAndEject() {
        Sg1000Machine machine = new Sg1000Machine();
        byte[] rom = new byte[Sg1000Constants.MIN_ROM_SIZE];
        rom[0] = 0x12;
        rom[1] = 0x34;
        rom[rom.length - 1] = 0x7F;
        Media media = new ByteArrayMedia("test.sg", MediaKind.CARTRIDGE, rom);

        machine.insert(0, media);
        Sg1000CartridgeSlot slot = machine.cartridgeSlot();
        check(slot.isPresent(), "cartridge is reported as inserted");
        check(slot.storage().isPresent(), "inserted cartridge exposes storage");
        check(slot.read(0) == 0x12, "cartridge byte 0 is readable");
        check(slot.read(1) == 0x34, "cartridge byte 1 is readable");
        check(slot.read(rom.length - 1) == 0x7F, "last cartridge byte is readable");
        check(slot.read(rom.length) == 0xFF, "read past ROM returns 0xFF");

        Media ejected = machine.eject(0);
        check(ejected == media, "eject returns the original media");
        check(!slot.isPresent(), "slot is empty after eject");
        check(slot.read(0) == 0xFF, "empty slot reads as 0xFF");
    }

    private static void invalidCartridges() {
        Sg1000Machine machine = new Sg1000Machine();
        expectIllegal(
                () -> machine.insert(0, new ByteArrayMedia(
                        "game.cd", MediaKind.CD, new byte[Sg1000Constants.MIN_ROM_SIZE])),
                "non-cartridge media is rejected");
        expectIllegal(
                () -> machine.insert(0, new ByteArrayMedia(
                        "small.sg", MediaKind.CARTRIDGE, new byte[0x1000])),
                "ROM smaller than 8 KB is rejected");
        expectIllegal(
                () -> machine.insert(0, new ByteArrayMedia(
                        "odd.sg", MediaKind.CARTRIDGE, new byte[0x2001])),
                "ROM not aligned to 8 KB is rejected");
        expectIllegal(
                () -> machine.insert(0, new ByteArrayMedia(
                        "large.sg", MediaKind.CARTRIDGE, new byte[0xC001])),
                "ROM larger than 48 KB is rejected");
        check(!machine.cartridgeSlot().isPresent(),
                "failed inserts leave the slot empty");

        machine.insert(0, new ByteArrayMedia(
                "max.sg", MediaKind.CARTRIDGE, new byte[0xC000]));
        check(machine.cartridgeSlot().isPresent(),
                "48 KB cartridge is accepted");
    }

    private static void frontendExtensions() throws Exception {
        check("sg1000".equals(detectConsole("Flicky.sg")),
                ".sg is detected as SG-1000");
        check("sg1000".equals(detectConsole("game.SC")),
                ".sc is detected as SG-1000 case-insensitively");
        check("nes".equals(detectConsole("game.nes")),
                ".nes detection is unchanged");
        check("atari2600".equals(detectConsole("game.a26")),
                ".a26 detection is unchanged");

        boolean rejected = false;
        try {
            detectConsole("game.bin");
        } catch (InvocationTargetException e) {
            rejected = e.getCause() instanceof IllegalArgumentException;
        }
        check(rejected, ".bin is not guessed and needs --console");
    }

    private static void frontendWindow() {
        SwingVideoSink sink = new SwingVideoSink(
                Sg1000Constants.VISIBLE_WIDTH,
                Sg1000Constants.VISIBLE_HEIGHT);
        Dimension size = sink.getPreferredSize();
        check(size.width == 768 && size.height == 576,
                "SG-1000 window keeps 256x192 proportions at 3x");
        check(!sink.getFocusTraversalKeysEnabled(),
                "SG-1000 arrow keys are delivered instead of moving focus");
    }

    private static String detectConsole(String fileName) throws Exception {
        Method method = Main.class.getDeclaredMethod("consoleFor", Path.class);
        method.setAccessible(true);
        return (String) method.invoke(null, Path.of(fileName));
    }

    private static void expectIllegal(Runnable action, String message) {
        boolean thrown = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        check(thrown, message);
    }

    private static boolean allBlack(VideoFrame frame) {
        for (int pixel : frame.pixels()) {
            if (pixel != 0xFF000000) {
                return false;
            }
        }
        return true;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }

    private static final class FrameCapture implements VideoSink {

        private final List<VideoFrame> frames = new ArrayList<>();

        @Override
        public void frameReady(VideoFrame frame) {
            frames.add(frame);
        }
    }

    private static final class AudioCapture implements AudioSink {

        private long samples;
        private boolean nonZero;

        @Override
        public void samplesReady(short[] samples, int count) {
            this.samples += count;
            for (int i = 0; i < count; i++) {
                if (samples[i] != 0) {
                    nonZero = true;
                }
            }
        }
    }
}
