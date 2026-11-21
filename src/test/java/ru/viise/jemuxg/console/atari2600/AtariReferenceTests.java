package ru.viise.jemuxg.console.atari2600;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Сравнение кадров Atari 2600 с эталонными снимками Stella из
 * missingno-vcs-tests. Снимки сделаны для NTSC-разметки, поэтому кадр
 * эмулятора сравнивается с соответствующим вертикальным срезом PNG.
 */
public final class AtariReferenceTests {

    private static final int FRAMES = 10;

    private static int checks;

    private AtariReferenceTests() {
    }

    public static void main(String[] args) throws Exception {
        compare("colors", 37);
        compare("score-mode", 41);
        System.out.println("Atari reference ROM tests OK, checks=" + checks);
    }

    private static void compare(String name, int referenceYOffset) throws Exception {
        BufferedImage reference = readImage("/test-roms/atari/" + name + "_ntsc.png");
        check(reference.getWidth() == Atari2600Constants.VISIBLE_WIDTH,
                name + ": reference image width is 160");
        check(reference.getHeight() >= referenceYOffset
                        + Atari2600Constants.VISIBLE_HEIGHT,
                name + ": reference image has enough visible lines");

        Atari2600Machine machine = new Atari2600Machine();
        machine.insert(0, new ByteArrayMedia(
                name + "_ntsc.a26", MediaKind.CARTRIDGE,
                readResource("/test-roms/atari/" + name + "_ntsc.a26")));
        for (int i = 0; i < FRAMES; i++) {
            machine.runFrame();
        }

        int[] frame = machine.debugSnapshot().framebufferArgb();
        int mismatches = 0;
        int firstX = -1;
        int firstY = -1;
        for (int y = 0; y < Atari2600Constants.VISIBLE_HEIGHT; y++) {
            for (int x = 0; x < Atari2600Constants.VISIBLE_WIDTH; x++) {
                int actual = frame[y * Atari2600Constants.VISIBLE_WIDTH + x];
                int expected = reference.getRGB(x, y + referenceYOffset);
                if ((actual & 0xFFFFFF) != (expected & 0xFFFFFF)) {
                    if (mismatches == 0) {
                        firstX = x;
                        firstY = y;
                    }
                    mismatches++;
                }
            }
        }
        check(mismatches == 0, name + ": frame matches Stella reference exactly"
                + (mismatches == 0 ? "" : " (mismatches=" + mismatches
                        + ", first=" + firstX + "," + firstY + ")"));
        check((frame[100 * Atari2600Constants.VISIBLE_WIDTH + 80] & 0xFFFFFF)
                        == (reference.getRGB(80, 100 + referenceYOffset) & 0xFFFFFF),
                name + ": center pixel matches reference");
    }

    private static byte[] readResource(String name) throws Exception {
        try (InputStream in = AtariReferenceTests.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: " + name);
            }
            return in.readAllBytes();
        }
    }

    private static BufferedImage readImage(String name) throws Exception {
        try (InputStream in = AtariReferenceTests.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: " + name);
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalStateException("cannot decode image: " + name);
            }
            return image;
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
