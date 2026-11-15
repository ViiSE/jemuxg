package ru.viise.jemuxg.console.nes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.nes.debug.NestestRunner;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Запуск эталонных ROM: nestest и PPU/sprite-hit тестов Blargg.
 */
public final class TestRomTests {

    private static final String PASS_SCREEN =
            "783e416b091a3ec9f138b4cfcb3a6bd5f16175a1f3fcab72673cb0c3f2b509eb";
    private static final int FRAMES = 300;
    private static final int PPU_READ_BUFFER_MAX_FRAMES = 4000;
    private static int checks;

    private TestRomTests() {
    }

    public static void main(String[] args) throws Exception {
        nestest();
        blarggPpu();
        ppuReadBuffer();
        spriteHit();
        System.out.println("Test ROM tests OK, checks=" + checks);
    }

    private static void nestest() throws IOException {
        Path directory = Files.createTempDirectory("jemuxg-nestest");
        Path rom = directory.resolve("nestest.nes");
        Path log = directory.resolve("nestest.log");
        Files.write(rom, resource("nestest.nes"));
        Files.write(log, resource("nestest.log"));

        NestestRunner.Result result = NestestRunner.run(rom, log);
        check(result.passed(), "nestest golden log passes");
        check(result.totalLines() == 8991, "nestest contains 8991 instructions");
        check(result.matchedLines() == 8991, "nestest matches all instructions");
    }

    private static void blarggPpu() throws Exception {
        checkScreen("blargg/palette_ram.nes", PASS_SCREEN, "$01 PASS screen");
        checkScreen("blargg/power_up_palette.nes", PASS_SCREEN, "$01 PASS screen");
        checkScreen("blargg/sprite_ram.nes", PASS_SCREEN, "$01 PASS screen");
        checkScreen("blargg/vram_access.nes", PASS_SCREEN, "$01 PASS screen");
        checkScreen("blargg/vbl_clear_time.nes", PASS_SCREEN, "vbl_clear_time $01 PASS screen");
    }

    private static void ppuReadBuffer() throws Exception {
        byte[] rom = resource("blargg/test_ppu_read_buffer.nes");
        NesMachine machine = new NesMachine();
        machine.insert(0, new ByteArrayMedia(
                "test_ppu_read_buffer.nes", MediaKind.CARTRIDGE, rom));
        machine.reset();

        boolean started = false;
        int status = -1;
        int frames = 0;
        byte[] prgRam = new byte[0x2000];
        for (; frames < PPU_READ_BUFFER_MAX_FRAMES; frames++) {
            machine.runFrame();
            prgRam = machine.debugSnapshot().prgRam();
            if ((prgRam[1] & 0xFF) == 0xDE
                    && (prgRam[2] & 0xFF) == 0xB0
                    && (prgRam[3] & 0xFF) == 0x61) {
                started = true;
                status = prgRam[0] & 0xFF;
                if (status < 0x80) {
                    break;
                }
            }
        }

        check(started, "test_ppu_read_buffer starts");
        check(frames < PPU_READ_BUFFER_MAX_FRAMES, "test_ppu_read_buffer completes in time");
        check(status == 0, "test_ppu_read_buffer result code is 0");

        int end = 4;
        while (end < prgRam.length && prgRam[end] != 0) {
            end++;
        }
        String text = new String(prgRam, 4, end - 4, StandardCharsets.ISO_8859_1)
                .replaceAll("\u001B\\[[0-9;]*[A-Za-z]", "");
        check(text.contains("Passed"), "test_ppu_read_buffer reports Passed");
        check(distinctColors(machine.debugSnapshot().framebufferIndices()) > 1,
                "test_ppu_read_buffer screen is not blank");
    }

    private static void spriteHit() throws Exception {
        checkScreen(
                "sprite-hit/01.basics.nes",
                "83d15be3a3ae1d718872921e2135c7db1034059ae803aaa0fdc0ad075f233b55",
                "sprite hit basics PASSED");
        checkScreen(
                "sprite-hit/02.alignment.nes",
                "57dc5946584144ceb4cc00f64bd5acdae204307bd025c3cb80954410c9e9f1de",
                "sprite hit alignment PASSED");
        checkScreen(
                "sprite-hit/03.corners.nes",
                "bd7519add80c0f7d1989c6ca5d6f0945f1a51c3506c45a7c717e2e1a0cddb82a",
                "sprite hit corners PASSED");
        checkScreen(
                "sprite-hit/04.flip.nes",
                "46d848fdcb3bdca3172ffd1ab274a736af380531d4429dde9fa6e09265036bb0",
                "sprite hit flipping PASSED");
        checkScreen(
                "sprite-hit/05.left_clip.nes",
                "ecfa9b624d3eb50dd46e943202a86465353f2a771a1b53b9ae43431018f6cf60",
                "sprite hit left clipping PASSED");
    }

    private static void checkScreen(String resource, String expectedHash, String message)
            throws Exception {
        byte[] framebuffer = runRom(resource);
        check(distinctColors(framebuffer) > 1, message + ": screen is not blank");
        check(expectedHash.equals(sha256(framebuffer)), message + ": framebuffer hash");
    }

    private static byte[] runRom(String resource) throws IOException {
        byte[] rom = resource(resource);
        NesMachine machine = new NesMachine();
        machine.insert(0, new ByteArrayMedia(resource, MediaKind.CARTRIDGE, rom));
        machine.reset();
        for (int frame = 0; frame < FRAMES; frame++) {
            machine.runFrame();
        }
        return machine.debugSnapshot().framebufferIndices();
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = TestRomTests.class.getResourceAsStream("/test-roms/" + name)) {
            if (input == null) {
                throw new IOException("missing test resource /test-roms/" + name);
            }
            return input.readAllBytes();
        }
    }

    private static int distinctColors(byte[] framebuffer) {
        Set<Integer> colors = new HashSet<>();
        for (byte value : framebuffer) {
            colors.add(value & 0xFF);
        }
        return colors.size();
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 0xFF));
        }
        return hex.toString();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
