package ru.viise.jemuxg.console.atari2600;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.PanelSwitch;
import ru.viise.jemuxg.api.hardware.SwitchPanel;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.core.input.SimpleController;
import ru.viise.jemuxg.hardware.video.tia.TiaPalette;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Интеграционные тесты машины Atari 2600: CPU 6507, RIOT и TIA на общей шине.
 */
public final class AtariMachineTests {

    private static int checks;

    private AtariMachineTests() {
    }

    public static void main(String[] args) {
        cpuExecutesProgram();
        backgroundColor();
        wsyncStallsCpu();
        riotTimerRuns();
        joystickAndSwitches();
        emptyCartridgeDoesNotCrash();
        System.out.println("Atari machine tests OK, checks=" + checks);
    }

    private static void cpuExecutesProgram() {
        byte[] rom = rom(
                0xA9, 0xAA,        // LDA #$AA
                0x85, 0x80,        // STA $80
                0x4C, 0x04, 0xF0); // JMP $F004
        Atari2600Machine machine = machine(rom);
        List<VideoFrame> frames = connectVideo(machine);

        machine.runFrame();

        check(machine.riot().ram().read(0x80) == 0xAA, "CPU writes RIOT RAM");
        check(frames.size() == 1, "one frame delivered");
        check(frames.get(0).width() == 160 && frames.get(0).height() == 192,
                "frame is 160x192");
    }

    private static void backgroundColor() {
        byte[] rom = rom(
                0xA9, 0x46,        // LDA #$46
                0x85, 0x09,        // STA COLUBK
                0x4C, 0x04, 0xF0); // JMP $F004
        Atari2600Machine machine = machine(rom);
        List<VideoFrame> frames = connectVideo(machine);

        machine.runFrame();

        int[] pixels = frames.get(0).pixels();
        check(pixels[100 * 160 + 80] == TiaPalette.ARGB[0x23],
                "CPU controls TIA background color");
    }

    private static void wsyncStallsCpu() {
        byte[] rom = rom(
                0xA9, 0x02,        // LDA #$02
                0x85, 0x02,        // STA WSYNC
                0xE6, 0x80,        // INC $80
                0xD0, 0x02,        // BNE +2
                0xE6, 0x81,        // INC $81
                0x4C, 0x00, 0xF0); // JMP $F000
        Atari2600Machine machine = machine(rom);

        machine.runFrame();

        int count = machine.riot().ram().read(0x80)
                | (machine.riot().ram().read(0x81) << 8);
        check(count >= 250 && count <= 270, "WSYNC limits loop to about one increment per line");
    }

    private static void riotTimerRuns() {
        byte[] rom = rom(
                0xA9, 0xC8,        // LDA #200
                0x8D, 0x97, 0x02,  // STA TIM1024T ($0297)
                0x4C, 0x05, 0xF0); // JMP $F005
        Atari2600Machine machine = machine(rom);

        machine.runFrame();

        int timer = machine.riot().timer().timer();
        check(timer >= 179 && timer <= 181, "RIOT timer counts CPU cycles during a frame");
    }

    private static void joystickAndSwitches() {
        byte[] rom = rom(
                0xAD, 0x80, 0x02,  // LDA SWCHA
                0x85, 0x80,        // STA $80
                0xAD, 0x82, 0x02,  // LDA SWCHB
                0x85, 0x81,        // STA $81
                0x4C, 0x00, 0xF0); // JMP $F000
        Atari2600Machine machine = machine(rom);
        SimpleController controller = new SimpleController();
        controller.press(Button.LEFT);
        machine.connectController(0, controller);
        EnumSet<PanelSwitch> switches = EnumSet.of(PanelSwitch.SELECT);
        machine.connectPanel(new SwitchPanel() {
            @Override
            public boolean pressed(PanelSwitch button) {
                return switches.contains(button);
            }
        });

        machine.runFrame();

        check(machine.riot().ram().read(0x80) == 0xFB, "joystick LEFT reaches SWCHA");
        check(machine.riot().ram().read(0x81) == 0x35, "console switches reach SWCHB");
    }

    private static void emptyCartridgeDoesNotCrash() {        Atari2600Machine machine = new Atari2600Machine();
        List<VideoFrame> frames = connectVideo(machine);

        machine.runFrame();

        check(frames.size() == 1, "empty cartridge still produces a frame");
    }

    private static Atari2600Machine machine(byte[] rom) {
        Atari2600Machine machine = new Atari2600Machine();
        machine.insert(0, new ByteArrayMedia("test.a26", MediaKind.CARTRIDGE, rom));
        return machine;
    }

    private static List<VideoFrame> connectVideo(Atari2600Machine machine) {
        List<VideoFrame> frames = new ArrayList<>();
        machine.connectVideo(new VideoSink() {
            @Override
            public void frameReady(VideoFrame frame) {
                frames.add(frame);
            }
        });
        return frames;
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

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
