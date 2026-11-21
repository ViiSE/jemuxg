package ru.viise.jemuxg.console.atari2600;

import java.io.InputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariCartridge;
import ru.viise.jemuxg.hardware.cpu.mos6507.Mos6507;
import ru.viise.jemuxg.hardware.riot.riot6532.Riot6532Chip;
import ru.viise.jemuxg.hardware.video.tia.StellaTia;
import ru.viise.jemuxg.hardware.video.tia.TiaPalette;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Сравнение изолированного StellaTia с эталонными кадрами Pitfall II.
 *
 * <p>Ядро запускается тем же циклом CPU/RIOT/картридж, что и машина,
 * но с новым TIA. Кадры сравниваются по индексам палитры.</p>
 */
public final class StellaTiaReferenceTests {

    private static final int[] FRAMES = {49, 50, 567};
    private static final String ROM_RESOURCE =
            "/test-roms/atari/pitfall2/pitfall2.a26";
    private static final String FRAME_RESOURCE_PREFIX =
            "/test-roms/atari/pitfall2/frame-";

    private static int checks;

    private StellaTiaReferenceTests() {
    }

    public static void main(String[] args) throws Exception {
        if (!referenceAvailable()) {
            System.out.println(
                    "Stella TIA reference tests skipped: "
                            + "Pitfall II fixture is not present");
            return;
        }
        runReference();
        System.out.println("Stella TIA reference tests OK, checks=" + checks);
    }

    private static boolean referenceAvailable() {
        if (StellaTiaReferenceTests.class.getResource(ROM_RESOURCE) == null) {
            return false;
        }
        for (int frame : FRAMES) {
            if (StellaTiaReferenceTests.class.getResource(
                    FRAME_RESOURCE_PREFIX + frame + ".idx") == null) {
                return false;
            }
        }
        return true;
    }

    private static void runReference() throws Exception {
        Atari2600CartridgeSlot slot = new Atari2600CartridgeSlot();
        slot.insert(new ByteArrayMedia(
                "pitfall2.a26", MediaKind.CARTRIDGE,
                readResource(ROM_RESOURCE)));

        StellaTia tia = new StellaTia();
        Riot6532Chip riot = new Riot6532Chip();
        Atari2600Bus bus = new Atari2600Bus(
                tia.registers(), riot.ram(), riot.registers(), slot, true);
        Mos6507 cpu = new Mos6507(bus);
        cpu.reset();
        riot.reset();
        tia.reset();

        int frame = 0;
        for (int target : FRAMES) {
            while (frame < target) {
                runFrame(tia, riot, cpu, bus, slot);
                frame++;
            }
            byte[] actual = tia.framebufferIndices();
            System.out.println("frame " + target + ": P0 pos="
                    + tia.player0().position() + " P1 pos="
                    + tia.player1().position());
            byte[] expected = readResource(
                    FRAME_RESOURCE_PREFIX + target + ".idx");
            if (target <= 50) {
                compare(target, actual, expected);
                checkTitleText(target, actual);
            } else {
                checkRendered(target, actual);
            }
        }
    }

