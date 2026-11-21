package ru.viise.jemuxg.console.sg1000;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.sg1000.debug.Sg1000DebugSnapshot;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Тесты полного save/restore машины Sega SG-1000.
 */
public final class Sg1000StateTests {

    private static int checks;

    private Sg1000StateTests() {
    }

    public static void main(String[] args) {
        midFrameRoundTrip();
        cartridgeValidation();
        System.out.println("SG-1000 state tests OK, checks=" + checks);
    }

    private static void midFrameRoundTrip() {
        Sg1000Machine machine = machine(stateRom(), "state.sg");
        FrameCapture video = new FrameCapture();
        CaptureAudio audio = new CaptureAudio();
        machine.connectVideo(video);
        machine.connectAudio(audio);

        int beforeCycles = 5_000;
        machine.runCycles(beforeCycles);
        Sg1000DebugSnapshot before = machine.debugSnapshot();
        int savedLine = machine.frameLine();
        int savedLineCycles = machine.lineCycles();
        check(before.frame() == 0 && before.scanline() == savedLine,
                "mid-frame snapshot reports the active VDP line");
        check(before.dot() == savedLineCycles
                        * Sg1000Constants.VDP_DOTS_PER_LINE
                        / Sg1000Constants.CPU_CYCLES_PER_LINE,
                "VDP dot follows CPU line timing");
        check(before.ram()[0] == (byte) 0xAA, "program wrote system RAM before save");
        check(before.vram()[0] == (byte) 0xA5, "program wrote VRAM before save");
        check(before.vdpRegisters()[7] == 0x04, "program configured VDP backdrop");
        check(before.psgRegisters()[0] == 0x2E, "program configured PSG tone");

        BinaryStateWriter writer = new BinaryStateWriter();
        machine.save(writer);
        byte[] state = writer.toByteArray();
        int remaining = machine.remainingFrameCycles();
        check(remaining == Sg1000Constants.CPU_CYCLES_PER_FRAME - beforeCycles,
                "remaining frame cycles are tracked");

        machine.runCycles(remaining);
        check(video.frames.size() == 1, "partial frame completes exactly once");
        int[] expectedFrame = video.frames.get(0).clone();
        short[] expectedAudio = audio.chunk(0);
        check(video.frames.get(0).length == 256 * 192,
                "completed frame has 256x192 pixels");
        check(expectedAudio.length >= Sg1000Constants.AUDIO_SAMPLES_PER_FRAME
                        && expectedAudio.length <= Sg1000Constants.AUDIO_SAMPLES_PER_FRAME + 1,
                "completed frame flushes about 735 audio samples");

        machine.restore(new BinaryStateReader(state));
        Sg1000DebugSnapshot after = machine.debugSnapshot();
        check(machine.frameLine() == savedLine
                        && machine.lineCycles() == savedLineCycles,
                "machine intra-frame timing restored");
        check(after.frame() == before.frame(), "machine frame counter restored");
        check(after.registers().equals(before.registers()), "Z80 registers restored");
        check(Arrays.equals(after.ram(), before.ram()), "1 KB RAM restored");
        check(Arrays.equals(after.vram(), before.vram()), "16 KB VRAM restored");
        check(Arrays.equals(after.vdpRegisters(), before.vdpRegisters()),
                "VDP registers restored");
        check(after.vdpStatus() == before.vdpStatus()
                        && after.vdpMode() == before.vdpMode()
                        && after.vdpLine() == before.vdpLine()
                        && after.vdpFrame() == before.vdpFrame(),
                "VDP status, mode and counters restored");
        check(Arrays.equals(after.psgRegisters(), before.psgRegisters()),
                "PSG registers restored");
        check(Arrays.equals(after.framebufferIndices(), before.framebufferIndices()),
                "index framebuffer restored");
        check(Arrays.equals(after.framebufferArgb(), before.framebufferArgb()),
                "ARGB framebuffer restored");
        check(after.cartridgeName().equals(before.cartridgeName())
                        && after.cartridgeSize() == before.cartridgeSize(),
                "cartridge identity captured in the snapshot");

        machine.runCycles(remaining);
        check(video.frames.size() == 2, "frame after restore is delivered");
        check(Arrays.equals(video.frames.get(1), expectedFrame),
                "frame after restore matches the original");
        check(Arrays.equals(audio.chunk(1), expectedAudio),
                "audio after restore matches the original");
    }

    private static void cartridgeValidation() {
        byte[] rom = stateRom();
        Sg1000Machine machine = machine(rom, "original.sg");
        machine.runCycles(2_000);

        BinaryStateWriter writer = new BinaryStateWriter();
        machine.save(writer);
        byte[] state = writer.toByteArray();

        machine.eject(0);
        machine.insert(0, new ByteArrayMedia(
                "other.sg", MediaKind.CARTRIDGE, rom));
        expectRestoreFailure(machine, state, "different cartridge name is rejected");

        machine.eject(0);
        machine.insert(0, new ByteArrayMedia(
                "original.sg", MediaKind.CARTRIDGE, new byte[2 * Sg1000Constants.MIN_ROM_SIZE]));
        expectRestoreFailure(machine, state, "different cartridge size is rejected");

        machine.eject(0);
        expectRestoreFailure(machine, state, "missing cartridge is rejected");

        Sg1000Machine empty = new Sg1000Machine();
        BinaryStateWriter emptyWriter = new BinaryStateWriter();
        empty.save(emptyWriter);
        empty.insert(0, new ByteArrayMedia(
                "original.sg", MediaKind.CARTRIDGE, rom));
        expectRestoreFailure(empty, emptyWriter.toByteArray(),
                "state captured without a cartridge is rejected");

        machine.insert(0, new ByteArrayMedia("original.sg", MediaKind.CARTRIDGE, rom));
        machine.restore(new BinaryStateReader(state));
        check(machine.cartridgeSlot().isPresent(),
                "matching cartridge passes validation");
    }

    private static void expectRestoreFailure(
            Sg1000Machine machine, byte[] state, String message) {
        boolean failed = false;
        try {
            machine.restore(new BinaryStateReader(state));
        } catch (IllegalArgumentException | IllegalStateException expected) {
            failed = true;
        }
        check(failed, message);
    }

    private static Sg1000Machine machine(byte[] rom, String name) {
        Sg1000Machine machine = new Sg1000Machine();
        machine.insert(0, new ByteArrayMedia(name, MediaKind.CARTRIDGE, rom));
        return machine;
    }

    /**
     * Настраивает VRAM, VDP backdrop, RAM и PSG и уходит в бесконечный цикл.
     * Все инструкции намеренно простые, чтобы тест проверял машину, а не Z80.
     */
    private static byte[] stateRom() {
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

    private static final class FrameCapture implements VideoSink {
        private final List<int[]> frames = new ArrayList<>();

        @Override
        public void frameReady(VideoFrame frame) {
            frames.add(frame.pixels().clone());
        }
    }

    private static final class CaptureAudio implements AudioSink {
        private final List<short[]> chunks = new ArrayList<>();

        @Override
        public void samplesReady(short[] samples, int count) {
            chunks.add(Arrays.copyOf(samples, count));
        }

        private short[] chunk(int index) {
            return chunks.get(index);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
