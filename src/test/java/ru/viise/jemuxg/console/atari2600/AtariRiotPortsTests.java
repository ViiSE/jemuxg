package ru.viise.jemuxg.console.atari2600;

import java.util.EnumSet;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.PanelSwitch;
import ru.viise.jemuxg.api.hardware.SwitchPanel;
import ru.viise.jemuxg.core.input.SimpleController;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.hardware.riot.riot6532.RiotPorts;

/**
 * Тесты портов A/B RIOT 6532.
 */
public final class AtariRiotPortsTests {

    private static int checks;

    private AtariRiotPortsTests() {
    }

    public static void main(String[] args) {
        initialState();
        joystickDirections();
        fireIsNotInSwcha();
        dataDirection();
        consoleSwitches();
        portBDataDirection();
        addressMask();
        saveRestore();
        reset();
        invalidPorts();
        System.out.println("Atari RIOT ports tests OK, checks=" + checks);
    }

    private static void initialState() {
        RiotPorts ports = new RiotPorts();
        check(ports.registers() == ports, "registers bus is the ports device");
        check(ports.read(RiotPorts.SWACNT) == 0, "initial SWACNT is zero");
        check(ports.read(RiotPorts.SWBCNT) == 0, "initial SWBCNT is zero");
        check(ports.read(RiotPorts.SWCHA) == 0xFF, "initial SWCHA has all directions released");
        check(ports.read(RiotPorts.SWCHB) == 0x3F, "initial SWCHB is color, difficulty B");
        check("RIOT 6532 Ports".equals(ports.name()), "ports name");
    }

    private static void joystickDirections() {
        RiotPorts ports = new RiotPorts();
        SimpleController p0 = new SimpleController();
        SimpleController p1 = new SimpleController();
        ports.connectPlayer(0, p0);
        ports.connectPlayer(1, p1);

        p0.press(Button.UP);
        check(ports.read(RiotPorts.SWCHA) == 0xFE, "player 0 UP clears bit 0");
        p0.release(Button.UP);

        p0.press(Button.DOWN);
        check(ports.read(RiotPorts.SWCHA) == 0xFD, "player 0 DOWN clears bit 1");
        p0.release(Button.DOWN);

        p0.press(Button.LEFT);
        check(ports.read(RiotPorts.SWCHA) == 0xFB, "player 0 LEFT clears bit 2");
        p0.release(Button.LEFT);

        p0.press(Button.RIGHT);
        check(ports.read(RiotPorts.SWCHA) == 0xF7, "player 0 RIGHT clears bit 3");
        p0.release(Button.RIGHT);

        p0.press(Button.UP);
        p0.press(Button.DOWN);
        p0.press(Button.LEFT);
        p0.press(Button.RIGHT);
        check(ports.read(RiotPorts.SWCHA) == 0xF0, "all player 0 directions");
        p0.releaseAll();

        p1.press(Button.UP);
        check(ports.read(RiotPorts.SWCHA) == 0xEF, "player 1 UP clears bit 4");
        p1.release(Button.UP);

        p1.press(Button.DOWN);
        check(ports.read(RiotPorts.SWCHA) == 0xDF, "player 1 DOWN clears bit 5");
        p1.release(Button.DOWN);

        p1.press(Button.LEFT);
        check(ports.read(RiotPorts.SWCHA) == 0xBF, "player 1 LEFT clears bit 6");
        p1.release(Button.LEFT);

        p1.press(Button.RIGHT);
        check(ports.read(RiotPorts.SWCHA) == 0x7F, "player 1 RIGHT clears bit 7");
        p1.release(Button.RIGHT);

        p1.press(Button.UP);
        p1.press(Button.DOWN);
        p1.press(Button.LEFT);
        p1.press(Button.RIGHT);
        check(ports.read(RiotPorts.SWCHA) == 0x0F, "all player 1 directions");
        p1.releaseAll();

        p0.press(Button.UP);
        p1.press(Button.DOWN);
        check(ports.read(RiotPorts.SWCHA) == 0xDE, "both players directions combine");
    }

    private static void fireIsNotInSwcha() {
        RiotPorts ports = new RiotPorts();
        SimpleController p0 = new SimpleController();
        ports.connectPlayer(0, p0);

        p0.press(Button.A);
        p0.press(Button.B);
        p0.press(Button.START);
        check(ports.read(RiotPorts.SWCHA) == 0xFF,
                "fire and non-direction buttons are not in SWCHA");
    }

    private static void dataDirection() {
        RiotPorts ports = new RiotPorts();
        ports.write(RiotPorts.SWACNT, 0x0F);
        ports.write(RiotPorts.SWCHA, 0x0A);
        check(ports.read(RiotPorts.SWACNT) == 0x0F, "SWACNT stores direction");
        check(ports.read(RiotPorts.SWCHA) == 0xFA,
                "port A output low nibble, input high nibble");

        SimpleController p0 = new SimpleController();
        ports.connectPlayer(0, p0);
        p0.press(Button.UP);
        check(ports.read(RiotPorts.SWCHA) == 0xFA,
                "output pin ignores joystick input");

        ports.write(RiotPorts.SWACNT, 0xF0);
        check(ports.read(RiotPorts.SWCHA) == 0x0E,
                "port A output high nibble, joystick input low nibble");
    }

