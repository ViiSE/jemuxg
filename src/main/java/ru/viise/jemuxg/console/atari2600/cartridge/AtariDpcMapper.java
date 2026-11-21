package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер DPC (Pitfall II).
 *
 * <p>Схема включает F8-банкинг, 2 КиБ дисплейной ROM по смещению
 * {@code 8K} и контроллер DPC в окне {@code $1000..$107F}:
 * {@code $1000..$103F} — чтение data fetchers, {@code $1040..$107F} —
 * запись их регистров. Адреса {@code $1080..$1FFF} обслуживаются
 * обычным F8-банком.</p>
 *
 * <p>Питание начинается с верхнего 4K-банка: reset-вектор Pitfall II
 * находится именно там. Музыкальные data fetchers DPC тактируются
 * независимо от CPU; их флаги игра читает как амплитуду и использует для
 * управления каналами TIA.</p>
 */
public final class AtariDpcMapper implements AtariMapper {

    private static final int DISPLAY_OFFSET = 0x2000;
    private static final int DISPLAY_SIZE = 0x0800;
    private static final int DPC_WINDOW = 0x0080;
    private static final int COUNTER_MASK = 0x07FF;
    private static final double DPC_PITCH = 20_000.0;
    private static final double CPU_CLOCK = Atari2600Constants.CPU_CLOCK_HZ;

    private static final int[] MUSIC_AMPLITUDES = {
            0x00, 0x04, 0x05, 0x09, 0x06, 0x0A, 0x0B, 0x0F
    };
    private static final int[] RANDOM_TABLE = {
            1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1
    };

    private final AtariF8Mapper banks;
    private final byte[] display = new byte[DISPLAY_SIZE];

    private final int[] tops = new int[8];
    private final int[] bottoms = new int[8];
    private final int[] counters = new int[8];
    private final int[] flags = new int[8];
    private final boolean[] musicMode = new boolean[3];

    private int random = 1;
    private long cpuCycles;
    private long musicCycles;
    private double fractionalMusicClocks;

    /**
     * @param media образ DPC: 8 КиБ программы + 2 КиБ display ROM,
     *              допускается стандартный размер 10K и образ с
     *              выравнивающими байтами (как в Pitfall II)
     */
    public AtariDpcMapper(Media media) {
        if (media == null) {
            throw new IllegalArgumentException("media must not be null");
        }
        long size = media.size();
        if (size < DISPLAY_OFFSET + DISPLAY_SIZE) {
            throw new IllegalArgumentException(
                    "DPC cartridge requires at least 10K, got " + size + " bytes");
        }
        for (int i = 0; i < DISPLAY_SIZE; i++) {
            int value = media.readByte(DISPLAY_OFFSET + i);
            display[i] = (byte) (value < 0 ? 0xFF : value);
        }
        banks = new AtariF8Mapper(1);
    }

    @Override
    public long map(int address) {
        return banks.map(address);
    }

    @Override
    public void write(int address, int value) {
        if (!inWindow(address)) {
            return;
        }
        clockRandom();
        int offset = address & 0x0FFF;
        if (offset >= 0x40 && offset < DPC_WINDOW) {
            writeDpc(offset, value & 0xFF);
        } else {
            banks.write(address, value);
        }
    }

    @Override
    public int readByte(int address) {
        if (!inWindow(address)) {
            return -1;
        }
        clockRandom();
        int offset = address & 0x0FFF;
        if (offset < 0x40) {
            return readDpc(offset);
        }
        // Обычное чтение ROM: map() заодно обрабатывает F8-hotspots $1FF8/$1FF9.
        banks.map(address);
        return -1;
    }

    @Override
    public void tick(int cpuCycles) {
        if (cpuCycles < 0) {
            throw new IllegalArgumentException("cpuCycles must be non-negative");
        }
        this.cpuCycles += cpuCycles;
        updateMusicModeDataFetchers();
    }

    /**
     * Тактирует DF5..DF7 в музыкальном режиме. Алгоритм и частота DPC OSC
     * повторяют Stella; без этого флаги «верх/низ» замирают и один из
     * голосов Pitfall II остаётся выключенным.
     */
    private void updateMusicModeDataFetchers() {
        long cycles = cpuCycles - musicCycles;
        if (cycles <= 0) {
            return;
        }
        musicCycles = cpuCycles;

        double clocks = (DPC_PITCH * cycles) / CPU_CLOCK + fractionalMusicClocks;
        int wholeClocks = (int) clocks;
        fractionalMusicClocks = clocks - wholeClocks;
        if (wholeClocks == 0) {
            return;
        }

        for (int x = 5; x < 8; x++) {
            if (!musicMode[x - 5]) {
                continue;
            }
            int top = tops[x] + 1;
            int newLow = counters[x] & 0xFF;
            if (tops[x] != 0) {
                newLow -= wholeClocks % top;
                if (newLow < 0) {
                    newLow += top;
                }
            } else {
                newLow = 0;
            }

            if (newLow <= bottoms[x]) {
                flags[x] = 0;
            } else if (newLow <= tops[x]) {
                flags[x] = 0xFF;
            }
            counters[x] = (counters[x] & 0x0700) | newLow;
        }
    }

