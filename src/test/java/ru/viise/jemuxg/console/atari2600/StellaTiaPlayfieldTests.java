package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.hardware.video.tia.StellaTiaPlayfield;

/** JUnit-free checks for the isolated Stella-style Playfield port. */
public final class StellaTiaPlayfieldTests {

    private static int checks;

    public static void main(String[] args) {
        patternShape();
        reflection();
        refpLatch();
        scoreColors();
        priorityModes();
        fullPattern();
        System.out.println("Stella TIA playfield tests OK, checks=" + checks);
    }

    private static void patternShape() {
        StellaTiaPlayfield pf = new StellaTiaPlayfield();
        pf.pf0(0x10);   // bit 0
        pf.pf1(0x80);   // bit 4
        pf.pf2(0x01);   // bit 12
        check(pf.pattern() == ((1 << 0) | (1 << 4) | (1 << 12)),
                "PF0/PF1/PF2 build the 20-bit pattern");

        pf.tick(0);
        check(pf.isOn(), "PF pattern bit 0 is on at x=0");
        pf.tick(4);
        check(!pf.isOn(), "PF bit 1 is off at x=4");
        pf.tick(16);
        check(pf.isOn(), "PF1 bit maps to x=16");
        pf.tick(48);
        check(pf.isOn(), "PF2 bit maps to x=48");
        pf.tick(52);
        check(!pf.isOn(), "PF2 next bit is off at x=52");
    }

    private static void reflection() {
        StellaTiaPlayfield plain = new StellaTiaPlayfield();
        plain.pf0(0x10); // bit 0
        plain.tick(80);
        check(plain.isOn(), "non-reflected right half repeats bit 0 at x=80");
        plain.tick(156);
        check(!plain.isOn(), "non-reflected right half has no bit at x=156");

        StellaTiaPlayfield mirror = new StellaTiaPlayfield();
        mirror.ctrlpf(0x01);
        mirror.pf0(0x10); // bit 0
        mirror.tick(0);
        check(mirror.isOn(), "reflected left half still uses bit 0 at x=0");
        mirror.tick(80);
        check(!mirror.isOn(), "reflected right half uses bit 19 at x=80");
        mirror.tick(156);
        check(mirror.isOn(), "reflected right half mirrors bit 0 to x=156");
    }

    private static void refpLatch() {
        StellaTiaPlayfield pf = new StellaTiaPlayfield();
        pf.ctrlpf(0x00);
        pf.pf0(0x10);
        pf.tick(0);
        check(!pf.refp(), "reflect latch starts off at x=0");

        pf.ctrlpf(0x01);
        check(!pf.refp(), "mid-line reflect change is not latched immediately");
        pf.tick(80);
        check(!pf.refp(), "reflect stays latched until x=79");
        check(pf.isOn(), "right half still repeats while latch is off");

        StellaTiaPlayfield latched = new StellaTiaPlayfield();
        latched.ctrlpf(0x00);
        latched.pf0(0x10);
        latched.tick(0);
        latched.ctrlpf(0x01);
        latched.tick(79);
        check(latched.refp(), "reflect latches at x=79");
        latched.tick(80);
        check(!latched.isOn(), "latched reflection uses bit 19 at x=80");
        latched.tick(156);
        check(latched.isOn(), "latched reflection mirrors bit 0 to x=156");
    }

    private static void scoreColors() {
        StellaTiaPlayfield pf = new StellaTiaPlayfield();
        pf.setColorP0(0x1F);
        pf.setColorP1(0x47);
        pf.setColor(0x85);
        check(pf.colorAt(0) == 0x84, "COLUPF is masked to 7 bits");

        pf.ctrlpf(0x02);
        check(pf.scoreMode(), "CTRLPF 0x02 enables score mode");
        check(pf.colorAt(0) == 0x1E, "score left half uses COLUP0");
        check(pf.colorAt(79) == 0x1E, "score boundary is at x=80");
        check(pf.colorAt(80) == 0x46, "score right half uses COLUP1");
        check(pf.colorAt(159) == 0x46, "score right half extends to x=159");

        pf.ctrlpf(0x00);
        check(!pf.scoreMode(), "CTRLPF 0x00 returns to normal mode");
        check(pf.colorAt(0) == 0x84 && pf.colorAt(80) == 0x84,
                "normal mode uses COLUPF for both halves");
    }

    private static void priorityModes() {
        StellaTiaPlayfield pf = new StellaTiaPlayfield();
        pf.ctrlpf(0x00);
        check(pf.priority() == StellaTiaPlayfield.PRIORITY_NORMAL,
                "CTRLPF 0x00 selects normal priority");
        pf.ctrlpf(0x01);
        check(pf.priority() == StellaTiaPlayfield.PRIORITY_NORMAL,
                "reflect does not change priority");
        pf.ctrlpf(0x02);
        check(pf.priority() == StellaTiaPlayfield.PRIORITY_SCORE,
                "CTRLPF 0x02 selects score priority");
        pf.ctrlpf(0x04);
        check(pf.priority() == StellaTiaPlayfield.PRIORITY_PFP,
                "CTRLPF 0x04 selects playfield priority");
        pf.ctrlpf(0x06);
        check(pf.priority() == StellaTiaPlayfield.PRIORITY_PFP,
                "CTRLPF D2 wins over D1");
        check(!pf.scoreMode(), "CTRLPF 0x06 is not score color mode");
    }

    private static void fullPattern() {
        StellaTiaPlayfield pf = new StellaTiaPlayfield();
        pf.pf0(0xFF);
        pf.pf1(0xFF);
        pf.pf2(0xFF);
        check(pf.pattern() == 0xFFFFF, "all PF registers set all 20 bits");

        int on = 0;
        for (int x = 0; x < StellaTiaPlayfield.WIDTH; x += 4) {
            pf.tick(x);
            if (pf.isOn()) {
                on++;
            }
        }
        check(on == 40, "full playfield covers all 40 four-clock columns");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}
