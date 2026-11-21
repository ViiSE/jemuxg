package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.hardware.video.tia.StellaTia;

/** JUnit-free checks for HMOVE timing and sprite movement. */
public final class StellaTiaHmoveTests {

    private static int checks;

    public static void main(String[] args) {
        delay();
        playerMovement();
        extendedHblank();
        comb();
        lateHmove();
        missileMovement();
        ballMovement();
        hmclr();
        System.out.println("Stella TIA hmove tests OK, checks=" + checks);
    }

    private static void delay() {
        StellaTia tia = new StellaTia();
        tia.player0().setCounter(150);
        tia.player0().hmp(0);
        tia.write(StellaTia.HMOVE, 0);
        tia.tick(6);
        check(tia.player0().counter() == 150,
                "HMOVE is not applied before six clocks");
        check(!tia.extendedHblank(),
                "HMOVE has not extended HBLANK before the delay expires");
        tia.tick(1);
        check(tia.extendedHblank(), "HMOVE extends HBLANK");
        check(tia.player0().counter() == 150,
                "no movement before the next four-clock boundary");
        tia.tick(2);
        check(tia.player0().counter() == 151,
                "first movement tick lands on hctr 8");
    }

    private static void playerMovement() {
        check(movedPlayerCounter(0x00) == 158,
                "HMP 0x00 moves the player eight clocks");
        check(movedPlayerCounter(0x70) == 5,
                "HMP 0x70 moves the player fifteen clocks");
        check(movedPlayerCounter(0x80) == 150,
                "HMP 0x80 leaves the player in place");
    }

    private static void extendedHblank() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.HMOVE, 0);
        tia.tick(67);
        check(tia.hblank() && tia.extendedHblank(),
                "extended HBLANK is active at hctr 67");
        tia.tick(1);
        check(tia.hblank(), "extended HBLANK keeps hctr 68 blank");
        tia.tick(7);
        check(tia.hblank(), "extended HBLANK keeps hctr 75 blank");
        tia.tick(1);
        check(!tia.hblank() && tia.hctr() == 76,
                "visible frame starts at hctr 76 with extended HBLANK");
    }

    private static void comb() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.VBLANK, 0);
        tia.write(StellaTia.COLUPF, 0x1E);
        tia.playfield().pf0(0xF0);
        tia.tick(40 * StellaTia.H_CLOCKS + 60);
        tia.write(StellaTia.HMOVE, 0);
        tia.tick(7);
        tia.tick(9);
        tia.tick(1);
        byte[] frame = tia.framebufferIndices();
        check(frame[0] == 0, "HMOVE comb clears pixel 0");
        check(frame[8] == 0x0F, "first visible pixel after HMOVE is x=8");
    }

    private static void lateHmove() {
        StellaTia tia = new StellaTia();
        tia.player0().hmp(0);
        tia.tick(40 * StellaTia.H_CLOCKS + 100);
        tia.player0().setCounter(150);
        tia.write(StellaTia.HMOVE, 0);
        tia.tick(6);
        tia.tick(1);
        check(tia.extendedHblank(),
                "late HMOVE sets the extension latch");
        tia.tick(3);
        check(tia.player0().counter() == 0,
                "visible movement tick does not add counter movement");
        tia.tick(10);
        check(tia.player0().counter() == 10,
                "late HMOVE does not add movement outside HBLANK");
        tia.tick(228 - 120);
        tia.tick(1);
        check(!tia.extendedHblank(),
                "extended HBLANK latch clears at the next line start");
        check(!tia.movementInProgress(), "movement finishes during the line");
    }

    private static void missileMovement() {
        check(movedMissileCounter(0x00) == 158,
                "HMM 0x00 moves the missile eight clocks");
        check(movedMissileCounter(0x70) == 5,
                "HMM 0x70 moves the missile fifteen clocks");
        check(movedMissileCounter(0x80) == 150,
                "HMM 0x80 leaves the missile in place");
    }

    private static void ballMovement() {
        check(movedBallCounter(0x00) == 158,
                "HMBL 0x00 moves the ball eight clocks");
        check(movedBallCounter(0x70) == 5,
                "HMBL 0x70 moves the ball fifteen clocks");
        check(movedBallCounter(0x80) == 150,
                "HMBL 0x80 leaves the ball in place");
    }

    private static void hmclr() {
        StellaTia tia = new StellaTia();
        tia.player0().setCounter(150);
        tia.missile0().setCounter(150);
        tia.ball().setCounter(150);
        tia.player0().hmp(0x70);
        tia.missile0().hmm(0x70);
        tia.ball().hmbl(0x70);
        tia.write(StellaTia.HMCLR, 0);
        tia.write(StellaTia.HMOVE, 0);
        tia.tick(66);
        check(tia.player0().counter() == 158,
                "HMCLR resets player movement to the default eight clocks");
        check(tia.missile0().counter() == 158,
                "HMCLR resets missile movement to the default eight clocks");
        check(tia.ball().counter() == 158,
                "HMCLR resets ball movement to the default eight clocks");
    }

    private static int movedPlayerCounter(int hmp) {
        StellaTia tia = new StellaTia();
        tia.player0().setCounter(150);
        tia.player0().hmp(hmp);
        tia.write(StellaTia.HMOVE, 0);
        tia.tick(66);
        return tia.player0().counter();
    }

    private static int movedMissileCounter(int hmm) {
        StellaTia tia = new StellaTia();
        tia.missile0().setCounter(150);
        tia.missile0().hmm(hmm);
        tia.write(StellaTia.HMOVE, 0);
        tia.tick(66);
        return tia.missile0().counter();
    }

    private static int movedBallCounter(int hmbl) {
        StellaTia tia = new StellaTia();
        tia.ball().setCounter(150);
        tia.ball().hmbl(hmbl);
        tia.write(StellaTia.HMOVE, 0);
        tia.tick(66);
        return tia.ball().counter();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
