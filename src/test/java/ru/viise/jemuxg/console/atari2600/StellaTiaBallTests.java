package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.hardware.video.tia.StellaTia;
import ru.viise.jemuxg.hardware.video.tia.StellaTiaBall;

/** JUnit-free checks for the isolated Stella-style Ball port. */
public final class StellaTiaBallTests {

    private static int checks;

    public static void main(String[] args) {
        widths();
        enabled();
        vdel();
        resblTargets();
        hmblFormula();
        registerWiring();
        System.out.println("Stella TIA ball tests OK, checks=" + checks);
    }

    private static void widths() {
        width(0x00, 1);
        width(0x10, 2);
        width(0x20, 4);
        width(0x30, 8);
    }

    private static void width(int sizeBits, int expected) {
        StellaTiaBall b = new StellaTiaBall();
        b.ctrlpf(sizeBits);
        b.enabl(0x02);
        b.resbl(157, false);
        check(countOn(b, 20) == expected, "ball CTRLPF width bits "
                + (sizeBits >>> 4) + " produces " + expected + " pixels");
    }

    private static void enabled() {
        StellaTiaBall b = new StellaTiaBall();
        b.ctrlpf(0);
        b.resbl(157, false);
        check(countOn(b, 20) == 0, "ball is invisible without ENABL");

        b.enabl(0x02);
        b.resbl(157, false);
        check(countOn(b, 20) == 1, "ENABL enables ball output");
    }

    private static void vdel() {
        StellaTiaBall hidden = new StellaTiaBall();
        hidden.ctrlpf(0);
        hidden.vdelbl(0x01);
        hidden.enabl(0x02);
        hidden.resbl(157, false);
        check(countOn(hidden, 20) == 0,
                "VDELBL keeps the old disabled state until shuffle");

        StellaTiaBall shown = new StellaTiaBall();
        shown.ctrlpf(0);
        shown.vdelbl(0x01);
        shown.enabl(0x02);
        shown.resbl(157, false);
        shown.shuffleStatus();
        check(countOn(shown, 20) == 1,
                "VDELBL shuffle latches ENABL into the visible state");
    }

    private static void resblTargets() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.RESBL, 0);
        check(tia.ball().counter() == 159, "RESBL in hblank targets 159");

        StellaTia visible = new StellaTia();
        visible.tick(68);
        visible.write(StellaTia.RESBL, 0);
        check(visible.ball().counter() == 157, "RESBL in visible targets 157");
    }

    private static void hmblFormula() {
        check(hmbl(0x00) == 8, "HMBL 0x00 means eight movement clocks");
        check(hmbl(0x80) == 0, "HMBL 0x80 means zero movement clocks");
        check(hmbl(0x70) == 15, "HMBL 0x70 means fifteen movement clocks");
    }

    private static int hmbl(int value) {
        StellaTiaBall b = new StellaTiaBall();
        b.hmbl(value);
        return b.hmmClocks();
    }

    private static void registerWiring() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.CTRLPF, 0x23);
        tia.write(StellaTia.COLUPF, 0xAB);
        check(tia.ball().width() == 4, "CTRLPF sets ball width");
        check(tia.playfield().pf2() == 0 && tia.ball().color() == 0xAA,
                "COLUPF sets ball color with bit 0 masked");

        tia.write(StellaTia.VDELBL, 0x01);
        tia.write(StellaTia.ENABL, 0x02);
        check(!tia.ball().isEnabled(), "ENABL is delayed by one clock");
        tia.tick(2);
        check(!tia.ball().isEnabled(), "VDELBL holds ENABL until GRP1 shuffle");
        tia.write(StellaTia.GRP1, 0);
        tia.tick(3);
        check(tia.ball().isEnabled(), "GRP1 shuffle latches ENABL with VDELBL");
    }

    private static int countOn(StellaTiaBall b, int clocks) {
        int count = 0;
        for (int i = 0; i < clocks; i++) {
            b.tick();
            if (b.isOn()) {
                count++;
            }
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
