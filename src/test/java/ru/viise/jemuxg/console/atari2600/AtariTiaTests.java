package ru.viise.jemuxg.console.atari2600;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.core.input.SimpleController;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.hardware.video.tia.Tia;
import ru.viise.jemuxg.hardware.video.tia.TiaPalette;

/**
 * Тесты каркаса развёртки TIA.
 */
public final class AtariTiaTests {

    private static int checks;

    public static void main(String[] args) {
        initialState();
        paletteTable();
        indexFramebuffer();
        playfieldPatternAndBackground();
        playfieldReflect();
        playfieldScoreMode();
        playfieldVblank();
        playfieldControlBits();
        playfieldSaveRestore();
        playerGraphics();
        playerCopiesAndSizes();
        missilesAndBall();
        verticalDelay();
        resmpLocksMissile();
        objectSaveRestore();
        hmoveOffsets();
        hmoveAllObjects();
        hmoveClear();
        hmoveLateAndSaveRestore();
        collisions();
        collisionClearAndVblank();
        tiaInput();
        machineControllerWiring();
        midFrameSaveRestore();
        lineBoundary();
        frameDelivery();
        wsyncStopsCpu();
        syncRegisters();
        resetState();
        saveRestore();
        machineRunFrame();
        negativeTickRejected();
        System.out.println("Atari TIA tests OK, checks=" + checks);
    }

    private static void initialState() {
        Tia tia = new Tia();
        check(tia.width() == Atari2600Constants.VISIBLE_WIDTH, "TIA width");
        check(tia.height() == Atari2600Constants.VISIBLE_HEIGHT, "TIA height");
        check(tia.scanline() == 0, "TIA starts at scanline 0");
        check(tia.colorClock() == 0, "TIA starts at color clock 0");
        check(!tia.cpuHalted(), "TIA starts not halted");
        check(tia.frameCount() == 0, "TIA starts at frame 0");
        check("TIA".equals(tia.name()), "TIA name");
    }

    private static void paletteTable() {
        check(TiaPalette.size() == 128, "TIA palette has 128 colors");
        check(TiaPalette.ARGB[0x00] == 0xFF000000, "TIA palette 0x00 black");
        check(TiaPalette.ARGB[0x07] == 0xFFECECEC, "TIA palette 0x07 white");
        check(TiaPalette.ARGB[0x08] == 0xFF484800, "TIA palette 0x08 yellow");
        check(TiaPalette.ARGB[0x0F] == 0xFFFCFC54, "TIA palette 0x0F bright yellow");
        check(TiaPalette.ARGB[0x20] == 0xFF940000, "TIA palette 0x20 red");
        check(TiaPalette.ARGB[0x27] == 0xFFFC9090, "TIA palette 0x27 bright red");
        check(TiaPalette.ARGB[0x40] == 0xFF000094, "TIA palette 0x40 blue");
        check(TiaPalette.ARGB[0x7F] == 0xFFFCE070, "TIA palette 0x7F light brown");

        boolean alpha = true;
        for (int color : TiaPalette.ARGB) {
            alpha &= (color >>> 24) == 0xFF;
        }
        check(alpha, "TIA palette colors are opaque");
    }

    private static void indexFramebuffer() {
        Tia tia = new Tia();
        byte[] indices = tia.framebufferIndices();
        check(indices.length == Atari2600Constants.VISIBLE_WIDTH
                * Atari2600Constants.VISIBLE_HEIGHT, "TIA index framebuffer size");

        boolean zero = true;
        for (byte index : indices) {
            zero &= index == 0;
        }
        check(zero, "TIA index framebuffer starts at color 0");
        check(tia.framebufferArgb()[0] == TiaPalette.ARGB[0],
                "TIA framebuffer starts with palette color 0");

        indices[0] = 127;
        check(tia.framebufferIndices()[0] == 0,
                "TIA index framebuffer returns a copy");
    }

