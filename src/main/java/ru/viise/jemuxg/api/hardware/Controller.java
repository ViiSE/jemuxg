package ru.viise.jemuxg.api.hardware;

/**
 * Геймпад/клавиатура в глазах эмулятора.
 */
public interface Controller {

    boolean pressed(Button button);
}
