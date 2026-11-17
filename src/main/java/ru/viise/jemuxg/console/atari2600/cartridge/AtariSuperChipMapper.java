package ru.viise.jemuxg.console.atari2600.cartridge;

/**
 * SuperChip: банковый маппер F8/F6/F4 плюс 128 байт RAM.
 *
 * <p>Запись RAM — {@code $1000-$107F}, чтение — {@code $1080-$10FF}.
 * Банковые hotspots обслуживает базовый маппер.</p>
 */
public final class AtariSuperChipMapper implements AtariMapper {

    private static final int RAM_SIZE = 0x80;

    private final AtariMapper base;
    private final byte[] ram = new byte[RAM_SIZE];

    public AtariSuperChipMapper(AtariMapper base) {
        if (base == null) {
            throw new IllegalArgumentException("base mapper is required");
        }
        this.base = base;
    }

    @Override
    public long map(int address) {
        if (isRamWindow(address)) {
            return -1;
        }
        return base.map(address);
    }

    @Override
    public int readByte(int address) {
        if (address >= 0x1080 && address <= 0x10FF) {
            return ram[address & 0x7F] & 0xFF;
        }
        return -1;
    }

    @Override
    public void write(int address, int value) {
        if (address >= 0x1000 && address <= 0x107F) {
            ram[address & 0x7F] = (byte) value;
        } else {
            base.write(address, value);
        }
    }

    @Override
    public String name() {
        return base.name() + "SC";
    }

    @Override
    public int bank() {
        return base.bank();
    }

    @Override
    public int bankCount() {
        return base.bankCount();
    }

    @Override
    public boolean writable() {
        return true;
    }

    public byte[] ramSnapshot() {
        return ram.clone();
    }

    private static boolean isRamWindow(int address) {
        return address >= 0x1000 && address <= 0x10FF;
    }
}
