package ru.viise.jemuxg.hardware.video.tia;

import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Controller;
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

    /**
     * @return остановлен ли CPU записью в WSYNC до конца текущей строки
     */
    boolean cpuHalted();

    /**
     * Подключает джойстик к порту игрока.
     *
     * @param port 0 или 1
     * @param controller контроллер; {@code null} отключает порт
     */
    void connectPlayer(int port, Controller controller);
}
