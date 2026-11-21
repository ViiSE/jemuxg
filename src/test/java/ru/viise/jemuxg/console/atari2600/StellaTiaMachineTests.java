package ru.viise.jemuxg.console.atari2600;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.debug.AtariDebugSnapshot;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.hardware.video.tia.StellaTia;
import ru.viise.jemuxg.hardware.video.tia.StellaTiaVideo;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Проверки штатного Stella-слоя TIA в машине Atari 2600.
 */
public final class StellaTiaMachineTests {

    private static int checks;

    private StellaTiaMachineTests() {
    }

    public static void main(String[] args) {
        stellaLayerVideoAndInput();
        stellaLayerAudio();
        stateRoundTrip();
        System.out.println("Stella TIA machine tests OK, checks=" + checks);
    }

    private static void stellaLayerVideoAndInput() {
        Atari2600Machine machine = new Atari2600Machine();
        StellaTiaVideo stella = machine.stellaTiaChip();
        check(stella.width() == 160 && stella.height() == 192,
                "Stella TIA is 160x192");

        List<VideoFrame> frames = new ArrayList<>();
        machine.connectVideo(frames::add);
        machine.insert(0, new ByteArrayMedia(
                "pitfall2.a26", MediaKind.CARTRIDGE,
                readResource("/test-roms/atari/pitfall2/pitfall2.a26")));
        machine.runFrame();
        machine.runFrame();
        check(frames.size() >= 1, "Stella TIA emits video frames");
        VideoFrame frame = frames.get(frames.size() - 1);
        check(frame.width() == 160 && frame.height() == 192,
                "emitted frame has the TIA dimensions");

        machine.connectController(0, button -> button == Button.A);
        check(machine.tia().registers().read(0x0C) == 0x00,
                "pressed fire is visible through INPT4");
        check(machine.tia().registers().read(0x3C) == 0x00,
                "pressed fire is visible through mirrored INPT4");
        machine.connectController(0, button -> false);
        check(machine.tia().registers().read(0x0C) == 0x80,
                "released fire is visible through INPT4");
        check(machine.tia().registers().read(0x3C) == 0x80,
                "released fire is visible through mirrored INPT4");

        check(machine.tia().registers().read(0x00)
                        == machine.tia().registers().read(0x30),
                "collision registers are mirrored at 0x00 and 0x30");

        StellaTiaVideo paddleTia = new StellaTiaVideo();
        paddleTia.connectPaddle(0, () -> 0);
        paddleTia.registers().write(0x01, 0x80);
        paddleTia.registers().write(0x01, 0x00);
        paddleTia.tick(2000);
        check(paddleTia.registers().read(0x08) == 0x80,
                "paddle at zero position reads high");
        check(paddleTia.registers().read(0x38) == 0x80,
                "paddle input is mirrored at 0x38");

        StellaTiaVideo slowPaddle = new StellaTiaVideo();
        slowPaddle.connectPaddle(1, () -> 1000);
        slowPaddle.registers().write(0x01, 0x80);
        slowPaddle.registers().write(0x01, 0x00);
        slowPaddle.tick(2000);
        check(slowPaddle.registers().read(0x09) == 0x00,
                "paddle at maximum position still reads low");
    }

    private static void stellaLayerAudio() {
        Atari2600Machine machine = new Atari2600Machine();
        CaptureAudio audio = new CaptureAudio();
        machine.connectAudio(audio);
        machine.tia().registers().write(StellaTia.AUDC0, 0x0C);
        machine.tia().registers().write(StellaTia.AUDF0, 0);
        machine.tia().registers().write(StellaTia.AUDV0, 0x0F);
        machine.runCycles(30_000);
        check(audio.anyNonZero(), "Stella TIA routes audio through the machine");
    }

    private static void stateRoundTrip() {
        Atari2600Machine machine = new Atari2600Machine();
        machine.insert(0, new ByteArrayMedia(
                "pitfall2.a26", MediaKind.CARTRIDGE,
                readResource("/test-roms/atari/pitfall2/pitfall2.a26")));
        List<VideoFrame> frames = new ArrayList<>();
        machine.connectVideo(frames::add);
        machine.runCycles(10_000);
        AtariDebugSnapshot before = machine.debugSnapshot();

        BinaryStateWriter writer = new BinaryStateWriter();
        machine.save(writer);
        byte[] state = writer.toByteArray();

        machine.runCycles(10_000);
        machine.restore(new BinaryStateReader(state));
        AtariDebugSnapshot after = machine.debugSnapshot();
        check(after.scanline() == before.scanline()
                && after.colorClock() == before.colorClock()
                && after.wsync() == before.wsync(),
                "Stella TIA timing restored");
        check(Arrays.equals(after.tiaRegisters(), before.tiaRegisters()),
                "Stella TIA registers restored");
        check(Arrays.equals(after.framebufferArgb(), before.framebufferArgb()),
                "Stella TIA framebuffer restored");
        check(Arrays.equals(after.framebufferIndices(), before.framebufferIndices()),
                "Stella TIA palette indices restored");

        machine.runCycles(Atari2600Constants.CPU_CYCLES_PER_FRAME);
        check(!frames.isEmpty(), "frame after Stella TIA restore is delivered");
    }

    private static byte[] readResource(String name) {
        try (InputStream stream =
                     StellaTiaMachineTests.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalStateException("missing resource: " + name);
            }
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }

    private static final class CaptureAudio implements AudioSink {

        private boolean nonZero;

        @Override
        public void samplesReady(short[] samples, int count) {
            for (int i = 0; i < count; i++) {
                if (samples[i] != 0) {
                    nonZero = true;
                }
            }
        }

        boolean anyNonZero() {
            return nonZero;
        }
    }
}
