package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Маппер фиксированных картриджей Atari 2600 объёмом 2 или 4 КБ.
 *
 * <p>2 КБ ROM зеркалируется в окне {@code $1000..$1FFF}: адреса
 * {@code $1800..$1FFF} повторяют {@code $1000..$17FF}. 4 КБ ROM
 * отображается напрямую.</p>
 */
public final class AtariFixedMapper implements AtariMapper {

    private static final int TWO_KB = 2 * 1024;
    private static final int FOUR_KB = 4 * 1024;

    private final int size;
    private final String name;
    private final int bankCount;

    public AtariFixedMapper(int size) {
        if (size != TWO_KB && size != FOUR_KB) {
            throw new IllegalArgumentException(
                    "Fixed Atari mapper supports 2K or 4K ROM, got " + size + " bytes");
        }
        this.size = size;
        this.name = size == TWO_KB ? "2K" : "4K";
        this.bankCount = 1;
    }

    @Override
    public long map(int address) {
        if (address < Atari2600Constants.CARTRIDGE_BASE
                || address > Atari2600Constants.CARTRIDGE_END) {
            return -1;
        }
        int offset = address & Atari2600Constants.CARTRIDGE_OFFSET_MASK;
        return size == TWO_KB ? offset & 0x07FF : offset;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int bank() {
        return 0;
    }

    @Override
    public int bankCount() {
        return bankCount;
    }

    public int size() {
        return size;
    }
}