    private static void playfieldPatternAndBackground() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.COLUBK, 0x08);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.PF0, 0x10);
        registers.write(Tia.PF1, 0x80);
        registers.write(Tia.PF2, 0x01);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        int[] pixels = tia.framebufferArgb();
        int pf = TiaPalette.ARGB[0x0F];
        int bk = TiaPalette.ARGB[0x08];

        check(pixels[0] == pf, "playfield PF0 bit renders");
        check(pixels[3] == pf, "playfield PF0 spans four pixels");
        check(pixels[4] == bk, "playfield PF0 ends after four pixels");
        check(pixels[16] == pf, "playfield PF1 bit renders");
        check(pixels[20] == bk, "playfield PF1 ends after four pixels");
        check(pixels[48] == pf, "playfield PF2 bit renders");
        check(pixels[52] == bk, "playfield PF2 ends after four pixels");
        check(pixels[80] == pf, "playfield repeats PF0 on right half");
        check(pixels[96] == pf, "playfield repeats PF1 on right half");
        check(pixels[128] == pf, "playfield repeats PF2 on right half");
        byte[] indices = tia.framebufferIndices();
        check((indices[0] & 0xFF) == 0x0F, "playfield index framebuffer PF color");
        check((indices[4] & 0xFF) == 0x08, "playfield index framebuffer background color");
    }

    private static void playfieldReflect() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.CTRLPF, 0x01);
        registers.write(Tia.COLUPF, 0x20);
        registers.write(Tia.PF2, 0x80);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        int[] pixels = tia.framebufferArgb();
        int pf = TiaPalette.ARGB[0x20];

        check(pixels[80] == pf, "reflected playfield starts with PF2 bit 7");
        check(pixels[83] == pf, "reflected playfield spans four pixels");
        check(pixels[84] == TiaPalette.ARGB[0], "reflected playfield ends after four pixels");
        check(tia.controlPf() == 0x01, "CTRLPF reflect bit stored");
    }

    private static void playfieldScoreMode() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.CTRLPF, 0x02);
        registers.write(Tia.COLUP0, 0x20);
        registers.write(Tia.COLUP1, 0x40);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.COLUBK, 0x08);
        registers.write(Tia.PF0, 0x10);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        int[] pixels = tia.framebufferArgb();

        check(pixels[0] == TiaPalette.ARGB[0x20], "score mode uses COLUP0 on left");
        check(pixels[80] == TiaPalette.ARGB[0x40], "score mode uses COLUP1 on right");
        check(pixels[4] == TiaPalette.ARGB[0x08], "score mode keeps background color");
    }

    private static void playfieldVblank() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.VBLANK, 0x02);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.PF0, 0x10);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        check(tia.framebufferArgb()[0] == 0xFF000000, "VBLANK hides playfield");
    }

    private static void playfieldControlBits() {
        Tia tia = new Tia();
        tia.registers().write(Tia.CTRLPF, 0x34);
        check(tia.controlPf() == 0x34, "CTRLPF priority and ball size bits stored");
        tia.registers().write(Tia.CTRLPF, 0x00);
        check(tia.controlPf() == 0x00, "CTRLPF bits cleared");
    }

    private static void playfieldSaveRestore() {
        Tia original = new Tia();
        Bus registers = original.registers();
        registers.write(Tia.COLUBK, 0x08);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.PF0, 0x10);
        original.tick(500);

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);
        Tia restored = new Tia();
        restored.restore(new BinaryStateReader(writer.toByteArray()));

        long remaining = Atari2600Constants.COLOR_CLOCKS_PER_FRAME - 500;
        original.tick(remaining);
        restored.tick(remaining);
        check(Arrays.equals(original.framebufferArgb(), restored.framebufferArgb()),
                "restored playfield renders identically");
        check(restored.framebufferArgb()[0] == TiaPalette.ARGB[0x0F],
                "restored playfield uses restored color");
    }

    private static void playerGraphics() {
        Tia tia = new Tia();
        positionAt(tia, Tia.RESP0, 10);
        Bus registers = tia.registers();
        registers.write(Tia.COLUP0, 0x20);
        registers.write(Tia.COLUBK, 0x08);
        registers.write(Tia.GRP0, 0xFF);
        finishFrame(tia);

        int[] pixels = tia.framebufferArgb();
        check(pixelAt(pixels, 10, 150) == TiaPalette.ARGB[0x20],
                "player 0 first pixel uses COLUP0");
        check(pixelAt(pixels, 17, 150) == TiaPalette.ARGB[0x20],
                "player 0 spans eight pixels");
        check(pixelAt(pixels, 18, 150) == TiaPalette.ARGB[0x08],
                "player 0 ends after eight pixels");
        check((tia.framebufferIndices()[150 * 160 + 10] & 0xFF) == 0x20,
                "player 0 writes palette index");

        Tia reflected = new Tia();
        positionAt(reflected, Tia.RESP0, 10);
        reflected.registers().write(Tia.COLUP0, 0x20);
        reflected.registers().write(Tia.GRP0, 0x01);
        reflected.registers().write(Tia.REFP0, 0x08);
        finishFrame(reflected);
        int[] reflectedPixels = reflected.framebufferArgb();
        check(pixelAt(reflectedPixels, 10, 150) == TiaPalette.ARGB[0x20],
                "reflected player 0 starts with pattern bit 0");
        check(pixelAt(reflectedPixels, 17, 150) == TiaPalette.ARGB[0],
                "reflected player 0 clears pattern bit 7");

        Tia player1 = new Tia();
        positionAt(player1, Tia.RESP1, 40);
        player1.registers().write(Tia.COLUP1, 0x40);
        player1.registers().write(Tia.GRP1, 0x80);
        finishFrame(player1);
        check(pixelAt(player1.framebufferArgb(), 40, 150) == TiaPalette.ARGB[0x40],
                "player 1 uses COLUP1 and RESP1 position");
    }

    private static void playerCopiesAndSizes() {
        Tia close = playerWithNusiz(1);
        check(pixelAt(close.framebufferArgb(), 26, 150) == TiaPalette.ARGB[0x20],
                "NUSIZ 1 renders two close copies");

        Tia medium = playerWithNusiz(2);
        check(pixelAt(medium.framebufferArgb(), 42, 150) == TiaPalette.ARGB[0x20],
                "NUSIZ 2 renders two medium copies");

        Tia wide = playerWithNusiz(4);
        check(pixelAt(wide.framebufferArgb(), 74, 150) == TiaPalette.ARGB[0x20],
                "NUSIZ 4 renders two wide copies");

        Tia threeClose = playerWithNusiz(3);
        check(pixelAt(threeClose.framebufferArgb(), 26, 150) == TiaPalette.ARGB[0x20],
                "NUSIZ 3 second copy");
        check(pixelAt(threeClose.framebufferArgb(), 42, 150) == TiaPalette.ARGB[0x20],
                "NUSIZ 3 third copy");

        Tia threeMedium = playerWithNusiz(6);
        check(pixelAt(threeMedium.framebufferArgb(), 42, 150) == TiaPalette.ARGB[0x20],
                "NUSIZ 6 second copy");
        check(pixelAt(threeMedium.framebufferArgb(), 74, 150) == TiaPalette.ARGB[0x20],
                "NUSIZ 6 third copy");

        Tia doubled = playerWithNusiz(5);
        check(pixelAt(doubled.framebufferArgb(), 10, 150) == TiaPalette.ARGB[0x20]
                        && pixelAt(doubled.framebufferArgb(), 11, 150) == TiaPalette.ARGB[0x20],
                "NUSIZ 5 doubles player width");
        check(pixelAt(doubled.framebufferArgb(), 12, 150) == TiaPalette.ARGB[0],
                "NUSIZ 5 stops after double width");

        Tia quad = playerWithNusiz(7);
        boolean quadWidth = true;
        for (int x = 10; x < 14; x++) {
            quadWidth &= pixelAt(quad.framebufferArgb(), x, 150) == TiaPalette.ARGB[0x20];
        }
        check(quadWidth, "NUSIZ 7 quadruples player width");
        check(pixelAt(quad.framebufferArgb(), 14, 150) == TiaPalette.ARGB[0],
                "NUSIZ 7 stops after quad width");
    }

    private static Tia playerWithNusiz(int nusiz) {
        Tia tia = new Tia();
        positionAt(tia, Tia.RESP0, 10);
        Bus registers = tia.registers();
        registers.write(Tia.NUSIZ0, nusiz);
        registers.write(Tia.COLUP0, 0x20);
        registers.write(Tia.GRP0, 0x80);
        finishFrame(tia);
        return tia;
    }

    private static void missilesAndBall() {
        Tia missile0 = new Tia();
        positionAt(missile0, Tia.RESM0, 20);
        missile0.registers().write(Tia.COLUP0, 0x30);
        missile0.registers().write(Tia.NUSIZ0, 0);
        missile0.registers().write(Tia.ENAM0, 0x02);
        finishFrame(missile0);
        check(pixelAt(missile0.framebufferArgb(), 20, 150) == TiaPalette.ARGB[0x30],
                "missile 0 renders one pixel");
        check(pixelAt(missile0.framebufferArgb(), 21, 150) == TiaPalette.ARGB[0],
                "missile 0 width 1 ends");

        Tia missile0Wide = new Tia();
        positionAt(missile0Wide, Tia.RESM0, 20);
        missile0Wide.registers().write(Tia.COLUP0, 0x30);
        missile0Wide.registers().write(Tia.NUSIZ0, 0x10);
        missile0Wide.registers().write(Tia.ENAM0, 0x02);
        finishFrame(missile0Wide);
        check(pixelAt(missile0Wide.framebufferArgb(), 20, 150) == TiaPalette.ARGB[0x30]
                        && pixelAt(missile0Wide.framebufferArgb(), 21, 150) == TiaPalette.ARGB[0x30],
                "NUSIZ missile width 2");
        check(pixelAt(missile0Wide.framebufferArgb(), 22, 150) == TiaPalette.ARGB[0],
                "NUSIZ missile width 2 ends");

        Tia missileCopies = new Tia();
        positionAt(missileCopies, Tia.RESM0, 20);
        missileCopies.registers().write(Tia.COLUP0, 0x30);
        missileCopies.registers().write(Tia.NUSIZ0, 0x01);
        missileCopies.registers().write(Tia.ENAM0, 0x02);
        finishFrame(missileCopies);
        check(pixelAt(missileCopies.framebufferArgb(), 36, 150) == TiaPalette.ARGB[0x30],
                "missile 0 copies follow NUSIZ");

        Tia missile1 = new Tia();
        positionAt(missile1, Tia.RESM1, 30);
        missile1.registers().write(Tia.COLUP1, 0x40);
        missile1.registers().write(Tia.ENAM1, 0x02);
        finishFrame(missile1);
        check(pixelAt(missile1.framebufferArgb(), 30, 150) == TiaPalette.ARGB[0x40],
                "missile 1 uses COLUP1 and RESP1 position");

        Tia ball = new Tia();
        positionAt(ball, Tia.RESBL, 40);
        ball.registers().write(Tia.COLUPF, 0x0F);
        ball.registers().write(Tia.CTRLPF, 0x10);
        ball.registers().write(Tia.ENABL, 0x02);
        finishFrame(ball);
        check(pixelAt(ball.framebufferArgb(), 40, 150) == TiaPalette.ARGB[0x0F]
                        && pixelAt(ball.framebufferArgb(), 41, 150) == TiaPalette.ARGB[0x0F],
                "ball width 2 from CTRLPF");
        check(pixelAt(ball.framebufferArgb(), 42, 150) == TiaPalette.ARGB[0],
                "ball width 2 ends");
    }

    private static void verticalDelay() {
        Tia player = new Tia();
        positionAt(player, Tia.RESP0, 10);
        player.registers().write(Tia.COLUP0, 0x20);
        player.registers().write(Tia.VDELP0, 0x01);
        player.registers().write(Tia.GRP0, 0xFF);
        finishFrame(player);
        check(pixelAt(player.framebufferArgb(), 10, 150) == TiaPalette.ARGB[0],
                "VDELP0 delays first player write");

        player.registers().write(Tia.GRP0, 0x00);
        finishFrame(player);
        check(pixelAt(player.framebufferArgb(), 10, 150) == TiaPalette.ARGB[0x20],
                "VDELP0 shows previous player pattern");

        Tia ball = new Tia();
        positionAt(ball, Tia.RESBL, 30);
        ball.registers().write(Tia.COLUPF, 0x0F);
        ball.registers().write(Tia.VDELBL, 0x01);
        ball.registers().write(Tia.ENABL, 0x02);
        finishFrame(ball);
        check(pixelAt(ball.framebufferArgb(), 30, 150) == TiaPalette.ARGB[0],
                "VDELBL delays first ball enable");

        ball.registers().write(Tia.ENABL, 0x00);
        finishFrame(ball);
        check(pixelAt(ball.framebufferArgb(), 30, 150) == TiaPalette.ARGB[0x0F],
                "VDELBL shows previous ball enable");
    }

    private static void resmpLocksMissile() {
        Tia tia = new Tia();
        positionAt(tia, Tia.RESP0, 10);
        positionAt(tia, Tia.RESM0, 30);
        Bus registers = tia.registers();
        registers.write(Tia.COLUP0, 0x20);
        registers.write(Tia.RESMP0, 0x02);
        registers.write(Tia.ENAM0, 0x02);
        finishFrame(tia);

        int[] pixels = tia.framebufferArgb();
        check(pixelAt(pixels, 14, 150) == TiaPalette.ARGB[0x20],
                "RESMP0 locks missile to player center");
        check(pixelAt(pixels, 30, 150) == TiaPalette.ARGB[0],
                "RESMP0 removes missile from its old position");
    }

    private static void objectSaveRestore() {
        Tia original = new Tia();
        positionAt(original, Tia.RESP0, 10);
        Bus registers = original.registers();
        registers.write(Tia.COLUP0, 0x20);
        registers.write(Tia.NUSIZ0, 3);
        registers.write(Tia.GRP0, 0x80);
        finishFrame(original);

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);
        Tia restored = new Tia();
        restored.restore(new BinaryStateReader(writer.toByteArray()));

        original.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        restored.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        check(Arrays.equals(original.framebufferArgb(), restored.framebufferArgb()),
                "restored player and missile state renders identically");
        check(pixelAt(restored.framebufferArgb(), 26, 150) == TiaPalette.ARGB[0x20],
                "restored player keeps NUSIZ copies");
    }

    private static void hmoveOffsets() {
        Tia right = new Tia();
        positionAt(right, Tia.RESP0, 10);
        right.registers().write(Tia.COLUP0, 0x20);
        right.registers().write(Tia.GRP0, 0xFF);
        right.registers().write(Tia.HMP0, 0x10);
        right.registers().write(Tia.HMOVE, 0);
        finishFrame(right);
        check(pixelAt(right.framebufferArgb(), 11, 150) == TiaPalette.ARGB[0x20],
                "HMOVE +1 shifts player right");
        check(pixelAt(right.framebufferArgb(), 10, 150) == TiaPalette.ARGB[0],
                "HMOVE +1 clears old player position");

        Tia left = new Tia();
        positionAt(left, Tia.RESP0, 10);
        left.registers().write(Tia.COLUP0, 0x20);
        left.registers().write(Tia.GRP0, 0xFF);
        left.registers().write(Tia.HMP0, 0xF0);
        left.registers().write(Tia.HMOVE, 0);
        finishFrame(left);
        check(pixelAt(left.framebufferArgb(), 9, 150) == TiaPalette.ARGB[0x20],
                "HMOVE -1 shifts player left");
        check(pixelAt(left.framebufferArgb(), 17, 150) == TiaPalette.ARGB[0],
                "HMOVE -1 clears old player position");

        Tia zero = new Tia();
        positionAt(zero, Tia.RESP0, 10);
        zero.registers().write(Tia.COLUP0, 0x20);
        zero.registers().write(Tia.GRP0, 0xFF);
        zero.registers().write(Tia.HMOVE, 0);
        finishFrame(zero);
        check(pixelAt(zero.framebufferArgb(), 10, 150) == TiaPalette.ARGB[0x20],
                "HMOVE with zero motion keeps player position");

        Tia wrap = new Tia();
        positionAt(wrap, Tia.RESP0, 159);
        wrap.registers().write(Tia.COLUP0, 0x20);
        wrap.registers().write(Tia.GRP0, 0xFF);
        wrap.registers().write(Tia.HMP0, 0x10);
        wrap.registers().write(Tia.HMOVE, 0);
        finishFrame(wrap);
        check(pixelAt(wrap.framebufferArgb(), 0, 150) == TiaPalette.ARGB[0x20],
                "HMOVE wraps player around right edge");
        check(pixelAt(wrap.framebufferArgb(), 159, 150) == TiaPalette.ARGB[0],
                "HMOVE wrap clears old right-edge position");
    }

    private static void hmoveAllObjects() {
        Tia tia = new Tia();
        positionAt(tia, Tia.RESP0, 10);
        positionAt(tia, Tia.RESP1, 20);
        positionAt(tia, Tia.RESM0, 30);
        positionAt(tia, Tia.RESM1, 40);
        positionAt(tia, Tia.RESBL, 50);
        Bus registers = tia.registers();
        registers.write(Tia.COLUP0, 0x20);
        registers.write(Tia.COLUP1, 0x40);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.GRP0, 0x80);
        registers.write(Tia.GRP1, 0x80);
        registers.write(Tia.ENAM0, 0x02);
        registers.write(Tia.ENAM1, 0x02);
        registers.write(Tia.ENABL, 0x02);
        registers.write(Tia.HMP0, 0x10);
        registers.write(Tia.HMP1, 0x20);
        registers.write(Tia.HMM0, 0x30);
        registers.write(Tia.HMM1, 0x40);
        registers.write(Tia.HMBL, 0x50);
        registers.write(Tia.HMOVE, 0);
        finishFrame(tia);

        int[] pixels = tia.framebufferArgb();
        check(pixelAt(pixels, 11, 150) == TiaPalette.ARGB[0x20],
                "HMOVE shifts player 0");
        check(pixelAt(pixels, 22, 150) == TiaPalette.ARGB[0x40],
                "HMOVE shifts player 1");
        check(pixelAt(pixels, 33, 150) == TiaPalette.ARGB[0x20],
                "HMOVE shifts missile 0");
        check(pixelAt(pixels, 44, 150) == TiaPalette.ARGB[0x40],
                "HMOVE shifts missile 1");
        check(pixelAt(pixels, 55, 150) == TiaPalette.ARGB[0x0F],
                "HMOVE shifts ball");
    }

    private static void hmoveClear() {
        Tia tia = new Tia();
        positionAt(tia, Tia.RESP0, 10);
        positionAt(tia, Tia.RESP1, 20);
        Bus registers = tia.registers();
        registers.write(Tia.COLUP0, 0x20);
        registers.write(Tia.COLUP1, 0x40);
        registers.write(Tia.GRP0, 0x80);
        registers.write(Tia.GRP1, 0x80);
        registers.write(Tia.HMP0, 0x10);
        registers.write(Tia.HMP1, 0x20);
        registers.write(Tia.HMM0, 0x30);
        registers.write(Tia.HMM1, 0x40);
        registers.write(Tia.HMBL, 0x50);
        registers.write(Tia.HMCLR, 0);
        registers.write(Tia.HMOVE, 0);
        finishFrame(tia);

        check(pixelAt(tia.framebufferArgb(), 10, 150) == TiaPalette.ARGB[0x20],
                "HMCLR clears player 0 motion");
        check(pixelAt(tia.framebufferArgb(), 20, 150) == TiaPalette.ARGB[0x40],
                "HMCLR clears player 1 motion");
        check(pixelAt(tia.framebufferArgb(), 11, 150) == TiaPalette.ARGB[0],
                "HMCLR leaves old shifted position empty");
    }

    private static void hmoveLateAndSaveRestore() {
        Tia late = new Tia();
        positionAt(late, Tia.RESP0, 10);
        late.registers().write(Tia.COLUP0, 0x20);
        late.registers().write(Tia.GRP0, 0xFF);
        late.tick(100);
        late.registers().write(Tia.HMP0, 0x10);
        late.registers().write(Tia.HMOVE, 0);
        finishFrame(late);
        check(pixelAt(late.framebufferArgb(), 11, 150) == TiaPalette.ARGB[0x20],
                "late HMOVE still shifts player");

        Tia original = new Tia();
        positionAt(original, Tia.RESP0, 10);
        original.registers().write(Tia.COLUP0, 0x20);
        original.registers().write(Tia.GRP0, 0xFF);
        original.registers().write(Tia.HMP0, 0x10);
        original.registers().write(Tia.HMOVE, 0);
        finishFrame(original);

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);
        Tia restored = new Tia();
        restored.restore(new BinaryStateReader(writer.toByteArray()));
        finishFrame(original);
        finishFrame(restored);

        check(Arrays.equals(original.framebufferArgb(), restored.framebufferArgb()),
                "restored HMOVE state renders identically");
        check(pixelAt(restored.framebufferArgb(), 11, 150) == TiaPalette.ARGB[0x20],
                "restored HMOVE keeps shifted position");
    }

    private static void collisions() {
        Tia tia = collisionTia(0);
        finishFrame(tia);
        Bus registers = tia.registers();

        check(registers.read(Tia.CXM0P) == 0xC0, "CXM0P detects M0 with P0/P1");
        check(registers.read(Tia.CXM1P) == 0xC0, "CXM1P detects M1 with P0/P1");
        check(registers.read(Tia.CXP0FB) == 0xC0, "CXP0FB detects P0 with PF/BL");
        check(registers.read(Tia.CXP1FB) == 0xC0, "CXP1FB detects P1 with PF/BL");
        check(registers.read(Tia.CXM0FB) == 0xC0, "CXM0FB detects M0 with PF/BL");
        check(registers.read(Tia.CXM1FB) == 0xC0, "CXM1FB detects M1 with PF/BL");
        check(registers.read(Tia.CXBLPF) == 0x80, "CXBLPF detects ball with PF");
        check(registers.read(Tia.CXPPMM) == 0xC0, "CXPPMM detects players and missiles");
        check(registers.read(Tia.CXPPMM) == 0xC0, "collision latch survives repeated read");
    }

    private static void collisionClearAndVblank() {
        Tia tia = collisionTia(0);
        finishFrame(tia);
        tia.registers().write(Tia.CXCLR, 0);
        check(tia.registers().read(Tia.CXM0P) == 0, "CXCLR clears M0/P latches");
        check(tia.registers().read(Tia.CXP0FB) == 0, "CXCLR clears P0/FB latches");
        check(tia.registers().read(Tia.CXBLPF) == 0, "CXCLR clears ball/PF latch");
        check(tia.registers().read(Tia.CXPPMM) == 0, "CXCLR clears P/P and M/M latches");

        Tia blanked = collisionTia(0x02);
        finishFrame(blanked);
        check(blanked.registers().read(Tia.CXPPMM) == 0, "VBLANK suppresses collisions");
        check(blanked.registers().read(Tia.CXBLPF) == 0, "VBLANK suppresses ball collision");
    }

    private static void tiaInput() {
        Tia tia = new Tia();
        SimpleController player0 = new SimpleController();
        SimpleController player1 = new SimpleController();
        tia.connectPlayer(0, player0);
        tia.connectPlayer(1, player1);
        Bus registers = tia.registers();

        check(registers.read(Tia.INPT0) == 0x80, "paddle 0 is released");
        check(registers.read(Tia.INPT1) == 0x80, "paddle 1 is released");
        check(registers.read(Tia.INPT2) == 0x80, "paddle 2 is released");
        check(registers.read(Tia.INPT3) == 0x80, "paddle 3 is released");
        check(registers.read(Tia.INPT4) == 0x80, "player 0 fire starts released");
        check(registers.read(Tia.INPT5) == 0x80, "player 1 fire starts released");

        player0.press(Button.A);
        check(registers.read(Tia.INPT4) == 0, "player 0 fire pressed is low");
        check(registers.read(Tia.INPT5) == 0x80, "player 0 fire does not affect player 1");
        player0.release(Button.A);
        check(registers.read(Tia.INPT4) == 0x80, "player 0 fire release is high");

        player1.press(Button.A);
        check(registers.read(Tia.INPT5) == 0, "player 1 fire pressed is low");
        player1.release(Button.A);
    }

    private static void machineControllerWiring() {
        Atari2600Machine machine = new Atari2600Machine();
        SimpleController controller = new SimpleController();
        machine.connectController(0, controller);
        controller.press(Button.A);
        check(machine.tia().registers().read(Tia.INPT4) == 0,
                "machine routes player 0 fire to TIA");
        controller.release(Button.A);
        check(machine.tia().registers().read(Tia.INPT4) == 0x80,
                "machine routes player 0 fire release to TIA");
    }

    private static void midFrameSaveRestore() {
        Tia original = collisionTia(0);
        original.tick(5L * Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE);

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);
        Tia restored = new Tia();
        restored.restore(new BinaryStateReader(writer.toByteArray()));

        check(restored.scanline() == original.scanline(), "restore TIA mid-frame scanline");
        check(restored.colorClock() == original.colorClock(), "restore TIA mid-frame color clock");
        check(restored.registers().read(Tia.CXPPMM) == 0xC0,
                "restore TIA collision latches");
        check(restored.registers().read(Tia.CXBLPF) == 0x80,
                "restore TIA ball collision latch");
        restored.registers().write(Tia.CXCLR, 0);
        check(restored.registers().read(Tia.CXP0FB) == 0,
                "clearing restored collisions works");
        check(original.registers().read(Tia.CXP0FB) == 0xC0,
                "clearing restored collisions leaves original untouched");

        finishFrame(original);
        finishFrame(restored);
        check(pixelAt(restored.framebufferArgb(), 10, 150) == TiaPalette.ARGB[0x20],
                "restored TIA continues rendering objects");
        check(pixelAt(original.framebufferArgb(), 10, 150)
                        == pixelAt(restored.framebufferArgb(), 10, 150),
                "restored TIA renders same object color");
        check(restored.frameCount() == original.frameCount(),
                "restored TIA keeps frame count");
    }

    private static Tia collisionTia(int vblank) {
        Tia tia = new Tia();
        positionAt(tia, Tia.RESP0, 10);
        positionAt(tia, Tia.RESP1, 10);
        positionAt(tia, Tia.RESM0, 10);
        positionAt(tia, Tia.RESM1, 10);
        positionAt(tia, Tia.RESBL, 10);
        Bus registers = tia.registers();
        registers.write(Tia.VBLANK, vblank);
        registers.write(Tia.COLUP0, 0x20);
        registers.write(Tia.COLUP1, 0x40);
        registers.write(Tia.COLUPF, 0x0F);
        registers.write(Tia.COLUBK, 0x08);
        registers.write(Tia.CTRLPF, 0);
        registers.write(Tia.PF0, 0x40);
        registers.write(Tia.GRP0, 0xFF);
        registers.write(Tia.GRP1, 0xFF);
        registers.write(Tia.ENAM0, 0x02);
        registers.write(Tia.ENAM1, 0x02);
        registers.write(Tia.ENABL, 0x02);
        return tia;
    }

    private static void positionAt(Tia tia, int register, int x) {
        long target = (long) (Atari2600Constants.VISIBLE_SCANLINE_START + 80)
                * Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE
                + Atari2600Constants.VISIBLE_COLOR_CLOCK_START + x;
        long used = (long) tia.scanline() * Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE
                + tia.colorClock();
        if (target < used) {
            throw new IllegalStateException("cannot move TIA backwards");
        }
        tia.tick(target - used);
        tia.registers().write(register, 0);
    }

    private static void finishFrame(Tia tia) {
        long used = (long) tia.scanline() * Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE
                + tia.colorClock();
        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME - used);
    }

    private static int pixelAt(int[] pixels, int x, int y) {
        return pixels[y * Atari2600Constants.VISIBLE_WIDTH + x];
    }

    private static void lineBoundary() {
        Tia tia = new Tia();
        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE - 1);
        check(tia.scanline() == 0, "line boundary: last clock stays on line 0");
        check(tia.colorClock() == 227, "line boundary: color clock 227");
        tia.tick(1);
        check(tia.scanline() == 1, "line boundary: next tick starts line 1");
        check(tia.colorClock() == 0, "line boundary: color clock resets");
    }

    private static void frameDelivery() {
        Tia tia = new Tia();
        List<VideoFrame> frames = new ArrayList<>();
        tia.connect(frames::add);

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME - 1);
        check(frames.isEmpty(), "no frame before full frame");
        check(tia.frameCount() == 0, "frame count before full frame");
        check(tia.scanline() == 261, "last scanline before frame end");
        check(tia.colorClock() == 227, "last color clock before frame end");

        tia.tick(1);
        check(frames.size() == 1, "exactly one frame per full frame");
        check(tia.frameCount() == 1, "frame count after full frame");
        check(tia.scanline() == 0, "frame ends at scanline 0");
        check(tia.colorClock() == 0, "frame ends at color clock 0");

        VideoFrame frame = frames.get(0);
        check(frame.width() == 160 && frame.height() == 192, "frame is 160x192");
        check(frame.pixels().length == 160 * 192, "frame pixel count");
        check(frame.pixels()[0] == 0xFF000000, "empty TIA frame is black");

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        check(frames.size() == 2, "second full frame delivered");
        check(tia.frameCount() == 2, "second frame count");
    }

    private static void wsyncStopsCpu() {
        Tia tia = new Tia();
        Bus registers = tia.registers();
        registers.write(Tia.WSYNC, 0);
        check(tia.cpuHalted(), "WSYNC write halts CPU");

        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE - 1);
        check(tia.scanline() == 0, "WSYNC keeps current scanline");
        check(tia.colorClock() == 227, "WSYNC keeps last color clock");
        check(tia.cpuHalted(), "CPU still halted before line end");

        tia.tick(1);
        check(tia.scanline() == 1, "WSYNC releases at next line");
        check(tia.colorClock() == 0, "WSYNC releases at color clock 0");
        check(!tia.cpuHalted(), "CPU released after line end");
    }

    private static void syncRegisters() {
        Tia tia = new Tia();
        Bus registers = tia.registers();

        registers.write(Tia.VSYNC, 0x02);
        check(tia.vsync(), "VSYNC bit set");
        check(registers.read(Tia.VSYNC) == 0x02, "VSYNC readable");
        registers.write(Tia.VSYNC, 0);
        check(!tia.vsync(), "VSYNC bit cleared");

        registers.write(Tia.VBLANK, 0x02);
        check(tia.vblank(), "VBLANK bit set");
        check(registers.read(Tia.VBLANK) == 0x02, "VBLANK readable");
        registers.write(Tia.VBLANK, 0);
        check(!tia.vblank(), "VBLANK bit cleared");
    }

    private static void resetState() {
        Tia tia = new Tia();
        tia.tick(500);
        Bus registers = tia.registers();
        registers.write(Tia.VSYNC, 0x02);
        registers.write(Tia.WSYNC, 0);
        tia.reset();
        check(tia.scanline() == 0, "reset scanline");
        check(tia.colorClock() == 0, "reset color clock");
        check(!tia.vsync(), "reset VSYNC");
        check(!tia.cpuHalted(), "reset WSYNC");
        check(tia.frameCount() == 0, "reset frame count");
    }

    private static void saveRestore() {
        Tia original = new Tia();
        original.tick(500);
        Bus registers = original.registers();
        registers.write(Tia.VSYNC, 0x02);
        registers.write(Tia.VBLANK, 0x02);
        registers.write(Tia.WSYNC, 0);

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);

        Tia restored = new Tia();
        restored.restore(new BinaryStateReader(writer.toByteArray()));
        check(restored.scanline() == original.scanline(), "restore scanline");
        check(restored.colorClock() == original.colorClock(), "restore color clock");
        check(restored.vsync(), "restore VSYNC");
        check(restored.vblank(), "restore VBLANK");
        check(restored.cpuHalted(), "restore WSYNC");
        check(restored.frameCount() == original.frameCount(), "restore frame count");

        restored.tick(Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE - restored.colorClock());
        check(restored.scanline() == original.scanline() + 1, "restored TIA continues");
        check(!restored.cpuHalted(), "restored WSYNC releases");
    }

    private static void machineRunFrame() {
        Atari2600Machine machine = new Atari2600Machine();
        List<VideoFrame> frames = new ArrayList<>();
        machine.connectVideo(frames::add);

        machine.runFrame();
        check(frames.size() == 1, "machine runFrame delivers one frame");
        check(frames.get(0).width() == 160 && frames.get(0).height() == 192,
                "machine frame dimensions");
        machine.runFrame();
        check(frames.size() == 2, "machine second runFrame delivers one frame");
    }

    private static void negativeTickRejected() {
        try {
            new Tia().tick(-1);
            check(false, "negative TIA tick rejected");
        } catch (IllegalArgumentException e) {
            check(true, "negative TIA tick rejected");
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }

    private AtariTiaTests() {
    }
}
