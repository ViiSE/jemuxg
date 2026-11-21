package ru.viise.jemuxg.hardware.audio.sn76489;

import java.util.Arrays;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.console.sg1000.Sg1000Bus;
import ru.viise.jemuxg.console.sg1000.Sg1000Machine;
import ru.viise.jemuxg.console.sg1000.cartridge.Sg1000CartridgeSlot;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.hardware.video.tms9918.Tms9918;

/**
 * Контрактные проверки SN76489AN.
 */
public final class Sn76489Tests {

    private static int checks;

    private Sn76489Tests() {
    }

    public static void main(String[] args) {
        resetSilence();
        writeProtocol();
        tonePeriods();
        volumeTable();
        noiseModes();
        mixing();
        saveRestore();
        busRouting();
        machineIntegration();
        System.out.println("SN76489 tests OK, checks=" + checks);
    }

    private static void resetSilence() {
        Sn76489 psg = new Sn76489();
        Capture capture = new Capture();
        psg.connect(capture);
        psg.tick(100_000);
        psg.flush();
        check(capture.count > 0 && capture.allZero(),
                "reset leaves all channels silent");
        check(psg.volume(0) == 0 && psg.volume(1) == 0
                && psg.volume(2) == 0 && psg.volume(3) == 0,
                "reset sets maximum attenuation");
    }

    private static void writeProtocol() {
        Sn76489 psg = new Sn76489();
        psg.write(0x85);
        psg.write(0x01);
        check(psg.register(0) == 0x15 && psg.period(0) == 0x15,
                "tone latch and high period byte are combined");

        psg.write(0x90);
        check(psg.register(1) == 0 && psg.volume(0) > 0,
                "volume latch selects the attenuation register");
        psg.write(0x9F);
        check(psg.volume(0) == 0, "maximum attenuation is silent");

        psg.write(0xA3);
        psg.write(0x02);
        check(psg.register(2) == 0x23 && psg.period(1) == 0x23,
                "second tone channel uses registers 2 and 3");

        psg.write(0xE4);
        check(psg.register(6) == 4 && psg.period(3) == 32,
                "noise latch stores mode and fixed clock");
        check(psg.lfsr() == 0x4000, "noise write resets the LFSR");
    }

    private static void tonePeriods() {
        Sn76489 psg = new Sn76489();
        psg.write(0x90);
        psg.write(0x8A);
        psg.write(0x00);
        check(psg.period(0) == 10, "10-bit tone period is loaded");
        psg.tick(16L * 10);
        check(psg.output(0), "tone output toggles after N chip clocks");
        psg.tick(16L * 10);
        check(!psg.output(0), "tone output toggles again after another N");

        psg.write(0x80);
        psg.write(0x00);
        check(psg.period(0) == 1024, "zero period uses the 1024 divider");
    }

    private static void volumeTable() {
        Sn76489 psg = new Sn76489();
        int previous = Integer.MAX_VALUE;
        for (int attenuation = 0; attenuation < 15; attenuation++) {
            psg.write(0x90 | attenuation);
            int current = psg.volume(0);
            check(current > 0 && current < previous,
                    "attenuation step " + attenuation + " lowers volume");
            previous = current;
        }
        psg.write(0x9F);
        check(psg.volume(0) == 0, "attenuation 15 is zero");
    }

    private static void noiseModes() {
        Sn76489 periodic = new Sn76489();
        periodic.write(0xE0);
        periodic.write(0xF0);
        periodic.tick(16L * 32 * 14);
        int periodicState = periodic.lfsr();
        check(periodicState != 0x4000, "periodic noise shifts the LFSR");

        Sn76489 white = new Sn76489();
        white.write(0xE4);
        white.write(0xF0);
        white.tick(16L * 32 * 15);
        int whiteState = white.lfsr();
        check(whiteState != periodicState,
                "white noise uses the second feedback tap");

        Sn76489 toneClock = new Sn76489();
        toneClock.write(0xA0);
        toneClock.write(0x06);
        toneClock.write(0xE3);
        check(toneClock.period(3) == toneClock.period(2) * 2,
                "noise mode 3 is clocked by tone channel 2");
    }