    private int readDpc(int offset) {
        int index = offset & 0x07;
        int function = (offset >> 3) & 0x07;

        if ((counters[index] & 0xFF) == tops[index]) {
            flags[index] = 0xFF;
        } else if ((counters[index] & 0xFF) == bottoms[index]) {
            flags[index] = 0x00;
        }

        int result;
        if (function == 0) {
            if (index < 4) {
                result = random;
            } else {
                // Stella updates the music fetchers only for this read path.
                updateMusicModeDataFetchers();
                result = musicAmplitude();
            }
        } else {
            result = switch (function) {
                case 1 -> display[(COUNTER_MASK - counters[index]) & COUNTER_MASK] & 0xFF;
                case 2 -> (display[(COUNTER_MASK - counters[index]) & COUNTER_MASK] & 0xFF)
                        & flags[index];
                case 7 -> flags[index];
                default -> 0;
            };
        }

        if (index < 5 || !musicMode[index - 5]) {
            counters[index] = (counters[index] - 1) & COUNTER_MASK;
        }
        return result;
    }

    private void writeDpc(int offset, int value) {
        int index = offset & 0x07;
        int function = (offset >> 3) & 0x07;

        switch (function) {
            case 0 -> {
                tops[index] = value;
                flags[index] = 0;
            }
            case 1 -> bottoms[index] = value;
            case 2 -> counters[index] = musicModeAt(index)
                    ? (counters[index] & 0x0700) | tops[index]
                    : (counters[index] & 0x0700) | value;
            case 3 -> {
                counters[index] = ((value & 0x07) << 8) | (counters[index] & 0xFF);
                if (index >= 5) {
                    musicMode[index - 5] = (value & 0x10) != 0;
                }
            }
            case 6 -> random = 1;
            default -> {
                // Остальные функции DPC зарезервированы.
            }
        }
    }

    private boolean musicModeAt(int index) {
        return index >= 5 && musicMode[index - 5];
    }

    private int musicAmplitude() {
        int value = 0;
        if (musicMode[0] && flags[5] != 0) {
            value |= 0x01;
        }
        if (musicMode[1] && flags[6] != 0) {
            value |= 0x02;
        }
        if (musicMode[2] && flags[7] != 0) {
            value |= 0x04;
        }
        return MUSIC_AMPLITUDES[value];
    }

    private void clockRandom() {
        int bit = RANDOM_TABLE[((random >> 3) & 0x07) | ((random & 0x80) != 0 ? 0x08 : 0)];
        random = ((random << 1) | bit) & 0xFF;
    }

    @Override
    public String name() {
        return "DPC";
    }

    @Override
    public int bank() {
        return banks.bank();
    }

    @Override
    public int bankCount() {
        return banks.bankCount();
    }

    @Override
    public boolean writable() {
        return true;
    }

    @Override
    public String debugState() {
        return "bank=" + bank() + "/" + bankCount()
                + ", random=" + String.format("%02X", random)
                + ", counters=" + java.util.Arrays.toString(counters);
    }

    @Override
    public void save(StateWriter writer) {
        banks.save(writer);
        for (int i = 0; i < 8; i++) {
            writer.writeByte(tops[i]);
            writer.writeByte(bottoms[i]);
            writer.writeShort(counters[i]);
            writer.writeByte(flags[i]);
        }
        for (boolean mode : musicMode) {
            writer.writeByte(mode ? 1 : 0);
        }
        writer.writeByte(random);
        writer.writeLong(cpuCycles);
        writer.writeLong(musicCycles);
        writer.writeLong(Double.doubleToLongBits(fractionalMusicClocks));
    }

    @Override
    public void restore(StateReader reader) {
        banks.restore(reader);
        for (int i = 0; i < 8; i++) {
            tops[i] = reader.readByte();
            bottoms[i] = reader.readByte();
            int counter = reader.readShort();
            if (counter < 0 || counter > COUNTER_MASK) {
                throw new IllegalArgumentException("invalid DPC counter in save state: " + counter);
            }
            counters[i] = counter;
            flags[i] = reader.readByte();
        }
        for (int i = 0; i < musicMode.length; i++) {
            musicMode[i] = reader.readByte() != 0;
        }
        random = reader.readByte();
        cpuCycles = reader.readLong();
        musicCycles = reader.readLong();
        fractionalMusicClocks = Double.longBitsToDouble(reader.readLong());
        if (cpuCycles < 0 || musicCycles < 0 || musicCycles > cpuCycles
                || !Double.isFinite(fractionalMusicClocks)) {
            throw new IllegalArgumentException("invalid DPC music timing in save state");
        }
    }

    private static boolean inWindow(int address) {
        return address >= Atari2600Constants.CARTRIDGE_BASE
                && address <= Atari2600Constants.CARTRIDGE_END;
    }
}
