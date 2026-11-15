package ru.viise.jemuxg.api.storage;

import ru.viise.jemuxg.api.hardware.Bus;

/**
 * Устройство-накопитель: плата картриджа, привод CD/DVD, дисковод.
 * Предоставляет шине свои регистры (если они есть).
 */
public interface Storage {

    Media media();

    Bus bus();
}
