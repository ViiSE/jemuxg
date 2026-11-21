package ru.viise.jemuxg.console.sg1000;

import java.awt.event.KeyEvent;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.console.sg1000.cartridge.Sg1000CartridgeSlot;
import ru.viise.jemuxg.core.input.SimpleController;
import ru.viise.jemuxg.platform.input.SwingKeyboardPanel;

/**
 * Проверки джойстиков и кнопки Reset SG-1000.
 */
public final class Sg1000InputTests {

    private static int checks;

    private Sg1000InputTests() {
    }

    public static void main(String[] args) {
        idlePorts();
        playerOneButtons();
        playerTwoSplitPorts();
        resetButton();
        portMirrors();
        machineIntegration();
        System.out.println("SG-1000 input tests OK, checks=" + checks);
    }

    private static void idlePorts() {
        Sg1000Bus bus = bus();
        check(bus.in(0xDC) == 0xFF && bus.in(0xDD) == 0xFF,
                "unconnected ports read high");
    }

    private static void playerOneButtons() {
        Sg1000Bus bus = bus();
        SimpleController controller = new SimpleController();
        bus.connectController(0, controller);

        checkButton(bus, controller, Button.UP, 0x01, "P1 Up");
        checkButton(bus, controller, Button.DOWN, 0x02, "P1 Down");
        checkButton(bus, controller, Button.LEFT, 0x04, "P1 Left");
        checkButton(bus, controller, Button.RIGHT, 0x08, "P1 Right");
        checkButton(bus, controller, Button.A, 0x10, "P1 Button 1");
        checkButton(bus, controller, Button.B, 0x20, "P1 Button 2");

        controller.press(Button.UP);
        check((bus.in(0xDC) & 0xC0) == 0xC0,
                "P1 leaves P2 bits high when port 2 is absent");
        controller.releaseAll();
    }

    private static void checkButton(
            Sg1000Bus bus, SimpleController controller, Button button, int mask, String name) {
        controller.press(button);
        check((bus.in(0xDC) & mask) == 0, name + " is active-low");
        controller.release(button);
        check((bus.in(0xDC) & 0xFF) == 0xFF, name + " release restores high bits");
    }

    private static void playerTwoSplitPorts() {
        Sg1000Bus bus = bus();
        SimpleController second = new SimpleController();
        bus.connectController(1, second);

        second.press(Button.UP);
        check((bus.in(0xDC) & 0x40) == 0, "P2 Up is read from $DC bit 6");
        check((bus.in(0xDD) & 0xFF) == 0xFF, "P2 Up does not touch $DD");
        second.release(Button.UP);

        second.press(Button.DOWN);
        check((bus.in(0xDC) & 0x80) == 0, "P2 Down is read from $DC bit 7");
        second.release(Button.DOWN);

        checkButton(bus, 0xDD, second, Button.LEFT, 0x01, "P2 Left");
        checkButton(bus, 0xDD, second, Button.RIGHT, 0x02, "P2 Right");
        checkButton(bus, 0xDD, second, Button.A, 0x04, "P2 Button 1");
        checkButton(bus, 0xDD, second, Button.B, 0x08, "P2 Button 2");

        second.press(Button.LEFT);
        second.press(Button.A);
        check((bus.in(0xDD) & 0xF0) == 0xF0,
                "P2 leaves unused and Reset bits high");
        second.releaseAll();
    }

    private static void checkButton(
            Sg1000Bus bus, int port, SimpleController controller,
            Button button, int mask, String name) {
        controller.press(button);
        check((bus.in(port) & mask) == 0, name + " is active-low");
        controller.release(button);
        check((bus.in(port) & 0xFF) == 0xFF, name + " release restores high bits");
    }

    private static void resetButton() {
        Sg1000Bus bus = bus();
        SwingKeyboardPanel panel = new SwingKeyboardPanel();
        bus.connectPanel(panel);

        check(panel.handleKeyPressed(KeyEvent.VK_F2), "F2 is handled by the panel");
        check((bus.in(0xDD) & 0x10) == 0, "Reset is read from $DD bit 4");
        check(panel.handleKeyReleased(KeyEvent.VK_F2), "F2 release is handled");
        check(bus.in(0xDD) == 0xFF, "Reset release restores $DD bits");
    }

    private static void portMirrors() {
        Sg1000Bus bus = bus();
        SimpleController first = new SimpleController();
        SimpleController second = new SimpleController();
        bus.connectController(0, first);
        bus.connectController(1, second);
        first.press(Button.UP);
        second.press(Button.LEFT);

        for (int port = 0xC0; port <= 0xFF; port++) {
            int value = bus.in(port);
            if ((port & 1) == 0) {
                check((value & 0x01) == 0, "even input port " + hex(port) + " reads P1");
            } else {
                check((value & 0x01) == 0, "odd input port " + hex(port) + " reads P2");
            }
        }
    }

    private static void machineIntegration() {
        Sg1000Machine machine = new Sg1000Machine();
        SimpleController controller = new SimpleController();
        machine.connectController(0, controller);
        controller.press(Button.UP);
        check((machine.bus().in(Sg1000Constants.INPUT_P1) & 0x01) == 0,
                "machine forwards port 0 to the input bus");
        controller.releaseAll();
        check(machine.bus().in(Sg1000Constants.INPUT_P1) == 0xFF,
                "machine input is released");

        SwingKeyboardPanel panel = new SwingKeyboardPanel();
        machine.connectPanel(panel);
        panel.handleKeyPressed(KeyEvent.VK_F2);
        check((machine.bus().in(Sg1000Constants.INPUT_P2) & 0x10) == 0,
                "machine forwards the reset panel");

        boolean rejected = false;
        try {
            machine.connectController(2, new SimpleController());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "machine rejects a third controller port");
    }

    private static Sg1000Bus bus() {
        return new Sg1000Bus(new Sg1000CartridgeSlot());
    }

    private static String hex(int value) {
        return "$" + Integer.toHexString(value).toUpperCase();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
