package ru.viise.jemuxg.hardware.audio.ricoh2a03;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;

/**
 * DMC-канал: воспроизведение сэмплов из памяти CPU.
 */
final class DmcChannel {

    private static final int[] RATES = {
        428, 380, 340, 320, 286, 254, 226, 214,
        190, 160, 142, 128, 106, 84, 72, 54
    };

    private Bus cpuBus = new Bus() {
        @Override
        public int read(int address) {
            return 0xFF;
        }

        @Override
        public void write(int address, int value) {
        }
    };

    private boolean irqEnabled;
    private boolean loop;
    private boolean enabled;
    private int rateIndex;
    private int phase;
    private int sampleAddress;
    private int sampleLength;
    private int currentAddress;
    private int bytesRemaining;
    private int sampleBuffer;
    private boolean bufferEmpty = true;
    private int shiftRegister;
    private int bitsRemaining;
    private int outputLevel;
    private boolean irqPending;

    void setCpuBus(Bus bus) {
        cpuBus = bus;
    }

    void write(int register, int value) {
        switch (register) {
            case 0 -> {
                irqEnabled = (value & 0x80) != 0;
                loop = (value & 0x40) != 0;
                rateIndex = value & 0x0F;
                if (!irqEnabled) {
                    irqPending = false;
                }
            }
            case 1 -> outputLevel = value & 0x7F;
            case 2 -> sampleAddress = 0xC000 + ((value & 0xFF) << 6);
            case 3 -> sampleLength = ((value & 0xFF) << 4) + 1;
            default -> {
            }
        }
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && bytesRemaining == 0) {
            restartSample();
        }
        if (!enabled) {
            bytesRemaining = 0;
        }
    }

    private void restartSample() {
        currentAddress = sampleAddress;
        bytesRemaining = sampleLength;
        bufferEmpty = true;
    }

    void tick() {
        phase++;
        if (phase >= RATES[rateIndex]) {
            phase = 0;
            clock();
        }
    }

    private void clock() {
        if (!enabled) {
            return;
        }

        if (bitsRemaining == 0) {
            if (!bufferEmpty) {
                shiftRegister = sampleBuffer;
                bufferEmpty = true;
                bitsRemaining = 8;
            } else if (bytesRemaining > 0) {
                fetchSample();
                if (!bufferEmpty) {
                    shiftRegister = sampleBuffer;
                    bufferEmpty = true;
                    bitsRemaining = 8;
                }
            }
        }

        if (bitsRemaining > 0) {
            if ((shiftRegister & 1) != 0) {
                if (outputLevel <= 125) {
                    outputLevel += 2;
                }
            } else if (outputLevel >= 2) {
                outputLevel -= 2;
            }
            shiftRegister >>= 1;
            bitsRemaining--;
        }
    }

    private void fetchSample() {
        sampleBuffer = cpuBus.read(currentAddress) & 0xFF;
        currentAddress++;
        if (currentAddress > 0xFFFF) {
            currentAddress = 0x8000;
        }
        bytesRemaining--;
        bufferEmpty = false;
        if (bytesRemaining == 0) {
            if (loop) {
                currentAddress = sampleAddress;
                bytesRemaining = sampleLength;
            } else if (irqEnabled) {
                irqPending = true;
            }
        }
    }

    int output() {
        return outputLevel;
    }

    boolean active() {
        return bytesRemaining > 0;
    }

    boolean irqPending() {
        return irqPending;
    }

    void clearIrq() {
        irqPending = false;
    }

    void reset() {
        irqEnabled = false;
        loop = false;
        enabled = false;
        rateIndex = 0;
        phase = 0;
        sampleAddress = 0xC000;
        sampleLength = 0;
        currentAddress = 0xC000;
        bytesRemaining = 0;
        sampleBuffer = 0;
        bufferEmpty = true;
        shiftRegister = 0;
        bitsRemaining = 0;
        outputLevel = 0;
        irqPending = false;
    }

    void save(StateWriter writer) {
        writer.writeByte(irqEnabled ? 1 : 0);
        writer.writeByte(loop ? 1 : 0);
        writer.writeByte(enabled ? 1 : 0);
        writer.writeInt(rateIndex);
        writer.writeInt(phase);
        writer.writeInt(sampleAddress);
        writer.writeInt(sampleLength);
        writer.writeInt(currentAddress);
        writer.writeInt(bytesRemaining);
        writer.writeInt(sampleBuffer);
        writer.writeByte(bufferEmpty ? 1 : 0);
        writer.writeInt(shiftRegister);
        writer.writeInt(bitsRemaining);
        writer.writeInt(outputLevel);
        writer.writeByte(irqPending ? 1 : 0);
    }

    void restore(StateReader reader) {
        irqEnabled = reader.readByte() != 0;
        loop = reader.readByte() != 0;
        enabled = reader.readByte() != 0;
        rateIndex = reader.readInt();
        phase = reader.readInt();
        sampleAddress = reader.readInt();
        sampleLength = reader.readInt();
        currentAddress = reader.readInt();
        bytesRemaining = reader.readInt();
        sampleBuffer = reader.readInt();
        bufferEmpty = reader.readByte() != 0;
        shiftRegister = reader.readInt();
        bitsRemaining = reader.readInt();
        outputLevel = reader.readInt();
        irqPending = reader.readByte() != 0;
    }
}
