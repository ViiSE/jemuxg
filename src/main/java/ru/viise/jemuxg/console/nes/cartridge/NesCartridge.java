package ru.viise.jemuxg.console.nes.cartridge;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Mirroring;
import ru.viise.jemuxg.api.storage.Cartridge;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;

/**
 * Картридж NES: разобранный iNES-образ, PRG/CHR-память и маппер.
 * CPU видит его как шину $4020..$FFFF, PPU — через {@link #chrBus()}.
 */
public final class NesCartridge implements Cartridge, Bus {

    private static final int PRG_RAM_BASE = 0x6000;
    private static final int PRG_RAM_END = 0x7FFF;
    private static final int PRG_RAM_SIZE = 0x2000;
    private static final int CHR_RAM_SIZE = 0x2000;

    private final Media media;
    private final INesHeader header;
    private final byte[] prgRom;
    private final byte[] chr;
    private final boolean chrRam;
    private final byte[] prgRam = new byte[PRG_RAM_SIZE];
    private final NesMapper mapper;
    private final Bus chrBus;

    public NesCartridge(Media media) {
        this.media = Objects.requireNonNull(media, "media");
        if (media.kind() != MediaKind.CARTRIDGE) {
            throw new IllegalArgumentException("NES accepts cartridge media, got " + media.kind());
        }

        this.header = INesHeader.parse(media);
        long prgOffset = header.headerSize();
        long chrOffset = prgOffset + header.prgRomSize();
        long end = chrOffset + header.chrRomSize();
        if (media.size() < end) {
            throw new IllegalArgumentException(
                    "truncated iNES ROM: header expects " + end + " bytes, media has " + media.size());
        }

        this.prgRom = INesHeader.read(media, prgOffset, header.prgRomSize());
        this.chrRam = header.chrRomSize() == 0;
        this.chr = chrRam
                ? new byte[CHR_RAM_SIZE]
                : INesHeader.read(media, chrOffset, header.chrRomSize());
        this.mapper = createMapper();
        this.chrBus = new Bus() {
            @Override
            public int read(int address) {
                long offset = mapper.mapPpu(address);
                if (offset < 0 || offset >= chr.length) {
                    return 0xFF;
                }
                return chr[(int) offset] & 0xFF;
            }

            @Override
            public void write(int address, int value) {
                mapper.writePpu(address, value);
            }
        };
    }

    public INesHeader header() {
        return header;
    }

    public NesMapper mapper() {
        return mapper;
    }

    public Mirroring mirroring() {
        return mapper.mirroring();
    }

    public boolean isChrRam() {
        return chrRam;
    }

    /**
     * Копия PRG RAM для отладочных снимков.
     */
    public byte[] prgRamSnapshot() {
        return prgRam.clone();
    }

    /**
     * Шина CHR-памяти для PPU (адреса $0000..$1FFF).
     */
    public Bus chrBus() {
        return chrBus;
    }

    @Override
    public Media media() {
        return media;
    }

    @Override
    public Bus bus() {
        return this;
    }

    @Override
    public int read(int address) {
        if (address >= PRG_RAM_BASE && address <= PRG_RAM_END) {
            if (!mapper.prgRamEnabled()) {
                return 0xFF;
            }
            return prgRam[address - PRG_RAM_BASE] & 0xFF;
        }
        long offset = mapper.map(address);
        if (offset < 0 || offset >= prgRom.length) {
            return 0xFF;
        }
        return prgRom[(int) offset] & 0xFF;
    }

    @Override
    public void write(int address, int value) {
        if (address >= PRG_RAM_BASE && address <= PRG_RAM_END) {
            if (mapper.prgRamWritable()) {
                prgRam[address - PRG_RAM_BASE] = (byte) value;
            }
            return;
        }
        mapper.write(address, value);
    }

    private NesMapper createMapper() {
        return switch (header.mapperId()) {
            case 0 -> new NromMapper(prgRom, chr, chrRam, header.mirroring(), header.fourScreen());
            case 1 -> new Mmc1Mapper(prgRom, chr, chrRam, header.mirroring(), header.fourScreen());
            case 3 -> new CnromMapper(prgRom, chr, chrRam, header.mirroring(), header.fourScreen());
            case 4 -> new Mmc3Mapper(prgRom, chr, chrRam, header.mirroring(), header.fourScreen());
            default -> throw new IllegalArgumentException(
                    "unsupported NES mapper " + header.mapperId()
                            + " (supported: 0 NROM, 1 MMC1, 3 CNROM, 4 MMC3)");
        };
    }
}
