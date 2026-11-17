package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер E0 (Parker Brothers): четыре 1K-сегмента, три переключаемых
 * и последний фиксированный.
 */
public final class AtariE0Mapper implements AtariMapper {

    private static final int BANK_SIZE = 0x400;
    private static final int BANK_COUNT = 8;

    private final int[] segments = {4, 5, 6};

    public AtariE0Mapper(int romSize) {
        if (romSize != BANK_COUNT * BANK_SIZE) {
            throw new IllegalArgumentException("E0 requires an 8K ROM, got " + romSize);
        }
    }

    @Override
    public long map(int address) {
        if (!inWindow(address)) {
            return -1;
        }
        int segment = (address - Atari2600Constants.CARTRIDGE_BASE) / BANK_SIZE;
        int selected = segment < 3 ? segments[segment] : BANK_COUNT - 1;
        return (long) selected * BANK_SIZE + (address & 0x03FF);
    }

    @Override
    public void write(int address, int value) {
        if (address >= 0x1FE0 && address <= 0x1FE7) {
            segments[0] = address & 0x07;
        } else if (address >= 0x1FE8 && address <= 0x1FEF) {
            segments[1] = address & 0x07;
        } else if (address >= 0x1FF0 && address <= 0x1FF7) {
            segments[2] = address & 0x07;
        }
    }

    @Override
    public String name() {
        return "E0";
    }

    @Override
    public int bank() {
        return segments[0];
    }

    @Override
    public int bankCount() {
        return BANK_COUNT;
    }

    @Override
    public String debugState() {
        return "segments=" + segments[0] + "," + segments[1] + "," + segments[2] + ",7";
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeByte(segments[0]);
        writer.writeByte(segments[1]);
        writer.writeByte(segments[2]);
    }

    @Override
    public void restore(StateReader reader) {
        int segment0 = reader.readByte();
        int segment1 = reader.readByte();
        int segment2 = reader.readByte();
        if (segment0 >= BANK_COUNT || segment1 >= BANK_COUNT || segment2 >= BANK_COUNT) {
            throw new IllegalArgumentException(
                    "invalid E0 segments in save state: " + segment0 + "," + segment1 + "," + segment2);
        }
        segments[0] = segment0;
        segments[1] = segment1;
        segments[2] = segment2;
    }

    private static boolean inWindow(int address) {
        return address >= Atari2600Constants.CARTRIDGE_BASE
                && address <= Atari2600Constants.CARTRIDGE_END;
    }
}
