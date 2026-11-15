package ru.viise.jemuxg.hardware.video.ricoh2c02;

import java.util.Arrays;
import java.util.Objects;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Mirroring;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoProcessor;
import ru.viise.jemuxg.api.hardware.VideoSink;

/**
 * PPU NES (Ricoh 2C02).
 * <p>
 * Реализованы регистры, видеопамять, палитра, OAM, NMI, прокрутка,
 * фон и спрайты. Рендеринг построчный: каждая видимая строка рисуется
 * в начале соответствующего scanline. Это достаточно точно для обычных
 * игр и позволяет менять прокрутку по IRQ между строками.
 * <p>
 * A12-событие для MMC3 выдаётся один раз за видимую строку при включённом
 * рендеринге. Точная эмуляция всех PPU-задержек и sprite overflow bug
 * не является целью этого этапа.
 */
public final class Ricoh2c02 implements VideoProcessor {

    public static final int WIDTH = 256;
    public static final int HEIGHT = 240;
    public static final int SCANLINES = 262;
    public static final int CYCLES_PER_SCANLINE = 341;
    public static final long CYCLES_PER_FRAME = (long) SCANLINES * CYCLES_PER_SCANLINE;

    private static final int STATUS_VBLANK = 0x80;
    private static final int STATUS_SPRITE_ZERO = 0x40;
    private static final int STATUS_SPRITE_OVERFLOW = 0x20;

    private static final int[] POWER_UP_PALETTE = {
        0x09, 0x01, 0x00, 0x01, 0x00, 0x02, 0x02, 0x0D,
        0x08, 0x10, 0x08, 0x24, 0x00, 0x00, 0x04, 0x2C,
        0x09, 0x01, 0x34, 0x03, 0x00, 0x04, 0x00, 0x14,
        0x08, 0x3A, 0x00, 0x02, 0x00, 0x20, 0x2C, 0x08
    };

    private final byte[] vram = new byte[0x1000];
    private final byte[] palette = powerUpPalette();
    private final byte[] oam = new byte[0x100];
    private final int[] pixels = new int[WIDTH * HEIGHT];
    private final int[] pixelIndices = new int[WIDTH * HEIGHT];
    private final int[] rowBackground = new int[WIDTH];
    private final int[] rowSprite = new int[WIDTH];

    private final Bus registerBus = new Bus() {
        @Override
        public int read(int address) {
            return readRegister(address & 0x07);
        }

        @Override
        public void write(int address, int value) {
            writeRegister(address & 0x07, value);
        }
    };

    private Bus chrBus = new Bus() {
        @Override
        public int read(int address) {
            return 0;
        }

        @Override
        public void write(int address, int value) {
        }
    };

    private VideoSink sink;
    private Runnable a12Callback;

    private int control;
    private int mask;
    private int status;
    private int oamAddress;
    private int v;
    private int t;
    private int fineX;
    private boolean writeToggle;
    private int readBuffer;
    private int ioLatch;
    private int scanline;
    private int cycle;
    private int sprite0HitCycle = -1;
    private boolean nmiPending;
    private long frameCount;
    private Mirroring mirroring = Mirroring.HORIZONTAL;

    public Bus registers() {
        return registerBus;
    }

    public void connectChr(Bus bus) {
        chrBus = Objects.requireNonNull(bus, "bus");
    }

    public void setMirroring(Mirroring mirroring) {
        this.mirroring = Objects.requireNonNull(mirroring, "mirroring");
    }

    /**
     * Слушатель подъёма A12. Нужен MMC3 для счётчика IRQ.
     */
    public void connectA12(Runnable callback) {
        a12Callback = callback;
    }

    /**
     * @return было ли запрошено NMI; флаг сбрасывается
     */
    public boolean takeNmi() {
        boolean pending = nmiPending;
        nmiPending = false;
        return pending;
    }

    public int oamAddress() {
        return oamAddress;
    }

    public int status() {
        return status;
    }

    public long frameCount() {
        return frameCount;
    }

