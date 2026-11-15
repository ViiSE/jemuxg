package ru.viise.jemuxg.console.nes.cartridge;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Mirroring;
import ru.viise.jemuxg.api.storage.Media;

/**
 * Заголовок iNES/NES 2.0.
 *
 * @param mapperId    номер маппера
 * @param prgRomSize  размер PRG ROM в байтах
 * @param chrRomSize  размер CHR ROM в байтах (0 — CHR RAM)
 * @param prgRamSize  размер PRG RAM по заголовку (0 — не указан)
 * @param mirroring   начальная схема зеркалирования
 * @param battery     есть ли battery-backed PRG RAM
 * @param trainer     есть ли 512-байтный trainer после заголовка
 * @param fourScreen  включено ли four-screen зеркалирование
 * @param nes2        используется ли формат NES 2.0
 * @param headerSize  смещение начала PRG ROM
 */
public record INesHeader(
        int mapperId,
        int prgRomSize,
        int chrRomSize,
        int prgRamSize,
        Mirroring mirroring,
        boolean battery,
        boolean trainer,
        boolean fourScreen,
        boolean nes2,
        int headerSize) {

    public static final int SIZE = 16;
    public static final int TRAINER_SIZE = 512;

    public INesHeader {
        Objects.requireNonNull(mirroring, "mirroring");
        if (mapperId < 0) {
            throw new IllegalArgumentException("mapperId must be >= 0, got " + mapperId);
        }
        if (prgRomSize <= 0) {
            throw new IllegalArgumentException("prgRomSize must be > 0, got " + prgRomSize);
        }
        if (chrRomSize < 0) {
            throw new IllegalArgumentException("chrRomSize must be >= 0, got " + chrRomSize);
        }
        if (prgRamSize < 0) {
            throw new IllegalArgumentException("prgRamSize must be >= 0, got " + prgRamSize);
        }
        if (headerSize < SIZE) {
            throw new IllegalArgumentException("headerSize must be >= " + SIZE + ", got " + headerSize);
        }
    }

    public static INesHeader parse(Media media) {
        Objects.requireNonNull(media, "media");
        return parse(read(media, 0, SIZE));
    }

    public static INesHeader parse(byte[] header) {
        Objects.requireNonNull(header, "header");
        if (header.length < SIZE) {
            throw new IllegalArgumentException(
                    "iNES header is too short: expected " + SIZE + " bytes, got " + header.length);
        }
        if ((header[0] & 0xFF) != 'N' || (header[1] & 0xFF) != 'E'
                || (header[2] & 0xFF) != 'S' || (header[3] & 0xFF) != 0x1A) {
            throw new IllegalArgumentException("not an iNES ROM: bad magic");
        }

        int flags6 = header[6] & 0xFF;
        int flags7 = header[7] & 0xFF;
        int flags8 = header[8] & 0xFF;
        int flags9 = header[9] & 0xFF;
        int flags10 = header[10] & 0xFF;

        int consoleType = flags7 & 0x03;
        if (consoleType != 0) {
            throw new IllegalArgumentException(
                    "unsupported iNES console type " + consoleType + " (only NES is supported)");
        }

        boolean nes2 = (flags7 & 0x0C) == 0x08;
        int mapperId = (flags6 >> 4) | (flags7 & 0xF0);
        int prgBanks = header[4] & 0xFF;
        int chrBanks = header[5] & 0xFF;
        if (nes2) {
            mapperId |= (flags8 & 0x0F) << 8;
            prgBanks |= (flags9 & 0x0F) << 8;
            chrBanks |= ((flags9 >> 4) & 0x0F) << 8;
        }
        if (prgBanks == 0) {
            throw new IllegalArgumentException(
                    "PRG ROM size is zero (unsupported NES 2.0 exponent notation?)");
        }

        boolean battery = (flags6 & 0x02) != 0;
        boolean trainer = (flags6 & 0x04) != 0;
        boolean fourScreen = (flags6 & 0x08) != 0;
        Mirroring mirroring;
        if (fourScreen) {
            mirroring = Mirroring.FOUR_SCREEN;
        } else {
            mirroring = (flags6 & 0x01) == 0 ? Mirroring.HORIZONTAL : Mirroring.VERTICAL;
        }

        int prgRamSize;
        if (nes2) {
            int shift = flags10 & 0x0F;
            prgRamSize = shift == 0 ? 0 : 64 << shift;
        } else {
            prgRamSize = flags8 == 0 ? 8 * 1024 : flags8 * 8 * 1024;
        }

        int prgRomSize = prgBanks * 0x4000;
        int chrRomSize = chrBanks * 0x2000;
        int headerSize = SIZE + (trainer ? TRAINER_SIZE : 0);
        return new INesHeader(
                mapperId, prgRomSize, chrRomSize, prgRamSize,
                mirroring, battery, trainer, fourScreen, nes2, headerSize);
    }

    /**
     * Читает ровно {@code length} байт или бросает исключение, если носитель
     * закончился раньше.
     */
    public static byte[] read(Media media, long offset, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0, got " + length);
        }
        byte[] data = new byte[length];
        int total = 0;
        while (total < length) {
            int read = media.read(offset + total, data, total, length - total);
            if (read <= 0) {
                throw new IllegalArgumentException(
                        "truncated cartridge: wanted " + length + " bytes at offset " + offset
                                + ", got " + total);
            }
            total += read;
        }
        return data;
    }
}
