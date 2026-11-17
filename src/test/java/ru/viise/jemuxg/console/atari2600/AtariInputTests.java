package ru.viise.jemuxg.console.atari2600;

import java.awt.event.KeyEvent;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.PanelSwitch;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.core.input.SimpleController;
import ru.viise.jemuxg.platform.input.SwingKeyboardController;
import ru.viise.jemuxg.platform.input.SwingKeyboardPanel;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Клавиатурный ввод Atari 2600: джойстик и консольные переключатели.
 */
public final class AtariInputTests {

    private static int checks;

    private AtariInputTests() {
    }

    public static void main(String[] args) {
        joystickKeys();
        consolePanelKeys();
        machineSwitches();
        System.out.println("Atari input tests OK, checks=" + checks);
    }

    private static void joystickKeys() {
        SimpleController controller = new SimpleController();
        SwingKeyboardController keyboard = new SwingKeyboardController(controller);

        check(keyboard.handleKeyPressed(KeyEvent.VK_LEFT), "LEFT is a joystick key");
        check(controller.pressed(Button.LEFT), "LEFT reaches the controller");
        check(keyboard.handleKeyReleased(KeyEvent.VK_LEFT), "LEFT release is handled");
        check(!controller.pressed(Button.LEFT), "LEFT release reaches the controller");

        check(keyboard.handleKeyPressed(KeyEvent.VK_A), "A is a fire key");
        check(controller.pressed(Button.A), "A presses fire");
        check(keyboard.handleKeyReleased(KeyEvent.VK_A), "A release is handled");

        check(keyboard.handleKeyPressed(KeyEvent.VK_SPACE), "Space is a fire key");
        check(controller.pressed(Button.A), "Space presses fire");
        check(keyboard.handleKeyReleased(KeyEvent.VK_SPACE), "Space release is handled");

        check(!keyboard.handleKeyPressed(KeyEvent.VK_F2),
                "F2 is not a joystick key");
    }

    private static void consolePanelKeys() {
        SwingKeyboardPanel panel = new SwingKeyboardPanel();

        check(panel.colorEnabled(), "color mode is enabled by default");

        check(panel.handleKeyPressed(KeyEvent.VK_F2), "F2 is a panel key");
        check(panel.pressed(PanelSwitch.RESET), "F2 holds Reset");
        check(!panel.pressed(PanelSwitch.SELECT), "F2 does not press Select");
        check(panel.handleKeyReleased(KeyEvent.VK_F2), "F2 release is handled");
        check(!panel.pressed(PanelSwitch.RESET), "Reset is released");

        check(panel.handleKeyPressed(KeyEvent.VK_F3), "F3 is a panel key");
        check(panel.pressed(PanelSwitch.SELECT), "F3 holds Select");
        panel.handleKeyReleased(KeyEvent.VK_F3);
        check(!panel.pressed(PanelSwitch.SELECT), "Select is released");

        check(panel.handleKeyPressed(KeyEvent.VK_F4), "F4 is a panel key");
        check(!panel.colorEnabled(), "F4 switches to B&W");
        check(panel.handleKeyPressed(KeyEvent.VK_F4),
                "repeated F4 press while held is handled");
        check(!panel.colorEnabled(), "held F4 toggles only once");
        panel.handleKeyReleased(KeyEvent.VK_F4);
        check(panel.handleKeyPressed(KeyEvent.VK_F4), "F4 toggles back");
        check(panel.colorEnabled(), "F4 restores color mode");
        panel.handleKeyReleased(KeyEvent.VK_F4);

        panel.handleKeyPressed(KeyEvent.VK_F2);
        panel.handleKeyPressed(KeyEvent.VK_F3);
        panel.releaseAll();
        check(!panel.pressed(PanelSwitch.RESET) && !panel.pressed(PanelSwitch.SELECT),
                "releaseAll clears Reset and Select");
        check(panel.colorEnabled(), "releaseAll keeps Color/B&W state");

        panel.setColor(false);
        check(!panel.pressed(PanelSwitch.COLOR), "Color state can be set directly");
        check(!panel.pressed(PanelSwitch.DIFFICULTY_LEFT),
                "difficulty stays in default position");
    }

    private static void machineSwitches() {
        Atari2600Machine machine = new Atari2600Machine();
        machine.insert(0, new ByteArrayMedia("input.a26", MediaKind.CARTRIDGE, rom()));
        SwingKeyboardPanel panel = new SwingKeyboardPanel();
        SimpleController controller = new SimpleController();
        machine.connectPanel(panel);
        machine.connectController(0, controller);

        panel.handleKeyPressed(KeyEvent.VK_F3);
        controller.press(Button.A);
        machine.runFrame();
        check(machine.riot().ram().read(0x80) == 0x3D,
                "Select reaches SWCHB");
        check(machine.riot().ram().read(0x81) == 0x00,
                "A presses TIA fire");

        controller.release(Button.A);
        panel.handleKeyReleased(KeyEvent.VK_F3);
        panel.handleKeyPressed(KeyEvent.VK_F2);
        panel.handleKeyPressed(KeyEvent.VK_F4);
        machine.runFrame();
        check(machine.riot().ram().read(0x80) == 0x36,
                "Reset and B&W reach SWCHB");
        check(machine.riot().ram().read(0x81) == 0x80,
                "released fire reads high");

        panel.releaseAll();
        machine.runFrame();
        check(machine.riot().ram().read(0x80) == 0x37,
                "B&W state persists after releasing keys");
    }

    private static byte[] rom() {
        byte[] rom = new byte[4096];
        int[] code = {
                0xAD, 0x82, 0x02,  // LDA SWCHB
                0x85, 0x80,        // STA $80
                0xAD, 0x3C, 0x00,  // LDA INPT4
                0x85, 0x81,        // STA $81
                0x4C, 0x00, 0xF0   // JMP $F000
        };
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
