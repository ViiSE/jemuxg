package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.hardware.video.tia.StellaTia;

/** JUnit-free checks for the isolated StellaTia core. */
public final class StellaTiaTests {

    private static int checks;

    public static void main(String[] args) {
        timing();
        vblankDelay();
        respTargets();
        playfieldDelay();
        playfieldVisible();
        System.out.println("Stella TIA core tests OK, checks=" + checks);
    }

    private static void timing() {
        StellaTia tia = new StellaTia();
        check(tia.colorClock() == 0 && tia.hctr() == 0, "starts at clock 0");
        check(tia.hblank(), "starts in hblank");
        tia.tick(68);
        check(!tia.hblank(), "visible starts after 68 clocks");
        tia.tick(160);
        check(tia.scanline() == 1 && tia.hctr() == 0, "line wraps after 228 clocks");
        check(tia.colorClock() == 228, "absolute clock advanced by 228");
        tia.tick(228 * StellaTia.SCANLINES);
        check(tia.frame() == 1, "frame advances after 262 lines");
    }

    private static void vblankDelay() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.VBLANK, 0x02);
        check(!tia.vblank(), "vblank is delayed by one clock");
        tia.tick(2);
        check(tia.vblank(), "vblank becomes active on next clock");
    }

    private static void respTargets() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.RESP0, 0);
        check(tia.player0().counter() == 159, "RESP in hblank targets 159");
        tia.tick(68);
        tia.write(StellaTia.RESP0, 0);
        check(tia.player0().counter() == 157, "RESP in visible targets 157");
        tia.tick(5);
        tia.write(StellaTia.RESP1, 0);
        check(tia.player1().counter() == 157, "RESP1 in visible targets 157");
    }

    private static void playfieldDelay() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.PF0, 0x10);
        check(tia.playfield().pf0() == 0, "PF0 write is delayed by two clocks");
        tia.tick(2);
        check(tia.playfield().pf0() == 0, "PF0 still not applied after two clocks");
        tia.tick(1);
        check(tia.playfield().pf0() == 1, "PF0 applies on the third clock");
    }

    private static void playfieldVisible() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.PF0, 0x10);
        tia.tick(69);
        check(tia.playfield().x() == 0, "playfield starts at x=0 on the first visible clock");
        check(tia.playfield().isOn(), "playfield renders PF0 bit 0 at x=0");
        tia.tick(4);
        check(!tia.playfield().isOn(), "playfield releases the pixel at x=4");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
