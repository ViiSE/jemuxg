package ru.viise.jemuxg.console.atari2600.cartridge;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.storage.Cartridge;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Картридж Atari 2600 с фиксированной схемой 2K/4K или банковыми
 * схемами F8/F6/F4.
 *
 * <p>Адреса {@code $1000..$1FFF} преобразуются маппером в смещение
 * внутри ROM. Reset-вектор читается из окна картриджа, поэтому для
 * 2K-картриджа он автоматически берётся из зеркала
 * {@code $1FFC..$1FFD}, а для банковых схем — из текущего банка.</p>
 */
public final class AtariCartridge implements Cartridge, Bus {

    private static final int TWO_KB = 2 * 1024;
    private static final int FOUR_KB = 4 * 1024;
    private static final int EIGHT_KB = 8 * 1024;
    private static final int SIXTEEN_KB = 16 * 1024;
    private static final int THIRTY_TWO_KB = 32 * 1024;

    private final Media media;
    private final AtariMapper mapper;

    public AtariCartridge(Media media) {
        this.media = Objects.requireNonNull(media, "media");
        if (media.kind() != MediaKind.CARTRIDGE) {
            throw new IllegalArgumentException(
                    "Atari 2600 accepts cartridge media, got " + media.kind());
        }
        long size = media.size();
        this.mapper = switch ((int) size) {
            case TWO_KB, FOUR_KB -> new AtariFixedMapper((int) size);
            case EIGHT_KB -> new AtariF8Mapper();
            case SIXTEEN_KB -> new AtariF6Mapper();
            case THIRTY_TWO_KB -> new AtariF4Mapper();
            default -> throw new IllegalArgumentException(
                    "Atari 2600 cartridge must be 2K, 4K, 8K (F8), 16K (F6) or 32K (F4), got "
                            + size + " bytes");
        };
    }

    @Override
    public Media media() {
        return media;
    }

    @Override
    public AtariMapper mapper() {
        return mapper;
    }

    @Override
    public Bus bus() {
        return this;
    }

    @Override
    public int read(int address) {
        long offset = mapper.map(address);
        if (offset < 0 || offset >= media.size()) {
            return 0xFF;
        }
        int value = media.readByte(offset);
        return value < 0 ? 0xFF : value;
    }

    @Override
    public void write(int address, int value) {
        mapper.write(address, value);
    }

    /**
     * Reset-вектор 6507, прочитанный через окно картриджа.
     */
    public int resetVector() {
        return read(Atari2600Constants.RESET_VECTOR)
                | (read(Atari2600Constants.RESET_VECTOR + 1) << 8);
    }
}
