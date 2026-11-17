package ru.viise.jemuxg.console.atari2600;

import java.awt.Dimension;
import ru.viise.jemuxg.platform.video.SwingVideoSink;

/**
 * Контрактные проверки фронтенда, не требующие открытия окна.
 */
public final class AtariFrontendTests {

    private static int checks;

    private AtariFrontendTests() {
    }

    public static void main(String[] args) {
        machineVideoSize();
        sinkPreferredSize();
        System.out.println("Atari frontend tests OK, checks=" + checks);
    }

    private static void machineVideoSize() {
        Atari2600Machine machine = new Atari2600Machine();
        check(machine.videoWidth() == 160, "Atari frame width is 160");
        check(machine.videoHeight() == 192, "Atari frame height is 192");
    }

    private static void sinkPreferredSize() {
        SwingVideoSink atari = new SwingVideoSink(160, 192);
        Dimension atariSize = atari.getPreferredSize();
        check(atariSize.width == 480 && atariSize.height == 576,
                "Atari window keeps 160x192 proportions");

        SwingVideoSink nes = new SwingVideoSink();
        Dimension nesSize = nes.getPreferredSize();
        check(nesSize.width == 768 && nesSize.height == 720,
                "default NES window remains 768x720");

        boolean rejected = false;
        try {
            new SwingVideoSink(0, 192);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "invalid frame size is rejected");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
