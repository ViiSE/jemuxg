package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.hardware.video.tia.StellaTia;
import ru.viise.jemuxg.hardware.video.tia.StellaTiaMissile;

/** JUnit-free checks for the isolated Stella-style Missile port. */
public final class StellaTiaMissileTests {

    private static int checks;

    public static void main(String[] args) {
        widths();
        enabled();
        resmp();
        copies();
        resmTargets();
        resmDuringDraw();
        hmmFormula();
        registerWiring();
        System.out.println("Stella TIA missile tests OK, checks=" + checks);
    }

    private static void widths() {
        width(0x00, 1);
        width(0x10, 2);
        width(0x20, 4);
        width(0x30, 8);
    }

    private static void width(int sizeBits, int expected) {
        StellaTiaMissile m = new StellaTiaMissile();
        m.nusiz(sizeBits);
        m.enam(0x02);
        m.setCounter(150);
        int on = countOn(m, 30);
        check(on == expected, "missile width bits "
                + (sizeBits >>> 4) + " produces " + expected + " pixels");
    }

    private static void enabled() {
        StellaTiaMissile m = new StellaTiaMissile();
        m.nusiz(0);
        m.setCounter(150);
        check(countOn(m, 30) == 0, "missile is invisible without ENAM");

        StellaTiaMissile enabled = new StellaTiaMissile();
        enabled.nusiz(0);
        enabled.enam(0x02);
        enabled.setCounter(150);
        check(countOn(enabled, 30) == 1, "ENAM enables missile output");
    }

    private static void resmp() {
        StellaTiaMissile m = new StellaTiaMissile();
        m.nusiz(0);
        m.enam(0x02);
        m.resmp(0x02);
        m.setCounter(150);
        check(countOn(m, 30) == 0, "RESMP forces missile invisible");

        m.resmp(0x00);
        m.setCounter(150);
        check(countOn(m, 30) == 1, "clearing RESMP restores missile output");
    }

    private static void copies() {
        StellaTiaMissile m = new StellaTiaMissile();
        m.nusiz(3);
        m.enam(0x02);
        m.setCounter(0);
        check(countOn(m, 165) == 3, "NUSIZ 3 produces three missile copies");

        StellaTiaMissile wide = new StellaTiaMissile();
        wide.nusiz(0x13);
        wide.enam(0x02);
        wide.setCounter(0);
        check(countOn(wide, 165) == 6, "NUSIZ 3 with width 2 produces six pixels");
    }

    private static void resmTargets() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.RESM0, 0);
        check(tia.missile0().counter() == 159, "RESM0 in hblank targets 159");

        StellaTia visible = new StellaTia();
        visible.tick(68);
        visible.write(StellaTia.RESM0, 0);
        check(visible.missile0().counter() == 157, "RESM0 in visible targets 157");
    }

    private static void resmDuringDraw() {
        StellaTiaMissile m = new StellaTiaMissile();
        m.nusiz(0x20);
        m.enam(0x02);
        m.setCounter(150);
        for (int i = 0; i < 12; i++) {
            m.tick(150 + i);
        }
        check(m.renderCounter() == 1, "missile render counter reached draw pixel 1");
        m.resm(159, false, false);
        check(m.counter() == 159, "RESM sets missile counter during draw");
        check(m.renderCounter() == 2,
                "RESM restarts render counter for width 4 during draw");
    }

    private static void hmmFormula() {
        check(hmm(0x00) == 8, "HMM 0x00 means eight movement clocks");
        check(hmm(0x80) == 0, "HMM 0x80 means zero movement clocks");
        check(hmm(0x70) == 15, "HMM 0x70 means fifteen movement clocks");
    }

    private static int hmm(int value) {
        StellaTiaMissile m = new StellaTiaMissile();
        m.hmm(value);
        return m.hmmClocks();
    }

    private static void registerWiring() {
        StellaTia tia = new StellaTia();
        tia.write(StellaTia.NUSIZ0, 0x33);
        tia.write(StellaTia.NUSIZ1, 0x23);
        tia.tick(1);
        check(tia.missile0().width() == 8 && tia.missile1().width() == 4,
                "NUSIZ0/NUSIZ1 set missile widths");
        check(tia.player0().nusiz() == 0x33 && tia.player1().nusiz() == 0x23,
                "NUSIZ0/NUSIZ1 still set player registers");

        tia.write(StellaTia.ENAM0, 0x02);
        check(!tia.missile0().isOn(), "ENAM0 is delayed and not applied immediately");
        tia.tick(2);
        tia.write(StellaTia.RESM0, 0);
        for (int i = 0; i < 200; i++) {
            tia.tick(1);
            if (tia.missile0().isOn()) {
                break;
            }
        }
        check(tia.missile0().isOn(), "ENAM0 enables output after the register delay");

        tia.write(StellaTia.COLUP0, 0x57);
        tia.tick(1);
        check(tia.missile0().color() == 0x56, "COLUP0 drives missile color with bit 0 masked");
    }

    private static int countOn(StellaTiaMissile m, int clocks) {
        int count = 0;
        for (int i = 0; i < clocks; i++) {
            m.tick(i % 228);
            if (m.isOn()) {
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
