package ru.viise.jemuxg.api.debug;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Куда складывать отладочный снимок. Реализация может писать в файлы,
 * в память или в тестовый буфер.
 */
public interface DebugSession {

    /**
     * Каталог, в который будут записаны артефакты этого снимка.
     */
    Path directory();

    /**
     * Имя ROM без изменений, как его видит пользователь.
     */
    String romName();

    /**
     * Номер кадра, на котором сделан снимок.
     */
    long frameNumber();

    void writeBytes(String name, byte[] data) throws IOException;

    void writeText(String name, String text) throws IOException;

    /**
     * Сохранить кадр как PNG. Пиксели в формате ARGB (0xAARRGGBB).
     */
    void writePng(String name, int width, int height, int[] argb) throws IOException;
}
