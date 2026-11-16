package ru.viise.jemuxg.console.atari2600;

import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.hardware.riot.riot6532.RiotTimer;

/**
 * Тесты таймера RIOT 6532.
 */
public final class AtariRiotTimerTests {

    private static int checks;

    private AtariRiotTimerTests() {
    }

    public static void main(String[] args) {
        initialState();
        oneCycleMode();
        eightCycleMode();
        sixtyFourCycleMode();
        thousandTwentyFourCycleMode();
        zeroValue();
        restart();
        saveRestore();
        absoluteAddresses();
        resetState();

        System.out.println("Atari RIOT timer tests OK, checks=" + checks);
    }

    private static void initialState() {
        RiotTimer timer = new RiotTimer();
        Bus registers = timer.registers();

        check(timer.timer() == 0, "initial timer is zero");
        check(!timer.underflow(), "initial underflow flag is clear");
        check(registers.read(RiotTimer.INTIM) == 0, "initial INTIM is zero");
        check(registers.read(RiotTimer.INTFLG) == 0, "initial INTFLG is zero");
    }

    private static void oneCycleMode() {
        RiotTimer timer = new RiotTimer();
        Bus registers = timer.registers();
        registers.write(RiotTimer.TIM1T, 5);

        check(timer.timer() == 5, "TIM1T loads interval");
        timer.tick(4);
        check(timer.timer() == 1, "TIM1T decrements every cycle");
        timer.tick(1);
        check(timer.timer() == 0, "TIM1T reaches zero");
        check(!timer.underflow(), "TIM1T flag is clear at zero");
        timer.tick(1);
        check(timer.timer() == 0xFF, "TIM1T underflows to FF");
        check(timer.underflow(), "TIM1T sets underflow flag");
        check(registers.read(RiotTimer.INTFLG) == 0x80, "TIM1T flag is visible in INTFLG");
        check(timer.underflow(), "reading INTFLG does not clear timer flag");
        check(registers.read(RiotTimer.INTIM) == 0xFF, "INTIM returns current value");
        check(!timer.underflow(), "reading INTIM clears timer flag");
    }

    private static void eightCycleMode() {
        RiotTimer timer = new RiotTimer();
        Bus registers = timer.registers();
        registers.write(RiotTimer.TIM8T, 2);

        timer.tick(1);
        check(timer.timer() == 1, "TIM8T first decrement after one cycle");
        timer.tick(8);
        check(timer.timer() == 0, "TIM8T reaches zero after divider");
        check(!timer.underflow(), "TIM8T flag is clear at zero");
        timer.tick(8);
        check(timer.timer() == 0xFF, "TIM8T underflows after divider");
        check(timer.underflow(), "TIM8T sets underflow flag");
    }

    private static void sixtyFourCycleMode() {
        RiotTimer timer = new RiotTimer();
        Bus registers = timer.registers();
        registers.write(RiotTimer.TIM64T, 1);

        timer.tick(1);
        check(timer.timer() == 0, "TIM64T first decrement after one cycle");
        timer.tick(64);
        check(timer.timer() == 0xFF, "TIM64T underflows after 64 cycles");
        check(timer.underflow(), "TIM64T sets underflow flag");
    }

    private static void thousandTwentyFourCycleMode() {
        RiotTimer timer = new RiotTimer();
        Bus registers = timer.registers();
        registers.write(RiotTimer.TIM1024T, 1);

        timer.tick(1);
        check(timer.timer() == 0, "TIM1024T first decrement after one cycle");
        timer.tick(1024);
        check(timer.timer() == 0xFF, "TIM1024T underflows after 1024 cycles");
        check(timer.underflow(), "TIM1024T sets underflow flag");
    }

    private static void zeroValue() {
        RiotTimer timer = new RiotTimer();
        timer.registers().write(RiotTimer.TIM1T, 0);

        timer.tick(1);
        check(timer.timer() == 0xFF, "zero interval underflows after one cycle");
        check(timer.underflow(), "zero interval sets flag");
    }

    private static void restart() {
        RiotTimer timer = new RiotTimer();
        Bus registers = timer.registers();

        registers.write(RiotTimer.TIM1T, 5);
        timer.tick(6);
        check(timer.underflow(), "timer underflows before restart");

        registers.write(RiotTimer.TIM1T, 3);
        check(timer.timer() == 3, "restart reloads interval");
        check(!timer.underflow(), "restart clears flag");
        timer.tick(3);
        check(timer.timer() == 0, "restarted timer reaches zero");
        timer.tick(1);
        check(timer.timer() == 0xFF, "restarted timer underflows");
    }

    private static void saveRestore() {
        RiotTimer original = new RiotTimer();
        original.registers().write(RiotTimer.TIM1T, 5);
        original.tick(2);
        check(original.timer() == 3, "timer mid-count before save");

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);

        RiotTimer restored = new RiotTimer();
        restored.restore(new BinaryStateReader(writer.toByteArray()));
        check(restored.timer() == 3, "timer value restored");
        check(!restored.underflow(), "timer flag restored");

        restored.tick(3);
        check(restored.timer() == 0, "restored timer reaches zero");
        restored.tick(1);
        check(restored.timer() == 0xFF, "restored timer underflows");
        check(restored.underflow(), "restored timer sets flag");
    }

    private static void absoluteAddresses() {
        RiotTimer timer = new RiotTimer();
        Bus registers = timer.registers();

        registers.write(0x0294, 7);
        check(timer.timer() == 7, "absolute TIM1T address works");
        check(registers.read(0x0294) == 7, "absolute INTIM address works");

        registers.write(0x0295, 2);
        timer.tick(1);
        check(timer.timer() == 1, "absolute TIM8T address works");
    }

    private static void resetState() {
        RiotTimer timer = new RiotTimer();
        timer.registers().write(RiotTimer.TIM1T, 0);
        timer.tick(1);
        check(timer.underflow(), "timer flag set before reset");

        timer.reset();
        check(timer.timer() == 0, "reset clears timer");
        check(!timer.underflow(), "reset clears flag");
        check(timer.registers().read(RiotTimer.INTFLG) == 0, "reset clears INTFLG");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
