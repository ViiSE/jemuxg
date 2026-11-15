package ru.viise.jemuxg.hardware.video.tia;

import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.VideoProcessor;

/**
 * Видеочасть TIA Atari 2600.
 *
 * <p>TIA не имеет отдельной видеопамяти: изображение строится по регистрам
 * playfield/player/missile/ball во время прохождения луча. Регистры доступны
 * CPU через 64-байтное окно {@code $0000..$003F}.</p>
 */
public interface TiaVideo extends VideoProcessor {

    /**
     * @return шина регистров TIA в адресном пространстве 6507
     */
    Bus registers();

    /**
     * @return текущая строка развёртки
     */
    int scanline();

    /**
     * @return текущий цветовой такт внутри строки
     */
    int colorClock();
}
