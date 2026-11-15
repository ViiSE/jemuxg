package ru.viise.jemuxg.api.storage;

/**
 * Неизменяемые данные носителя: ROM-файл, ISO-образ, образ дискеты.
 * Это не устройство, а содержимое.
 */
public interface Media {

    String name();

    long size();

    MediaKind kind();

    /**
     * @return сколько байт реально прочитано (0, если offset за пределами носителя)
     */
    int read(long offset, byte[] destination, int destinationOffset, int length);

    default int readByte(long offset) {
        byte[] one = new byte[1];
        return read(offset, one, 0, 1) == 1 ? one[0] & 0xFF : -1;
    }
}
