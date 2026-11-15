package ru.viise.jemuxg.api.storage;

/**
 * Диск с секторным доступом (CD/DVD).
 */
public interface Disc {

    int sectorSize();

    long sectorCount();

    int readSector(long lba, byte[] destination, int offset, int length);
}