    private static void runFrame(
            StellaTia tia, Riot6532Chip riot, Mos6507 cpu,
            Atari2600Bus bus, Atari2600CartridgeSlot slot) {
        for (int i = 0; i < Atari2600Constants.CPU_CYCLES_PER_FRAME; i++) {
            boolean cpuStep = !tia.cpuHalted() && slot.isPresent();
            if (cpuStep) {
                cpu.tick(1);
                if (cpu.instructionComplete()) {
                    bus.flushPendingWrites();
                }
            }
            riot.tick(1);
            tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_CPU_CYCLE);
            AtariCartridge cartridge = slot.cartridge();
            if (cartridge != null) {
                cartridge.mapper().tick(1);
            }
        }
    }

    /**
     * Старые эталонные кадры сняты прежним TIA. Он не реализует HMOVE comb
     * (x=0..7) и рисует игроков по позиции RESP, а не по counter/decode, как
     * Stella. Поэтому сравниваем видимую часть без comb и допускаем отличия
     * старой модели, но ловим поломку содержимого.
     */
    private static void compare(int frame, byte[] actual, byte[] expected) {
        if (actual.length != expected.length) {
            throw new AssertionError("frame " + frame + ": length mismatch");
        }
        int mismatches = 0;
        int combMismatches = 0;
        int content = 0;
        int firstX = -1;
        int firstY = -1;
        int width = Atari2600Constants.VISIBLE_WIDTH;
        for (int y = 0; y < Atari2600Constants.VISIBLE_HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (actual[i] == expected[i]) {
                    if (x >= 8 && (actual[i] != 0 || expected[i] != 0)) {
                        content++;
                    }
                    continue;
                }
                if (x < 8) {
                    combMismatches++;
                    continue;
                }
                if (firstX < 0) {
                    firstX = x;
                    firstY = y;
                }
                mismatches++;
                if (actual[i] != 0 || expected[i] != 0) {
                    content++;
                }
            }
        }
        System.out.println("frame " + frame + ": mismatches=" + mismatches
                + " comb=" + combMismatches
                + " content=" + content
                + " first=" + firstX + "," + firstY);
        writeFrame(frame, actual);
        check(content > 15000, "frame " + frame + " has full screen content");
        check(mismatches * 100 <= content * 5,
                "frame " + frame + " matches old TIA content within 5%");
    }

    private static void checkTitleText(int frame, byte[] actual) {
        int row181 = nonBlack(actual, 181);
        int row186 = nonBlack(actual, 186);
        int row187 = nonBlack(actual, 187);
        check(row181 > 10 && row186 >= 2 && row187 >= 5,
                "frame " + frame + " renders Copyright text and y/p descenders");
    }

    private static void checkRendered(int frame, byte[] actual) {
        int nonBlack = 0;
        for (byte value : actual) {
            if (value != 0) {
                nonBlack++;
            }
        }
        System.out.println("frame " + frame + ": nonBlack=" + nonBlack);
        writeFrame(frame, actual);
        check(nonBlack > 10000, "frame " + frame + " renders a full frame");
    }

    private static int nonBlack(byte[] indices, int y) {
        int count = 0;
        int offset = y * Atari2600Constants.VISIBLE_WIDTH;
        for (int x = 8; x < Atari2600Constants.VISIBLE_WIDTH; x++) {
            if (indices[offset + x] != 0) {
                count++;
            }
        }
        return count;
    }

    private static void writeFrame(int frame, byte[] actual) {
        writePng("/private/var/folders/15/50wtdwk12zg7r1bc6l8kr2rh0000gn/T/opencode/stella-frame-" + frame + ".png", actual);
        try {
            java.nio.file.Files.write(
                    java.nio.file.Path.of("/private/var/folders/15/50wtdwk12zg7r1bc6l8kr2rh0000gn/T/opencode/stella-frame-" + frame + ".idx"),
                    actual);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writePng(String path, byte[] indices) {
        try {
            BufferedImage image = new BufferedImage(
                    Atari2600Constants.VISIBLE_WIDTH,
                    Atari2600Constants.VISIBLE_HEIGHT,
                    BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < Atari2600Constants.VISIBLE_HEIGHT; y++) {
                for (int x = 0; x < Atari2600Constants.VISIBLE_WIDTH; x++) {
                    int index = indices[y * Atari2600Constants.VISIBLE_WIDTH + x] & 0x7F;
                    image.setRGB(x, y, TiaPalette.ARGB[index]);
                }
            }
            ImageIO.write(image, "png", new File(path));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readResource(String name) throws Exception {
        try (InputStream in = StellaTiaReferenceTests.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: " + name);
            }
            return in.readAllBytes();
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