    /**
     * Текущий кадр в ARGB (0xAARRGGBB), копия.
     */
    public int[] framebufferArgb() {
        return pixels.clone();
    }

    /**
     * Текущий кадр как индексы палитры NES (0..63), копия.
     */
    public byte[] framebufferIndices() {
        byte[] result = new byte[pixelIndices.length];
        for (int i = 0; i < pixelIndices.length; i++) {
            result[i] = (byte) (pixelIndices[i] & 0x3F);
        }
        return result;
    }

    public byte[] vramSnapshot() {
        return vram.clone();
    }

    public byte[] paletteSnapshot() {
        return palette.clone();
    }

    public byte[] oamSnapshot() {
        return oam.clone();
    }

    /**
     * OAM DMA: 256 байт из страницы памяти CPU.
     */
    public void oamDma(Bus cpuBus, int page) {
        int base = (page & 0xFF) << 8;
        for (int i = 0; i < 256; i++) {
            oam[(oamAddress + i) & 0xFF] = (byte) cpuBus.read((base + i) & 0xFFFF);
        }
    }

    @Override
    public String name() {
        return "Ricoh 2C02";
    }

    @Override
    public void reset() {
        Arrays.fill(vram, (byte) 0);
        for (int i = 0; i < palette.length; i++) {
            palette[i] = (byte) POWER_UP_PALETTE[i];
        }
        Arrays.fill(oam, (byte) 0);
        Arrays.fill(pixels, 0xFF000000);
        Arrays.fill(pixelIndices, 0);
        control = 0;
        mask = 0;
        status = 0;
        oamAddress = 0;
        v = 0;
        t = 0;
        fineX = 0;
        writeToggle = false;
        readBuffer = 0;
        ioLatch = 0;
        scanline = 0;
        cycle = 0;
        sprite0HitCycle = -1;
        nmiPending = false;
        frameCount = 0;
        mirroring = Mirroring.HORIZONTAL;
    }

