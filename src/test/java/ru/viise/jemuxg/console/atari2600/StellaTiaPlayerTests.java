package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.hardware.video.tia.StellaTiaPlayer;

/** JUnit-free checks for the isolated Stella-style Player port. */
public final class StellaTiaPlayerTests {

    private static int checks;

    public static void main(String[] args) {
        singleCopy();
        copies();
        vdel();
        System.out.println("Stella TIA player tests OK, checks=" + checks);
    }

    private static void singleCopy() {
        StellaTiaPlayer p = new StellaTiaPlayer();
        p.setCounter(150);
        p.grp(0xFF);
        p.setNusiz(0);
        int on = countOn(p, 160);
        check(on == 8, "single copy produces eight pixels");
    }

    private static void copies() {
        StellaTiaPlayer p = new StellaTiaPlayer();
        p.setCounter(150);
        p.grp(0xFF);
        p.setNusiz(3);
        int on = countOn(p, 160);
        check(on == 24, "NUSIZ 3 produces three copies");
    }

    private static void vdel() {
        StellaTiaPlayer p = new StellaTiaPlayer();
        p.setCounter(150);
        p.grp(0xFF);
        p.setVdel(1);
        check(countOn(p, 160) == 0, "VDEL hides new pattern until shuffle");
        p.shufflePatterns();
        check(countOn(p, 160) == 8, "VDEL shows old pattern after shuffle");
    }

    private static int countOn(StellaTiaPlayer p, int clocks) {
        int count = 0;
        for (int i = 0; i < clocks; i++) {
            p.tick();
            if (p.on()) {
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
