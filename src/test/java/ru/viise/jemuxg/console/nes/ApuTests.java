package ru.viise.jemuxg.console.nes;

import java.util.Arrays;
import java.util.function.Consumer;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.hardware.audio.ricoh2a03.Ricoh2a03Apu;

/**
 * JUnit-free проверки APU. Запуск:
 * {@code java -cp target/classes:target/test-classes ru.viise.jemuxg.console.nes.ApuTests}.
 */
public final class ApuTests {

    private static int checks;

    private ApuTests() {
    }

    public static void main(String[] args) {
        pulseProducesOutput();
        triangleProducesOutput();
        noiseProducesOutput();
        dmcProducesOutput();
        fourStepFrameIrq();
        fiveStepHasNoIrq();
        dcRemoval();
        saveRestore();
        System.out.println("APU tests OK, checks=" + checks);
    }

    private static void pulseProducesOutput() {
        expectOutput("pulse", apu -> {
            Bus registers = apu.registers();
            registers.write(0x4015, 0x01);
            registers.write(0x4000, 0xBF);
            registers.write(0x4001, 0x08);
            registers.write(0x4002, 0x00);
            registers.write(0x4003, 0x01);
        });
    }

    private static void triangleProducesOutput() {
        expectOutput("triangle", apu -> {
            Bus registers = apu.registers();
            registers.write(0x4015, 0x04);
            registers.write(0x4008, 0xFF);
            registers.write(0x400A, 0x00);
            registers.write(0x400B, 0x01);
        });
    }

    private static void noiseProducesOutput() {
        expectOutput("noise", apu -> {
            Bus registers = apu.registers();
            registers.write(0x4015, 0x08);
            registers.write(0x400C, 0x3F);
            registers.write(0x400E, 0x00);
            registers.write(0x400F, 0x01);
        });
    }

    private static void dmcProducesOutput() {
        expectOutput("dmc", apu -> {
            apu.connectCpuBus(new Bus() {
                @Override
                public int read(int address) {
                    return 0xFF;
                }

                @Override
                public void write(int address, int value) {
                }
            });
            Bus registers = apu.registers();
            registers.write(0x4010, 0x0F);
            registers.write(0x4011, 0x00);
            registers.write(0x4012, 0x00);
            registers.write(0x4013, 0x01);
            registers.write(0x4015, 0x10);
        });
    }

    private static void expectOutput(String name, Consumer<Ricoh2a03Apu> setup) {
        Ricoh2a03Apu apu = new Ricoh2a03Apu();
        Capture capture = new Capture();
        apu.connect(capture);
        setup.accept(apu);
        apu.tick(30_000);
        apu.flush();
        check(capture.anyNonZero(), name + " produces output");
    }

    private static void fourStepFrameIrq() {
        Ricoh2a03Apu apu = new Ricoh2a03Apu();
        Bus registers = apu.registers();
        registers.write(0x4017, 0x00);

        apu.tick(29_828);
        check(!apu.irqPending(), "frame IRQ not set before 29829 cycles");
        apu.tick(1);
        check(apu.irqPending(), "frame IRQ set at 29829 cycles");

        int status = registers.read(0x4015);
        check((status & 0x40) != 0, "frame IRQ visible in $4015");
        check(!apu.irqPending(), "frame IRQ cleared by $4015 read");
    }

    private static void fiveStepHasNoIrq() {
        Ricoh2a03Apu apu = new Ricoh2a03Apu();
        apu.registers().write(0x4017, 0x80);
        apu.tick(37_282);
        check(!apu.irqPending(), "5-step frame counter has no IRQ");
    }

    private static void dcRemoval() {
        Ricoh2a03Apu apu = new Ricoh2a03Apu();
        Capture capture = new Capture();
        apu.connect(capture);
        configurePulse(apu);
        apu.tick(30_000);
        apu.flush();

        capture.clear();
        apu.registers().write(0x4015, 0x00);
        // Даём high-pass фильтру достаточно времени, чтобы переходный
        // процесс после выключения канала ушёл в ноль.
        apu.tick(300_000);
        apu.flush();
        double tailMean = capture.tailAbsoluteMean(1_000);
        check(tailMean < 100.0, "DC removal settles to near zero");
    }

    private static void saveRestore() {
        Ricoh2a03Apu original = new Ricoh2a03Apu();
        configurePulse(original);
        original.tick(5_000);
        original.flush();

        BinaryStateWriter writer = new BinaryStateWriter();
        original.save(writer);
        Ricoh2a03Apu restored = new Ricoh2a03Apu();
        restored.restore(new BinaryStateReader(writer.toByteArray()));

        Capture expected = new Capture();
        Capture actual = new Capture();
        original.connect(expected);
        restored.connect(actual);
        original.tick(1_000);
        restored.tick(1_000);
        original.flush();
        restored.flush();

        check(Arrays.equals(expected.data(), actual.data()), "APU save/restore output matches");
    }

    private static void configurePulse(Ricoh2a03Apu apu) {
        Bus registers = apu.registers();
        registers.write(0x4015, 0x01);
        registers.write(0x4000, 0xBF);
        registers.write(0x4001, 0x08);
        registers.write(0x4002, 0x00);
        registers.write(0x4003, 0x01);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }

    private static final class Capture implements AudioSink {

        private short[] samples = new short[1_024];
        private int size;

        @Override
        public void samplesReady(short[] source, int count) {
            if (count <= 0) {
                return;
            }
            ensureCapacity(size + count);
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

        private double tailAbsoluteMean(int count) {
            int length = Math.min(count, size);
            if (length == 0) {
                return 0.0;
            }
            long sum = 0;
            for (int i = size - length; i < size; i++) {
                sum += Math.abs((int) samples[i]);
            }
            return (double) sum / length;
        }

        private short[] data() {
            return Arrays.copyOf(samples, size);
        }

        private void ensureCapacity(int needed) {
            if (needed <= samples.length) {
                return;
            }
            samples = Arrays.copyOf(samples, Math.max(needed, samples.length * 2));
        }
    }
}