    @Override
    public void connect(VideoSink sink) {
        this.sink = sink;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public void tick(long delta) {
        for (long i = 0; i < delta; i++) {
            step();
        }
    }

    private void step() {
        if (scanline < HEIGHT && cycle == 0) {
            renderScanline(scanline);
        }

        if (scanline < HEIGHT && cycle == sprite0HitCycle) {
            status |= STATUS_SPRITE_ZERO;
        }

        // MMC3 считает строки по подъёму A12. Один подъём на строку —
        // достаточное приближение для scanline-эффектов.
        if (scanline < HEIGHT && cycle == 256
                && (mask & 0x18) != 0 && a12Callback != null) {
            a12Callback.run();
        }

        if (scanline == 241 && cycle == 1) {
            status |= STATUS_VBLANK;
            if ((control & 0x80) != 0) {
                nmiPending = true;
            }
        }

        // Флаг VBL сбрасывается примерно через 2270 CPU-тактов после NMI.
        // При NMI на 241:1 это соответствует 260:330.
        if (scanline == 260 && cycle == 330) {
            status &= ~STATUS_VBLANK;
        }

        if (scanline == 261 && cycle == 1) {
            status &= ~(STATUS_SPRITE_ZERO | STATUS_SPRITE_OVERFLOW);
        }

        cycle++;
        if (cycle >= CYCLES_PER_SCANLINE) {
            cycle = 0;
            scanline++;
            if (scanline >= SCANLINES) {
                scanline = 0;
                deliverFrame();
            }
        }
    }

    private void deliverFrame() {
        frameCount++;
        if (sink != null) {
            sink.frameReady(new VideoFrame(WIDTH, HEIGHT, pixels.clone()));
        }
    }

    private void renderScanline(int y) {
        sprite0HitCycle = -1;
        int offset = y * WIDTH;
        int backdropIndex = palette[0] & 0x3F;
        int backdrop = color(backdropIndex);
        Arrays.fill(pixels, offset, offset + WIDTH, backdrop);
        Arrays.fill(pixelIndices, offset, offset + WIDTH, backdropIndex);

        if ((mask & 0x18) == 0) {
            return;
        }

        int scrollX = (((t >> 10) & 0x01) << 8) | ((t & 0x1F) << 3) | fineX;
        int scrollY = (((t >> 5) & 0x1F) << 3) | ((t >> 12) & 0x07);
        if ((mask & 0x08) != 0) {
            renderBackground(y, offset, scrollX, scrollY);
        } else {
            Arrays.fill(rowBackground, 0);
        }

        if ((mask & 0x10) != 0) {
            renderSprites(y, offset);
        }
    }

    private void renderBackground(int y, int offset, int scrollX, int scrollY) {
        int patternBase = (control & 0x10) != 0 ? 0x1000 : 0x0000;
        boolean showLeft = (mask & 0x02) != 0;

        int backdropIndex = palette[0] & 0x3F;
        for (int x = 0; x < WIDTH; x++) {
            int colorIndex = backdropIndex;
            boolean opaque = false;
            if (showLeft || x >= 8) {
                int worldX = (scrollX + x) & 0x1FF;
                int worldY = (scrollY + y) % 480;
                int nt = ((worldX >> 8) & 1) | (((worldY / 240) & 1) << 1);
                int tileX = (worldX & 0xFF) >> 3;
                int tileY = (worldY % 240) >> 3;
                int ntBase = 0x2000 + physicalNametable(nt) * 0x400;
                int tile = readVram(ntBase + tileY * 32 + tileX) & 0xFF;
                int attribute = readVram(ntBase + 0x3C0 + (tileY >> 2) * 8 + (tileX >> 2)) & 0xFF;
                int paletteIndex = (attribute >> (((tileY & 2) << 1) | (tileX & 2))) & 0x03;
                int fineY = worldY & 0x07;
                int low = chrBus.read(patternBase + tile * 16 + fineY) & 0xFF;
                int high = chrBus.read(patternBase + tile * 16 + fineY + 8) & 0xFF;
                int bit = 7 - (worldX & 0x07);
                int color = ((low >> bit) & 1) | (((high >> bit) & 1) << 1);
                if (color != 0) {
                    // Palette entry 0x00 is opaque black, not transparent.
                    colorIndex = palette[paletteIndex * 4 + color] & 0x3F;
                    opaque = true;
                }
            }
            rowBackground[x] = opaque ? 1 : 0;
            if (opaque) {
                pixels[offset + x] = color(colorIndex);
                pixelIndices[offset + x] = colorIndex;
            }
        }
    }

    private void renderSprites(int y, int offset) {
        Arrays.fill(rowSprite, -1);
        int spriteHeight = (control & 0x20) != 0 ? 16 : 8;
        int count = 0;

        for (int i = 0; i < 64; i++) {
            int top = (oam[i * 4] & 0xFF) + 1;
            if (y < top || y >= top + spriteHeight) {
                continue;
            }

            if (count >= 8) {
                status |= STATUS_SPRITE_OVERFLOW;
                continue;
            }
            count++;

            int tile = oam[i * 4 + 1] & 0xFF;
            int attribute = oam[i * 4 + 2] & 0xFF;
            int spriteX = oam[i * 4 + 3] & 0xFF;
            int row = y - top;
            if ((attribute & 0x80) != 0) {
                row = spriteHeight - 1 - row;
            }

            int patternBase;
            if (spriteHeight == 16) {
                patternBase = (tile & 1) != 0 ? 0x1000 : 0x0000;
                tile &= 0xFE;
                if (row >= 8) {
                    tile++;
                    row -= 8;
                }
            } else {
                patternBase = (control & 0x08) != 0 ? 0x1000 : 0x0000;
            }

            int low = chrBus.read(patternBase + tile * 16 + row) & 0xFF;
            int high = chrBus.read(patternBase + tile * 16 + row + 8) & 0xFF;

            for (int px = 0; px < 8; px++) {
                int x = spriteX + px;
                if (x >= WIDTH) {
                    continue;
                }
                if (x < 8 && (mask & 0x04) == 0) {
                    continue;
                }
                int bit = (attribute & 0x40) != 0 ? px : 7 - px;
                int colorBits = ((low >> bit) & 1) | (((high >> bit) & 1) << 1);
                if (colorBits == 0) {
                    continue;
                }

                int paletteAddress = 0x10 + ((attribute & 0x03) << 2) + colorBits;
                int colorIndex = palette[paletteIndex(paletteAddress)] & 0x3F;

                if (i == 0 && rowBackground[x] != 0 && sprite0HitCycle < 0) {
                    // Спрайт 0 выставляется не в начале строки, а на пикселе,
                    // где спрайт перекрывает непрозрачный фон.
                    sprite0HitCycle = x + 1;
                }

                if (rowSprite[x] == -1) {
                    if ((attribute & 0x20) != 0 && rowBackground[x] != 0) {
                        rowSprite[x] = -2;
                    } else {
                        rowSprite[x] = colorIndex;
                    }
                }
            }
        }

        for (int x = 0; x < WIDTH; x++) {
            if (rowSprite[x] >= 0) {
                pixels[offset + x] = color(rowSprite[x]);
                pixelIndices[offset + x] = rowSprite[x];
            }
        }
    }

    private static byte[] powerUpPalette() {
        byte[] result = new byte[POWER_UP_PALETTE.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) POWER_UP_PALETTE[i];
        }
        return result;
    }

