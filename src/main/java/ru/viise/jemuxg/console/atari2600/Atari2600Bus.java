package ru.viise.jemuxg.console.atari2600;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Memory;

/**
 * Базовая карта памяти Atari 2600.
 *
 * <p>Адреса 6507 маскируются до 13 бит. TIA занимает
 * {@code $0000..$003F} и зеркалируется в {@code $0040..$007F};
 * RAM RIOT — {@code $0080..$00FF}, регистры RIOT — {@code $0280..$029F},
 * картридж — {@code $1000..$1FFF}. Остальные адреса читаются как
 * {@code 0xFF}, запись в них игнорируется.</p>
 */
public final class Atari2600Bus implements Bus {

    private static final int UNMAPPED_READ = 0xFF;

    private final Bus tia;
    private final Memory riotRam;
    private final Bus riotRegisters;
    private final Bus cartridge;

    public Atari2600Bus(Bus tia, Memory riotRam, Bus riotRegisters, Bus cartridge) {
        this.tia = Objects.requireNonNull(tia, "tia");
        this.riotRam = Objects.requireNonNull(riotRam, "riotRam");
        this.riotRegisters = Objects.requireNonNull(riotRegisters, "riotRegisters");
        this.cartridge = Objects.requireNonNull(cartridge, "cartridge");
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
        if (address13 >= Atari2600Constants.RIOT_RAM_BASE
                && address13 <= Atari2600Constants.RIOT_RAM_END) {
            return riotRam.read(address13 & Atari2600Constants.RIOT_RAM_OFFSET_MASK);
        }
        if (address13 >= Atari2600Constants.RIOT_BASE
                && address13 <= Atari2600Constants.RIOT_END) {
            return riotRegisters.read(address13 - Atari2600Constants.RIOT_BASE);
        }
        if (address13 >= Atari2600Constants.CARTRIDGE_BASE) {
            return cartridge.read(address13 & Atari2600Constants.CARTRIDGE_OFFSET_MASK);
        }
        return UNMAPPED_READ;
    }

    @Override
    public void write(int address, int value) {
        int address13 = address & Atari2600Constants.ADDRESS_MASK;

        if (address13 <= Atari2600Constants.TIA_MIRROR_END) {
            tia.write(address13 & Atari2600Constants.TIA_ADDRESS_MASK, value);
            return;
        }
        if (address13 >= Atari2600Constants.RIOT_RAM_BASE
                && address13 <= Atari2600Constants.RIOT_RAM_END) {
            riotRam.write(address13 & Atari2600Constants.RIOT_RAM_OFFSET_MASK, value);
            return;
        }
        if (address13 >= Atari2600Constants.RIOT_BASE
                && address13 <= Atari2600Constants.RIOT_END) {
            riotRegisters.write(address13 - Atari2600Constants.RIOT_BASE, value);
            return;
        }
        if (address13 >= Atari2600Constants.CARTRIDGE_BASE) {
            cartridge.write(address13 & Atari2600Constants.CARTRIDGE_OFFSET_MASK, value);
        }
    }
}
