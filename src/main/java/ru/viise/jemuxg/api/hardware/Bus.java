package ru.viise.jemuxg.api.hardware;

/**
 * Шина. Единственный способ общения CPU с остальными устройствами.
 * Адреса — 32-битные, чтобы покрыть и 16-битные, и 32-битные платформы.
 */
public interface Bus {

    int read(int address);

    void write(int address, int value);
}