    private static void consoleSwitches() {
        RiotPorts ports = new RiotPorts();
        TestPanel panel = new TestPanel();
        ports.connectPanel(panel);

        check(ports.read(RiotPorts.SWCHB) == 0x3F, "panel defaults to color/difficulty B");

        panel.set(PanelSwitch.RESET, true);
        check(ports.read(RiotPorts.SWCHB) == 0x3E, "RESET clears SWCHB bit 0");
        panel.set(PanelSwitch.RESET, false);

        panel.set(PanelSwitch.SELECT, true);
        check(ports.read(RiotPorts.SWCHB) == 0x3D, "SELECT clears SWCHB bit 1");
        panel.set(PanelSwitch.SELECT, false);

        panel.set(PanelSwitch.RESET, true);
        panel.set(PanelSwitch.SELECT, true);
        check(ports.read(RiotPorts.SWCHB) == 0x3C, "RESET and SELECT together");
        panel.set(PanelSwitch.RESET, false);
        panel.set(PanelSwitch.SELECT, false);

        panel.set(PanelSwitch.COLOR, false);
        check(ports.read(RiotPorts.SWCHB) == 0x37, "B&W clears SWCHB bit 3");
        panel.set(PanelSwitch.COLOR, true);
        check(ports.read(RiotPorts.SWCHB) == 0x3F, "color sets SWCHB bit 3");

        panel.set(PanelSwitch.DIFFICULTY_LEFT, true);
        check(ports.read(RiotPorts.SWCHB) == 0x7F, "left difficulty A sets bit 6");
        panel.set(PanelSwitch.DIFFICULTY_LEFT, false);
        panel.set(PanelSwitch.DIFFICULTY_RIGHT, true);
        check(ports.read(RiotPorts.SWCHB) == 0xBF, "right difficulty A sets bit 7");
        panel.set(PanelSwitch.DIFFICULTY_LEFT, true);
        check(ports.read(RiotPorts.SWCHB) == 0xFF, "both difficulties A");
    }

    private static void portBDataDirection() {
        RiotPorts ports = new RiotPorts();
        ports.connectPanel(new TestPanel());
        ports.write(RiotPorts.SWBCNT, 0x0F);
        ports.write(RiotPorts.SWCHB, 0x05);
        check(ports.read(RiotPorts.SWBCNT) == 0x0F, "SWBCNT stores direction");
        check(ports.read(RiotPorts.SWCHB) == 0x35,
                "port B output low nibble, switch high nibble");

        ports.write(RiotPorts.SWBCNT, 0xF0);
        check(ports.read(RiotPorts.SWCHB) == 0x0F,
                "port B output high nibble, switch low nibble");
    }

    private static void addressMask() {
        RiotPorts ports = new RiotPorts();
        ports.connectPanel(new TestPanel());
        check(ports.read(0x0280) == ports.read(RiotPorts.SWCHA), "$0280 aliases SWCHA");
        check(ports.read(0x0281) == ports.read(RiotPorts.SWACNT), "$0281 aliases SWACNT");
        ports.write(0x0283, 0xFF);
        check(ports.read(0x0283) == 0xFF, "$0283 aliases SWBCNT");
        check(ports.read(0x02FF) == 0xFF, "register address is masked to 2 bits");
    }

    private static void saveRestore() {
        RiotPorts original = new RiotPorts();
        SimpleController p0 = new SimpleController();
        p0.press(Button.UP);
        TestPanel panel = new TestPanel();
        original.connectPlayer(0, p0);
        original.connectPanel(panel);
        original.write(RiotPorts.SWACNT, 0x0F);
        original.write(RiotPorts.SWCHA, 0x0A);
        original.write(RiotPorts.SWBCNT, 0xF0);
        original.write(RiotPorts.SWCHB, 0x50);

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);

        RiotPorts restored = new RiotPorts();
        restored.connectPlayer(0, p0);
        restored.connectPanel(panel);
        restored.restore(new BinaryStateReader(writer.toByteArray()));

        check(restored.read(RiotPorts.SWACNT) == 0x0F, "restore SWACNT");
        check(restored.read(RiotPorts.SWCHA) == 0xFA, "restore SWCHA outputs/inputs");
        check(restored.read(RiotPorts.SWBCNT) == 0xF0, "restore SWBCNT");
        check(restored.read(RiotPorts.SWCHB) == 0x5F, "restore SWCHB outputs/switches");
    }

    private static void reset() {
        RiotPorts ports = new RiotPorts();
        ports.write(RiotPorts.SWACNT, 0xFF);
        ports.write(RiotPorts.SWCHA, 0x55);
        ports.write(RiotPorts.SWBCNT, 0xFF);
        ports.write(RiotPorts.SWCHB, 0xAA);

        ports.reset();

        check(ports.read(RiotPorts.SWACNT) == 0, "reset clears SWACNT");
        check(ports.read(RiotPorts.SWBCNT) == 0, "reset clears SWBCNT");
        check(ports.read(RiotPorts.SWCHA) == 0xFF, "reset restores SWCHA inputs");
        check(ports.read(RiotPorts.SWCHB) == 0x3F, "reset restores default switches");
    }

    private static void invalidPorts() {
        RiotPorts ports = new RiotPorts();
        expectThrows(() -> ports.connectPlayer(2, new SimpleController()),
                "port 2 is rejected");
        expectThrows(() -> ports.connectPlayer(-1, new SimpleController()),
                "negative port is rejected");
    }

    private static void expectThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            check(true, message);
            return;
        }
        check(false, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }

    private static final class TestPanel implements SwitchPanel {

        private final EnumSet<PanelSwitch> active = EnumSet.noneOf(PanelSwitch.class);

        TestPanel() {
            active.add(PanelSwitch.COLOR);
        }

        void set(PanelSwitch button, boolean pressed) {
            if (pressed) {
                active.add(button);
            } else {
                active.remove(button);
            }
        }

        @Override
        public boolean pressed(PanelSwitch button) {
            return active.contains(button);
        }
    }
}
