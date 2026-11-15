package ru.viise.jemuxg.console.nes;

import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.core.input.SimpleController;

/**
 * Контрактные тесты последовательного опроса контроллеров NES.
 */
public final class InputTests {

    private static int checks;

    private InputTests() {
    }

    public static void main(String[] args) {
        RecordingBus apu = new RecordingBus();
        NesControllerBus bus = new NesControllerBus(apu);
        SimpleController port1 = new SimpleController();
        SimpleController port2 = new SimpleController();
        bus.connect(0, port1);
        bus.connect(1, port2);

        port1.press(Button.A);
        port1.press(Button.START);
        port1.press(Button.UP);
        port2.press(Button.B);

        bus.write(0x4016, 1);
        bus.write(0x4016, 0);

        int[] expected1 = {1, 0, 0, 1, 1, 0, 0, 0};
        for (int i = 0; i < expected1.length; i++) {
            check((bus.read(0x4016) & 1) == expected1[i], "port 1 serial bit " + i);
        }
        check((bus.read(0x4016) & 1) == 1, "port 1 idle high after 8 bits");

        int[] expected2 = {0, 1, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < expected2.length; i++) {
            check((bus.read(0x4017) & 1) == expected2[i], "port 2 serial bit " + i);
        }
        check((bus.read(0x4017) & 1) == 1, "port 2 idle high after 8 bits");

        bus.write(0x4016, 1);
        bus.write(0x4016, 0);
        check((bus.read(0x4016) & 1) == 1, "strobe restarts port 1 at A");

        port1.releaseAll();
        bus.write(0x4016, 1);
        bus.write(0x4016, 0);
        int released = 0;
        for (int i = 0; i < 8; i++) {
            released |= bus.read(0x4016) & 1;
        }
        check(released == 0, "releaseAll clears all buttons");

        bus.reset();
        bus.write(0x4016, 0);
        check((bus.read(0x4016) & 1) == 0, "reset rewinds serial state");

        bus.write(0x4017, 0x12);
        check(apu.lastAddress == 0x4017, "$4017 write forwarded to APU address");
        check(apu.lastValue == 0x12, "$4017 write forwarded to APU value");

        boolean rejected = false;
        try {
            bus.connect(2, new SimpleController());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "invalid controller port rejected");

        System.out.println("Input tests OK, checks=" + checks);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }

    private static final class RecordingBus implements Bus {

        private int lastAddress = -1;
        private int lastValue = -1;

        @Override
        public int read(int address) {
            return 0;
        }

        @Override
        public void write(int address, int value) {
            lastAddress = address;
            lastValue = value;
        }
    }
}
