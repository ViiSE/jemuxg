package ru.viise.jemuxg.console.atari2600;

import java.util.Arrays;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.hardware.audio.tia.TiaAudio;
import ru.viise.jemuxg.hardware.video.tia.Tia;

/**
 * Тесты аудиоканалов TIA.
 */
public final class AtariTiaAudioTests {

    private static int checks;

    public static void main(String[] args) {
        silence();
        audibleModes();
        frequencyChangesWaveform();
        sampleRate();
        dcRemoval();
        saveRestore();
        tiaIntegration();
        machineIntegration();
        System.out.println("Atari TIA audio tests OK, checks=" + checks);
    }

    private static void silence() {
        TiaAudio audio = new TiaAudio();
        Capture capture = new Capture();
        audio.connect(capture);
        audio.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        audio.flush();
        check(capture.size > 0, "audio produces samples even in silence");
        check(capture.allZero(), "AUDV=0 produces silence");
    }

    private static void audibleModes() {
        for (int mode = 0; mode < 16; mode++) {
            TiaAudio audio = new TiaAudio();
            Capture capture = new Capture();
            audio.connect(capture);
            audio.writeRegister(TiaAudio.AUDC0, mode);
            audio.writeRegister(TiaAudio.AUDF0, 0);
            audio.writeRegister(TiaAudio.AUDV0, 0x0F);
            audio.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME * 2);
            audio.flush();
            check(capture.anyNonZero(), "AUDC mode " + mode + " produces output");
        }
    }

    private static void frequencyChangesWaveform() {
        TiaAudio low = new TiaAudio();
        TiaAudio high = new TiaAudio();
        Capture lowCapture = new Capture();
        Capture highCapture = new Capture();
        low.connect(lowCapture);
        high.connect(highCapture);

        low.writeRegister(TiaAudio.AUDC0, 0x0C);
        low.writeRegister(TiaAudio.AUDF0, 0);
        low.writeRegister(TiaAudio.AUDV0, 0x0F);
        high.writeRegister(TiaAudio.AUDC0, 0x0C);
        high.writeRegister(TiaAudio.AUDF0, 31);
        high.writeRegister(TiaAudio.AUDV0, 0x0F);

        low.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME * 2);
        high.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME * 2);
        low.flush();
        high.flush();

        check(lowCapture.anyNonZero(), "low AUDF produces output");
        check(highCapture.anyNonZero(), "high AUDF produces output");
        check(!Arrays.equals(lowCapture.data(), highCapture.data()),
                "AUDF changes the generated waveform");
    }

    private static void sampleRate() {
        TiaAudio audio = new TiaAudio();
        Capture capture = new Capture();
        audio.connect(capture);
        audio.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        audio.flush();
        check(capture.size >= 730 && capture.size <= 740,
                "one frame produces about 735 samples at 44.1 kHz");
    }

    private static void dcRemoval() {
        TiaAudio audio = new TiaAudio();
        Capture capture = new Capture();
        audio.connect(capture);
        configure(audio, 0x0C, 0, 0x0F);
        audio.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME * 2);
        audio.flush();
        capture.clear();

        audio.writeRegister(TiaAudio.AUDV0, 0);
        audio.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME * 10);
        audio.flush();
        double mean = capture.tailAbsoluteMean(200);
        check(mean < 100.0, "DC removal settles to near zero, mean=" + mean);
    }

    private static void saveRestore() {
        TiaAudio original = new TiaAudio();
        configure(original, 0x0C, 0, 0x0F);
        original.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME * 2);
        original.flush();

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);
        TiaAudio restored = new TiaAudio();
        restored.restore(new BinaryStateReader(writer.toByteArray()));

        Capture expected = new Capture();
        Capture actual = new Capture();
        original.connect(expected);
        restored.connect(actual);
        original.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME * 2);
        restored.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME * 2);
        original.flush();
        restored.flush();

        check(Arrays.equals(expected.data(), actual.data()),
                "audio save/restore output matches");
    }

    private static void tiaIntegration() {
        Tia tia = new Tia();
        Capture capture = new Capture();
        tia.connectAudio(capture);
        tia.registers().write(Tia.AUDC0, 0x0C);
        tia.registers().write(Tia.AUDF0, 0);
        tia.registers().write(Tia.AUDV0, 0x0F);
        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
        check(capture.size > 0 && capture.anyNonZero(),
                "TIA registers drive audio output");
    }

    private static void machineIntegration() {
        Atari2600Machine machine = new Atari2600Machine();
        Capture capture = new Capture();
        machine.connectAudio(capture);
        machine.tia().registers().write(Tia.AUDC0, 0x0C);
        machine.tia().registers().write(Tia.AUDF0, 0);
        machine.tia().registers().write(Tia.AUDV0, 0x0F);
        machine.runFrame();
        check(capture.size > 0 && capture.anyNonZero(),
                "machine routes TIA audio to sink");
    }

    private static void configure(TiaAudio audio, int audc, int audf, int audv) {
        audio.writeRegister(TiaAudio.AUDC0, audc);
        audio.writeRegister(TiaAudio.AUDF0, audf);
        audio.writeRegister(TiaAudio.AUDV0, audv);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }

    private static final class Capture implements AudioSink {

        private short[] samples = new short[2048];
        private int size;

        @Override
        public void samplesReady(short[] source, int count) {
            ensure(size + count);
            System.arraycopy(source, 0, samples, size, count);
            size += count;
        }

        private void clear() {
            size = 0;
        }

        private boolean anyNonZero() {
            for (int i = 0; i < size; i++) {
                if (samples[i] != 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean allZero() {
            return !anyNonZero();
        }

        private double tailAbsoluteMean(int count) {
            int from = Math.max(0, size - count);
            if (from == size) {
                return 0;
            }
            long sum = 0;
            for (int i = from; i < size; i++) {
                sum += Math.abs((int) samples[i]);
            }
            return (double) sum / (size - from);
        }

        private short[] data() {
            return Arrays.copyOf(samples, size);
        }

        private void ensure(int capacity) {
            if (capacity > samples.length) {
                samples = Arrays.copyOf(samples, Math.max(capacity, samples.length * 2));
            }
        }
    }

    private AtariTiaAudioTests() {
    }
}
