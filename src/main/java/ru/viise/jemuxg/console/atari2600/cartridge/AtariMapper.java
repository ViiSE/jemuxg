package ru.viise.jemuxg.console.atari2600.cartridge;

import ru.viise.jemuxg.api.storage.Mapper;

/**
 * Банковый маппер картриджа Atari 2600.
 *
 * <p>Адреса приходят из окна 6507 {@code $1000..$1FFF}. Для фиксированных
 * картриджей 2K/4K маппер возвращает смещение без переключения банков.</p>
 */
public interface AtariMapper extends Mapper {

    String name();

    /**
     * @return текущий номер банка (с нуля)
     */
    int bank();

    /**
     * @return общее количество банков
     */
    int bankCount();

    /**
     * @return можно ли писать в адресное окно картриджа
     */
    default boolean writable() {
        return false;
    }
}
