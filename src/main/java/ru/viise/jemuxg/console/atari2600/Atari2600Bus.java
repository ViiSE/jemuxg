package ru.viise.jemuxg.console.atari2600;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Memory;

/**
 * Базовая карта памяти Atari 2600.
 *
 * <p>Адреса 6507 маскируются до 13 бит. TIA занимает
 * {@code $0000..$003F} и зеркалируется в {@code $0040..$007F};
 * RAM RIOT — {@code $0080..$00FF} и её зеркала
 * {@code $0100..$01FF} (включая страницу стека), регистры RIOT —
 * {@code $0280..$029F}, картридж — {@code $1000..$1FFF}. Остальные
 * адреса читаются как {@code 0xFF}, запись в них игнорируется.</p>
 */
public final class Atari2600Bus implements Bus {

    private static final int UNMAPPED_READ = 0xFF;

    private final Bus tia;
    private final Memory riotRam;
    private final Bus riotRegisters;
    private final Bus cartridge;
    private final boolean deferGraphicsWrites;
    private final List<int[]> pendingWrites = new ArrayList<>();

    public Atari2600Bus(Bus tia, Memory riotRam, Bus riotRegisters, Bus cartridge) {
        this(tia, riotRam, riotRegisters, cartridge, false);
    }

    public Atari2600Bus(
            Bus tia, Memory riotRam, Bus riotRegisters, Bus cartridge,
            boolean deferGraphicsWrites) {
        this.tia = Objects.requireNonNull(tia, "tia");
        this.riotRam = Objects.requireNonNull(riotRam, "riotRam");
        this.riotRegisters = Objects.requireNonNull(riotRegisters, "riotRegisters");
        this.cartridge = Objects.requireNonNull(cartridge, "cartridge");
        this.deferGraphicsWrites = deferGraphicsWrites;
        if (riotRam.size() != Atari2600Constants.RIOT_RAM_SIZE) {
            throw new IllegalArgumentException(
                    "RIOT RAM must be " + Atari2600Constants.RIOT_RAM_SIZE
                            + " bytes, got " + riotRam.size());
        }
    }

    @Override
    public int read(int address) {
        int address13 = address & Atari2600Constants.ADDRESS_MASK;

        if (address13 <= Atari2600Constants.TIA_MIRROR_END) {
            return tia.read(address13 & Atari2600Constants.TIA_ADDRESS_MASK);
        }
        if (address13 == 0x01FE) {
            // FE использует $01FE как hotspot, а чтение идёт из зеркала RIOT RAM.
            int value = riotRam.read(0x7E);
            cartridge.write(address13, value);
            return value;
        }
        if (address13 >= Atari2600Constants.RIOT_RAM_BASE
                && address13 <= 0x01FF) {
            return riotRam.read(address13 & Atari2600Constants.RIOT_RAM_OFFSET_MASK);
        }
        if (address13 >= Atari2600Constants.RIOT_BASE
                && address13 <= Atari2600Constants.RIOT_END) {
            return riotRegisters.read(address13 - Atari2600Constants.RIOT_BASE);
        }
        if (address13 >= Atari2600Constants.CARTRIDGE_BASE) {
            // Картридж получает абсолютный адрес окна: $1000..$1FFF.
            return cartridge.read(address13);
        }
        return UNMAPPED_READ;
    }

    @Override
    public void write(int address, int value) {
        int address13 = address & Atari2600Constants.ADDRESS_MASK;

        if (address13 <= Atari2600Constants.TIA_MIRROR_END) {
            int register = address13 & Atari2600Constants.TIA_ADDRESS_MASK;
            if (deferGraphicsWrites && register >= 0x04 && register <= 0x2B) {
                pendingWrites.add(new int[] {register, value & 0xFF});
            } else {
                tia.write(register, value);
            }
            // 3F/3E используют записи в страницу TIA как hotspots.
            cartridge.write(register, value);
            return;
        }
        if (address13 == 0x01FE) {
            riotRam.write(0x7E, value);
            cartridge.write(address13, value);
            return;
        }
        if (address13 >= Atari2600Constants.RIOT_RAM_BASE
                && address13 <= 0x01FF) {
            riotRam.write(address13 & Atari2600Constants.RIOT_RAM_OFFSET_MASK, value);
            return;
        }
        if (address13 >= Atari2600Constants.RIOT_BASE
                && address13 <= Atari2600Constants.RIOT_END) {
            riotRegisters.write(address13 - Atari2600Constants.RIOT_BASE, value);
            return;
        }
        if (address13 >= Atari2600Constants.CARTRIDGE_BASE) {
            cartridge.write(address13, value);
        }
    }

    /**
     * Применяет отложенные графические записи TIA в конце инструкции CPU.
     */
    public void flushPendingWrites() {
        for (int[] write : pendingWrites) {
            tia.write(write[0], write[1]);
        }
        pendingWrites.clear();
    }
}
