package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.hardware.video.tia.StellaTia;
import ru.viise.jemuxg.hardware.video.tia.TiaPalette;

/** JUnit-free checks for the isolated StellaTia framebuffer and priorities. */
public final class StellaTiaFrameTests {

    private static int checks;

    public static void main(String[] args) {
        dimensions();
        background();
        vblank();
        normalPriority();
        playfieldPriority();
        scoreColors();
        frameClear();
        argbMatchesPalette();
        System.out.println("Stella TIA frame tests OK, checks=" + checks);
    }

    private static void dimensions() {
        StellaTia tia = new StellaTia();
        check(tia.width() == 160, "framebuffer width is 160");
        check(tia.height() == 192, "framebuffer height is 192");
        check(tia.framebufferIndices().length == 160 * 192,
                "index framebuffer is 160x192");
        check(tia.framebufferArgb().length == 160 * 192,
                "ARGB framebuffer is 160x192");
    }

    private static void background() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.COLUBK, 0x1F);
        advanceToLine(tia, 40);
        tia.tick(1);

        int index = tia.framebufferIndices()[0];
        check(index == 0x0F, "COLUBK is masked and rendered as background");
    }

    private static void vblank() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.COLUBK, 0x1E);
        tia.write(StellaTia.VBLANK, 0x02);
        tia.tick(2);
        advanceToLine(tia, 40);
        tia.tick(16);

        boolean black = true;
        for (int x = 0; x < 16; x++) {
            black &= tia.framebufferIndices()[x] == 0;
        }
        check(black, "VBLANK renders black pixels");
    }

    private static void normalPriority() {
        StellaTia tia = prepare(0x00);
        tia.tick(40);

        check(tia.framebufferIndices()[30] == color(0x1E),
                "playfield is drawn over the background");
        check(tia.framebufferIndices()[16] == color(0x44),
                "player 0 is drawn over the playfield");
    }

    private static void playfieldPriority() {
        StellaTia tia = prepare(0x04);
        tia.tick(20);

        check(tia.framebufferIndices()[16] == color(0x1E),
                "CTRLPF D2 gives the playfield priority over player 0");
    }

    private static void scoreColors() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.COLUBK, 0x00);
        tia.write(StellaTia.COLUP0, 0x44);
        tia.write(StellaTia.COLUP1, 0x84);
        tia.write(StellaTia.COLUPF, 0x1E);
        tia.write(StellaTia.CTRLPF, 0x02);
        tia.playfield().pf0(0xFF);
        tia.playfield().pf1(0xFF);
        tia.playfield().pf2(0xFF);
        advanceToLine(tia, 40);
        tia.tick(160);

        check(tia.framebufferIndices()[0] == color(0x44),
                "score mode uses COLUP0 on the left");
        check(tia.framebufferIndices()[80] == color(0x84),
                "score mode uses COLUP1 on the right");
        check(tia.framebufferIndices()[159] == color(0x84),
                "score mode right half reaches x=159");
    }

    private static void frameClear() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.COLUBK, 0x1E);
        advanceToLine(tia, 40);
        tia.tick(1);
        check(tia.framebufferIndices()[0] != 0, "frame contains rendered pixels");

        long frameLength = (long) StellaTia.H_CLOCKS * StellaTia.SCANLINES;
        long nextFrame = (tia.colorClock() / frameLength + 1) * frameLength;
        tia.tick((int) (nextFrame - tia.colorClock() + 1));
        check(tia.framebufferIndices()[0] == 0,
                "framebuffer is cleared at the start of the next frame");
    }

    private static void argbMatchesPalette() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.COLUBK, 0x1E);
        advanceToLine(tia, 40);
        tia.tick(1);

        int index = tia.framebufferIndices()[0];
        check(tia.framebufferArgb()[0] == TiaPalette.ARGB[index],
                "ARGB framebuffer uses the TIA palette");
    }

    private static StellaTia prepare(int ctrlpf) {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.VBLANK, 0x00);
        tia.tick(2);
        tia.write(StellaTia.COLUBK, 0x00);
        tia.write(StellaTia.COLUP0, 0x44);
        tia.write(StellaTia.COLUP1, 0x84);
        tia.write(StellaTia.COLUPF, 0x1E);
        tia.write(StellaTia.CTRLPF, ctrlpf);
        tia.playfield().pf1(0xFF);
        advanceToLine(tia, 40);
        tia.player0().setCounter(150);
        tia.player0().grp(0xFF);
        return tia;
    }

    private static void advanceToLine(StellaTia tia, int line) {
        long target = (long) line * StellaTia.H_CLOCKS + StellaTia.H_BLANK;
        if (tia.colorClock() < target) {
            tia.tick((int) (target - tia.colorClock()));
        }
    }

    private static int color(int value) {
        return (value >> 1) & 0x7F;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
