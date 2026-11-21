package ru.viise.jemuxg.console.sg1000;

import java.util.HashSet;
import java.util.Set;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.hardware.video.tms9918.Tms9918;
import ru.viise.jemuxg.hardware.video.tms9918.Tms9918Palette;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Интеграционный тест SG-1000 на синтетическом ROM.
 *
 * <p>ROM настраивает VDP, заливает VRAM, выводит спрайт, включает
 * прерывание по VBlank, запускает тон на SN76489 и крутится в цикле.</p>
 */
public final class Sg1000IntegrationTests {

    private static int checks;

    private Sg1000IntegrationTests() {
    }

    public static void main(String[] args) {
        syntheticRomFrame();
        System.out.println("SG-1000 integration tests OK, checks=" + checks);
    }

    private static void syntheticRomFrame() {
        Sg1000Machine machine = new Sg1000Machine();
        FrameCapture video = new FrameCapture();
        CaptureAudio audio = new CaptureAudio();
        machine.connectVideo(video);
        machine.connectAudio(audio);
        machine.insert(0, new ByteArrayMedia(
                "integration.sg", MediaKind.CARTRIDGE, integrationRom()));

        machine.runFrame();

        check(video.frames == 1, "synthetic ROM emits exactly one frame");
        VideoFrame frame = video.last;
        check(frame != null && frame.width() == Tms9918.WIDTH
                        && frame.height() == Tms9918.HEIGHT,
                "synthetic frame has TMS9918 dimensions");
        check(frame != null && frame.pixels().length == Tms9918.WIDTH * Tms9918.HEIGHT,
                "synthetic frame contains a full ARGB buffer");
        check(frame != null && uniqueColors(frame) >= 2,
                "synthetic frame contains background and sprite colors");
        check(frame != null && frame.pixels()[10 * Tms9918.WIDTH]
                        == Tms9918Palette.argb(4),
                "synthetic ROM fills the background from the color table");
        check(frame != null
                        && frame.pixels()[41 * Tms9918.WIDTH + 50]
                        == Tms9918Palette.argb(15),
                "synthetic ROM renders a sprite at Y+1 and X");
        check(machine.vdp().mode() == Tms9918.Mode.GRAPHICS_I
                        && machine.vdp().displayEnabled(),
                "synthetic ROM selects Graphics I and enables display");
        check(machine.bus().read(0xC000) > 0,
                "synthetic ROM services the VDP interrupt handler");
        check(audio.samples >= Sg1000Constants.AUDIO_SAMPLES_PER_FRAME,
                "synthetic ROM flushes a full frame of audio");
        check(audio.nonZero > 0, "synthetic ROM produces audible SN76489 samples");
    }

    /**
     * 32 KB ROM: jump to 0x0100, IM 1 handler at 0x0038, setup at 0x0100.
     */
    private static byte[] integrationRom() {
        byte[] rom = new byte[0x8000];
        rom[0] = (byte) 0xC3;
        rom[1] = 0x00;
        rom[2] = 0x01;

        // IM 1 handler: read VDP status (clears IRQ), increment RAM[$C000],
        // return from interrupt.
        int[] handler = {
            0xDB, 0xBF,             // IN A,($BF)
            0x3A, 0x00, 0xC0,       // LD A,($C000)
            0x3C,                   // INC A
            0x32, 0x00, 0xC0,       // LD ($C000),A
            0xED, 0x4D              // RETI
        };
        for (int i = 0; i < handler.length; i++) {
            rom[0x38 + i] = (byte) handler[i];
        }

        int pc = 0x0100;
        // Register 0: Graphics I.
        pc = register(rom, pc, 0, 0x00);
        // Name table 0x3800, color table 0x2000, pattern table 0x0000,
        // sprite attributes 0x1F80, sprite patterns 0x3000.
        pc = register(rom, pc, 2, 0x0E);
        pc = register(rom, pc, 3, 0x80);
        pc = register(rom, pc, 4, 0x00);
        pc = register(rom, pc, 5, 0x3F);
        pc = register(rom, pc, 6, 0x06);
        pc = register(rom, pc, 7, 0x01);

        // Pattern 0: solid tile.
        pc = writeAddress(rom, pc, 0x0000);
        for (int i = 0; i < 8; i++) {
            pc = emit(rom, pc, 0x3E, 0xFF, 0xD3, 0xBE);
        }

        // Color table for tile 0: foreground 4, background 15.
        pc = writeAddress(rom, pc, 0x2000);
        pc = emit(rom, pc, 0x3E, 0x4F, 0xD3, 0xBE);

        // Sprite pattern 0: solid 8x8 block.
        pc = writeAddress(rom, pc, 0x3000);
        for (int i = 0; i < 8; i++) {
            pc = emit(rom, pc, 0x3E, 0xFF, 0xD3, 0xBE);
        }

        // Sprite 0: Y=40, X=50, pattern 0, color 15; terminator.
        pc = writeAddress(rom, pc, 0x1F80);
        pc = emit(rom, pc, 0x3E, 40, 0xD3, 0xBE);
        pc = emit(rom, pc, 0x3E, 50, 0xD3, 0xBE);
        pc = emit(rom, pc, 0x3E, 0, 0xD3, 0xBE);
        pc = emit(rom, pc, 0x3E, 0x0F, 0xD3, 0xBE);
        pc = emit(rom, pc, 0x3E, 0xD0, 0xD3, 0xBE);

        // Display on, VBlank IRQ enabled, 16K mode.
        pc = register(rom, pc, 1, 0xE0);

        // SN76489: tone channel 0 period 16, maximum volume.
        pc = emit(rom, pc, 0x3E, 0x80, 0xD3, 0x7F);
        pc = emit(rom, pc, 0x3E, 0x01, 0xD3, 0x7F);
        pc = emit(rom, pc, 0x3E, 0x90, 0xD3, 0x7F);

        // IM 1, EI, JR $.
        pc = emit(rom, pc, 0xED, 0x56);
        pc = emit(rom, pc, 0xFB);
        pc = emit(rom, pc, 0x18, 0xFE);
        return rom;
    }

    private static int register(byte[] rom, int pc, int index, int value) {
        pc = emit(rom, pc, 0x3E, value, 0xD3, 0xBF);
        return emit(rom, pc, 0x3E, 0x80 | index, 0xD3, 0xBF);
    }

    private static int writeAddress(byte[] rom, int pc, int address) {
        pc = emit(rom, pc, 0x3E, address & 0xFF, 0xD3, 0xBF);
        return emit(rom, pc, 0x3E, 0x40 | ((address >> 8) & 0x3F), 0xD3, 0xBF);
    }

    private static int emit(byte[] rom, int pc, int... bytes) {
        for (int value : bytes) {
            rom[pc++] = (byte) value;
        }
        return pc;
    }

    private static int uniqueColors(VideoFrame frame) {
        Set<Integer> colors = new HashSet<>();
        for (int pixel : frame.pixels()) {
            colors.add(pixel);
        }
        return colors.size();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }

    private static final class FrameCapture implements VideoSink {

        private VideoFrame last;
        private int frames;

        @Override
        public void frameReady(VideoFrame frame) {
            last = frame;
            frames++;
        }
    }

    private static final class CaptureAudio implements AudioSink {

        private long samples;
        private long nonZero;

        @Override
        public void samplesReady(short[] data, int count) {
            samples += count;
            for (int i = 0; i < count; i++) {
                if (data[i] != 0) {
                    nonZero++;
                }
            }
        }
    }
}
