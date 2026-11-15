package ru.viise.jemuxg.api.hardware;

/**
 * Блок памяти (RAM/ROM). Обращения зеркалируются по размеру блока.
 */
public interface Memory extends Bus {

    int size();
}