    private static void mixing() {
        Sn76489 psg = new Sn76489();
        Capture capture = new Capture();
        psg.connect(capture);
        psg.write(0x90);
        psg.write(0x84);
        psg.write(0x06);
        psg.tick(100_000);
        psg.flush();
        check(capture.count > 1000 && capture.anyNonZero(),
                "tone produces audio samples");
        check(capture.anyPositive() && capture.anyNegative(),
                "square wave is centred around zero");
        check(capture.maxAbs() <= 8191, "single channel stays within range");
    }

    private static void saveRestore() {
        Sn76489 psg = new Sn76489();
        psg.write(0x90);
        psg.write(0x8B);
        psg.write(0x07);
        psg.write(0xE4);
        psg.tick(5000);

        BinaryStateWriter writer = new BinaryStateWriter();
        psg.save(writer);
        byte[] state = writer.toByteArray();

        Sn76489 copy = new Sn76489();
        copy.restore(new BinaryStateReader(state));
        check(copy.register(0) == psg.register(0)
                && copy.period(0) == psg.period(0)
                && copy.volume(0) == psg.volume(0),
                "SN76489 restores registers and periods");
        check(copy.lfsr() == psg.lfsr()
                && copy.bufferPosition() == psg.bufferPosition(),
                "SN76489 restores LFSR and buffer position");

        Capture first = new Capture();
        Capture second = new Capture();
        psg.flush();
        copy.flush();
        psg.connect(first);
        copy.connect(second);
        psg.tick(10_000);
        copy.tick(10_000);
        psg.flush();
        copy.flush();
        check(Arrays.equals(first.samples(), second.samples()),
                "restored PSG produces the same samples");
    }

    private static void busRouting() {
        Sn76489 psg = new Sn76489();
        Sg1000Bus bus = new Sg1000Bus(
                new Sg1000CartridgeSlot(), new Tms9918(), psg);
        bus.out(0x7F, 0x90);
        check(psg.register(1) == 0, "PSG is reachable at 0x7F");
        bus.out(0x40, 0x9F);
        check(psg.register(1) == 0x0F, "PSG responds to mirrored ports");
        bus.out(0xBF, 0x00);
        check(psg.register(1) == 0x0F, "VDP control port is not routed to PSG");
        check(bus.psg() == psg, "bus exposes the PSG");
    }

    private static void machineIntegration() {
        Sg1000Machine machine = new Sg1000Machine();
        Capture capture = new Capture();
        machine.connectAudio(capture);
        machine.bus().out(0x7F, 0x90);
        machine.bus().out(0x7F, 0x84);
        machine.bus().out(0x7F, 0x06);
        machine.runFrame();
        check(capture.count >= 700 && capture.count <= 800,
                "one frame produces about 735 audio samples");
        check(capture.anyNonZero(), "machine routes PSG audio to the sink");
        check(machine.psg() == machine.bus().psg(),
                "machine exposes the same PSG as the bus");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }

    private static final class Capture implements AudioSink {

        private short[] data = new short[4096];
        private int count;

        @Override
        public void samplesReady(short[] samples, int sampleCount) {
            if (count + sampleCount > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, count + sampleCount));
            }
            System.arraycopy(samples, 0, data, count, sampleCount);
            count += sampleCount;
        }

        short[] samples() {
            return Arrays.copyOf(data, count);
        }

        boolean allZero() {
            for (int i = 0; i < count; i++) {
                if (data[i] != 0) {
                    return false;
                }
            }
            return true;
        }

        boolean anyNonZero() {
            return !allZero();
        }

        boolean anyPositive() {
            for (int i = 0; i < count; i++) {
                if (data[i] > 0) {
                    return true;
                }
            }
            return false;
        }

        boolean anyNegative() {
            for (int i = 0; i < count; i++) {
                if (data[i] < 0) {
                    return true;
                }
            }
            return false;
        }

        int maxAbs() {
            int max = 0;
            for (int i = 0; i < count; i++) {
                max = Math.max(max, Math.abs(data[i]));
            }
            return max;
        }
    }
}
