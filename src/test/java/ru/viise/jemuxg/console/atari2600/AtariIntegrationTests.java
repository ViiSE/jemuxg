package ru.viise.jemuxg.console.atari2600;

import java.util.ArrayList;
import java.util.List;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.core.input.SimpleController;
import ru.viise.jemuxg.hardware.video.tia.TiaPalette;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Интеграционный тест полного кадра Atari 2600: синтетический ROM,
 * джойстик, видеовыход и звук.
 */
public final class AtariIntegrationTests {

    private static int checks;

    private AtariIntegrationTests() {
    }

    public static void main(String[] args) {
        frameAudioAndJoystick();
        System.out.println("Atari integration tests OK, checks=" + checks);
    }

    private static void frameAudioAndJoystick() {
        byte[] rom = rom(
                0xA9, 0x0E,        // LDA #$0E
                0x85, 0x09,        // STA COLUBK
                0xA9, 0x08,        // LDA #$08
                0x85, 0x15,        // STA AUDC0
                0xA9, 0x0A,        // LDA #$0A
                0x85, 0x17,        // STA AUDF0
                0xA9, 0x0F,        // LDA #$0F
                0x85, 0x19,        // STA AUDV0
                0xAD, 0x80, 0x02,  // LDA SWCHA
                0x85, 0x80,        // STA $80
                0x4C, 0x11, 0xF0); // JMP $F011
        Atari2600Machine machine = machine(rom);
        List<VideoFrame> frames = new ArrayList<>();
        machine.connectVideo(new VideoSink() {
            @Override
            public void frameReady(VideoFrame frame) {
                frames.add(frame);
            }
        });
        CaptureAudio audio = new CaptureAudio();
        machine.connectAudio(audio);
        SimpleController controller = new SimpleController();
        controller.press(Button.RIGHT);
        machine.connectController(0, controller);

        machine.runFrame();

        check(frames.size() == 1, "one frame delivered");
        VideoFrame frame = frames.get(0);
        check(frame.width() == 160 && frame.height() == 192, "frame is 160x192");
        check(frame.pixels()[100 * 160 + 80] == TiaPalette.ARGB[0x07],
                "frame contains program background color");
        check(machine.riot().ram().read(0x80) == 0xF7,
                "joystick RIGHT reaches SWCHA");
        check(audio.count() > 0, "audio samples emitted");
        check(audio.hasNonZero(), "audio is not silent");
    }

    private static Atari2600Machine machine(byte[] rom) {
        Atari2600Machine machine = new Atari2600Machine();
        machine.insert(0, new ByteArrayMedia("integration.a26", MediaKind.CARTRIDGE, rom));
        return machine;
    }

    private static byte[] rom(int... code) {
        byte[] rom = new byte[4096];
        for (int i = 0; i < code.length; i++) {
            rom[i] = (byte) code[i];
        }
        rom[0x0FFC] = 0x00;
        rom[0x0FFD] = (byte) 0xF0;
        return rom;
    }

    private static final class CaptureAudio implements AudioSink {
        private int count;
        private boolean nonZero;

        @Override
        public void samplesReady(short[] samples, int count) {
            this.count += count;
            for (int i = 0; i < count; i++) {
                if (samples[i] != 0) {
                    nonZero = true;
                }
            }
        }

        int count() {
            return count;
        }

        boolean hasNonZero() {
            return nonZero;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