    private int color(int paletteIndex) {
        int index = paletteIndex & 0x3F;
        if ((mask & 0x01) != 0) {
            index &= 0x30;
        }
        int argb = NesPalette.ARGB[index];
        return applyEmphasis(argb);
    }

    private int applyEmphasis(int argb) {
        int emphasis = mask & 0xE0;
        if (emphasis == 0) {
            return argb;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        if ((emphasis & 0x20) != 0) {
            g = g * 3 / 4;
            b = b * 3 / 4;
        }
        if ((emphasis & 0x40) != 0) {
            r = r * 3 / 4;
            b = b * 3 / 4;
        }
        if ((emphasis & 0x80) != 0) {
            r = r * 3 / 4;
            g = g * 3 / 4;
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int readRegister(int register) {
        int result = switch (register) {
            case 2 -> {
                int value = (status & 0xE0) | (ioLatch & 0x1F);
                status &= ~STATUS_VBLANK;
                writeToggle = false;
                yield value;
            }
            case 4 -> oam[oamAddress] & 0xFF;
            case 7 -> readData();
            default -> ioLatch;
        };
        ioLatch = result;
        return result;
    }

    private void writeRegister(int register, int value) {
        int data = value & 0xFF;
        ioLatch = data;
        switch (register) {
            case 0 -> {
                boolean oldNmi = (control & 0x80) != 0;
                control = data;
                t = (t & 0x73FF) | ((data & 0x03) << 10);
                if (!oldNmi && (control & 0x80) != 0 && (status & STATUS_VBLANK) != 0) {
                    nmiPending = true;
                }
            }
            case 1 -> mask = data;
            case 3 -> oamAddress = data;
            case 4 -> {
                oam[oamAddress] = (byte) data;
                oamAddress = (oamAddress + 1) & 0xFF;
            }
            case 5 -> writeScroll(data);
            case 6 -> writeAddress(data);
            case 7 -> {
                writeVram(v & 0x3FFF, data);
                incrementV();
            }
            default -> {
                // PPUSTATUS только для чтения, $2002 write игнорируется.
            }
        }
    }

    private void writeScroll(int value) {
        if (!writeToggle) {
            t = (t & ~0x001F) | ((value >> 3) & 0x1F);
            fineX = value & 0x07;
            writeToggle = true;
        } else {
            t = (t & ~0x73E0) | ((value & 0xF8) << 2) | ((value & 0x07) << 12);
            writeToggle = false;
        }
    }

    private void writeAddress(int value) {
        if (!writeToggle) {
            t = (t & 0x00FF) | ((value & 0x3F) << 8);
            writeToggle = true;
        } else {
            t = (t & 0xFF00) | value;
            v = t & 0x7FFF;
            writeToggle = false;
        }
    }

    private int readData() {
        int address = v & 0x3FFF;
        int result;
        if (address >= 0x3F00) {
            // Чтение палитры возвращает полный байт: начальные значения
            // содержат биты, которые не являются индексом палитры.
            result = palette[paletteIndex(address)] & 0xFF;
            readBuffer = readVram(address - 0x1000) & 0xFF;
        } else {
            result = readBuffer;
            readBuffer = readVram(address) & 0xFF;
        }
        incrementV();
        return result;
    }

    private void incrementV() {
        int increment = (control & 0x04) != 0 ? 32 : 1;
        v = (v + increment) & 0x7FFF;
    }

    private int readVram(int address) {
        if (address < 0x2000) {
            return chrBus.read(address & 0x1FFF) & 0xFF;
        }
        if (address < 0x3F00) {
            return vram[nametableIndex(address)] & 0xFF;
        }
        return palette[paletteIndex(address)] & 0x3F;
    }

    private void writeVram(int address, int value) {
        if (address < 0x2000) {
            chrBus.write(address & 0x1FFF, value);
            return;
        }
        if (address < 0x3F00) {
            vram[nametableIndex(address)] = (byte) value;
            return;
        }
        palette[paletteIndex(address)] = (byte) (value & 0x3F);
    }

    private int nametableIndex(int address) {
        int normalized = address & 0x2FFF;
        int nt = (normalized >> 10) & 0x03;
        int offset = normalized & 0x03FF;
        return physicalNametable(nt) * 0x400 + offset;
    }

    private int physicalNametable(int nt) {
        return switch (mirroring) {
            case HORIZONTAL -> nt >> 1;
            case VERTICAL -> nt & 1;
            case SINGLE_SCREEN_LOWER -> 0;
            case SINGLE_SCREEN_UPPER -> 1;
            case FOUR_SCREEN -> nt;
        };
    }

    private static int paletteIndex(int address) {
        int index = address & 0x1F;
        if (index == 0x10 || index == 0x14 || index == 0x18 || index == 0x1C) {
            index -= 0x10;
        }
        return index;
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeInt(control);
        writer.writeInt(mask);
        writer.writeInt(status);
        writer.writeInt(oamAddress);
        writer.writeInt(v);
        writer.writeInt(t);
        writer.writeInt(fineX);
        writer.writeByte(writeToggle ? 1 : 0);
        writer.writeInt(readBuffer);
        writer.writeInt(ioLatch);
        writer.writeInt(scanline);
        writer.writeInt(cycle);
        writer.writeByte(nmiPending ? 1 : 0);
        writer.writeLong(frameCount);
        writer.writeByte(mirroring.ordinal());
        writer.writeBytes(vram);
        writer.writeBytes(palette);
        writer.writeBytes(oam);
    }

    @Override
    public void restore(StateReader reader) {
        control = reader.readInt();
        mask = reader.readInt();
        status = reader.readInt();
        oamAddress = reader.readInt();
        v = reader.readInt();
        t = reader.readInt();
        fineX = reader.readInt();
        writeToggle = reader.readByte() != 0;
        readBuffer = reader.readInt();
        ioLatch = reader.readInt();
        scanline = reader.readInt();
        cycle = reader.readInt();
        sprite0HitCycle = -1;
        nmiPending = reader.readByte() != 0;
        frameCount = reader.readLong();
        int mirroringOrdinal = reader.readByte();
        copyState(vram, reader.readBytes(), "VRAM");
        copyState(palette, reader.readBytes(), "palette");
        copyState(oam, reader.readBytes(), "OAM");
        updateMirroring(mirroringOrdinal);
    }

    private static void copyState(byte[] target, byte[] source, String name) {
        if (source.length != target.length) {
            throw new IllegalArgumentException(
                    name + " state size mismatch: expected " + target.length + ", got " + source.length);
        }
        System.arraycopy(source, 0, target, 0, target.length);
    }

    private void updateMirroring(int ordinal) {
        Mirroring[] values = Mirroring.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid mirroring ordinal " + ordinal);
        }
        mirroring = values[ordinal];
    }
}
