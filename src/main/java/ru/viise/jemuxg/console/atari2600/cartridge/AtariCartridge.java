package ru.viise.jemuxg.console.atari2600.cartridge;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.storage.Cartridge;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Картридж Atari 2600.
 *
 * <p>Схема выбирается {@link AtariMapperSelector} по размеру ROM,
 * имени файла или явной настройке {@code mapper}. Адреса
 * {@code $1000..$1FFF} преобразуются маппером в смещение внутри ROM.
 * Reset-вектор читается из окна картриджа, поэтому для 2K-картриджа он
 * автоматически берётся из зеркала {@code $1FFC..$1FFD}, а для банковых
 * схем — из текущего банка.</p>
 */
public final class AtariCartridge implements Cartridge, Bus, Stateful {

    private final Media media;
    private final AtariMapper mapper;

    public AtariCartridge(Media media) {
        this(media, null);
    }

    /**
     * @param mapperName явная схема ({@code f8}, {@code e0}, {@code 3f}...)
     *                   или {@code null}/{@code auto} для автоопределения
     */
    public AtariCartridge(Media media, String mapperName) {
        this.media = Objects.requireNonNull(media, "media");
        if (media.kind() != MediaKind.CARTRIDGE) {
            throw new IllegalArgumentException(
                    "Atari 2600 accepts cartridge media, got " + media.kind());
        }
        this.mapper = AtariMapperSelector.select(media, mapperName);
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
        int direct = mapper.readByte(address);
        if (direct >= 0) {
            return direct;
        }
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

    /**
     * Сохраняет состояние маппера. ROM не дублируется: при восстановлении
     * требуется картридж того же размера и схемы.
     */
    @Override
    public void save(StateWriter writer) {
        writer.writeLong(media.size());
        writer.writeBytes(mapper.name().getBytes(StandardCharsets.UTF_8));
        mapper.save(writer);
    }

    @Override
    public void restore(StateReader reader) {
        long savedSize = reader.readLong();
        if (savedSize != media.size()) {
            throw new IllegalArgumentException(
                    "save state cartridge size " + savedSize + " does not match " + media.size());
        }
        String savedMapper = new String(reader.readBytes(), StandardCharsets.UTF_8);
        if (!savedMapper.equals(mapper.name())) {
            throw new IllegalArgumentException(
                    "save state mapper " + savedMapper + " does not match " + mapper.name());
        }
        mapper.restore(reader);
    }
}
