package ru.viise.jemuxg.hardware.video.tia;

import java.util.Arrays;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Каркас видеочасти TIA для NTSC Atari 2600.
 *
 * <p>Сейчас реализованы тайминги развёртки, служебные регистры
 * {@code VSYNC}, {@code VBLANK} и {@code WSYNC}, палитра TIA и playfield.
 * Игроки, ракеты и мяч появятся на следующих этапах.</p>
 *
 * <p>Метод {@link #tick(long)} принимает количество <b>цветовых тактов</b>
 * TIA. Один такт CPU равен трём цветовым тактам.</p>
 */
public final class Tia implements TiaVideo {

    /** VSYNC: вертикальная синхронизация (бит 1). */
    public static final int VSYNC = 0x00;

    /** VBLANK: вертикальное гашение (бит 1). */
    public static final int VBLANK = 0x01;

    /** WSYNC: остановка CPU до конца текущей строки. */
    public static final int WSYNC = 0x02;

    /** COLUP0: цвет игрока 0 (также левая половина playfield в score mode). */
    public static final int COLUP0 = 0x06;

    /** COLUP1: цвет игрока 1 (также правая половина playfield в score mode). */
    public static final int COLUP1 = 0x07;

    /** COLUPF: цвет playfield и мяча. */
    public static final int COLUPF = 0x08;

    /** COLUBK: цвет фона. */
    public static final int COLUBK = 0x09;

    /** CTRLPF: отражение, score mode, приоритет и размер мяча. */
    public static final int CTRLPF = 0x0A;

    /** PF0: младшие 4 бита playfield (биты 4-7 регистра). */
    public static final int PF0 = 0x0D;

    /** PF1: средние 8 бит playfield. */
    public static final int PF1 = 0x0E;

    /** PF2: старшие 8 бит playfield. */
    public static final int PF2 = 0x0F;

    private static final int VSYNC_BIT = 0x02;
    private static final int VBLANK_BIT = 0x02;

    private final int[] pixels = new int[
            Atari2600Constants.VISIBLE_WIDTH * Atari2600Constants.VISIBLE_HEIGHT];

    private final byte[] pixelIndices = new byte[pixels.length];

    private VideoSink sink;
    private int vsyncRegister;
    private int vblankRegister;
    private int coluP0;
    private int coluP1;
    private int coluPf;
    private int coluBk;
    private int ctrlPf;
    private int pf0;
    private int pf1;
    private int pf2;
    private int scanline;
    private int colorClock;
    private boolean wsync;
    private long frameCount;

    public Tia() {
        clearFramebuffer();
    }

    @Override
    public Bus registers() {
        return new Bus() {
            @Override
            public int read(int address) {
                return readRegister(address & Atari2600Constants.TIA_ADDRESS_MASK);
            }

            @Override
            public void write(int address, int value) {
                writeRegister(address & Atari2600Constants.TIA_ADDRESS_MASK, value);
            }
        };
    }

    private int readRegister(int register) {
        return switch (register) {
            case VSYNC -> vsyncRegister;
            case VBLANK -> vblankRegister;
            default -> 0;
        };
    }

    private void writeRegister(int register, int value) {
        switch (register) {
            case VSYNC -> vsyncRegister = value & 0xFF;
            case VBLANK -> vblankRegister = value & 0xFF;
            case WSYNC -> wsync = true;
            case COLUP0 -> coluP0 = value & 0x7F;
            case COLUP1 -> coluP1 = value & 0x7F;
            case COLUPF -> coluPf = value & 0x7F;
            case COLUBK -> coluBk = value & 0x7F;
            case CTRLPF -> ctrlPf = value & 0xFF;
            case PF0 -> pf0 = value & 0xFF;
            case PF1 -> pf1 = value & 0xFF;
            case PF2 -> pf2 = value & 0xFF;
            default -> {
                // Остальные регистры TIA будут добавлены на этапах 4.4-4.6.
            }
        }
    }

    /**
     * @return включена ли вертикальная синхронизация
     */
    public boolean vsync() {
        return (vsyncRegister & VSYNC_BIT) != 0;
    }

    /**
     * @return включено ли вертикальное гашение
     */
    public boolean vblank() {
        return (vblankRegister & VBLANK_BIT) != 0;
    }

    /**
     * @return остановлен ли CPU записью в WSYNC до конца строки
     */
    @Override
    public boolean cpuHalted() {
        return wsync;
    }

    @Override
    public String name() {
        return "TIA";
    }

    @Override
    public void reset() {
        vsyncRegister = 0;
        vblankRegister = 0;
        coluP0 = 0;
        coluP1 = 0;
        coluPf = 0;
        coluBk = 0;
        ctrlPf = 0;
        pf0 = 0;
        pf1 = 0;
        pf2 = 0;
        scanline = 0;
        colorClock = 0;
        wsync = false;
        frameCount = 0;
        clearFramebuffer();
    }

    private void clearFramebuffer() {
        Arrays.fill(pixels, TiaPalette.ARGB[0]);
        Arrays.fill(pixelIndices, (byte) 0);
    }

    @Override
    public void connect(VideoSink sink) {
        this.sink = sink;
    }

    @Override
    public int width() {
        return Atari2600Constants.VISIBLE_WIDTH;
    }

    @Override
    public int height() {
        return Atari2600Constants.VISIBLE_HEIGHT;
    }

    @Override
    public int scanline() {
        return scanline;
    }

    @Override
    public int colorClock() {
        return colorClock;
    }

    public long frameCount() {
        return frameCount;
    }

    public int[] framebufferArgb() {
        return pixels.clone();
    }

    /**
     * @return копия индексного framebuffer (0..127)
     */
    public byte[] framebufferIndices() {
        return pixelIndices.clone();
    }

    @Override
    public void tick(long colorClocks) {
        if (colorClocks < 0) {
            throw new IllegalArgumentException("colorClocks must be >= 0");
        }
        for (long i = 0; i < colorClocks; i++) {
            if (scanline == 0 && colorClock == 0) {
                clearFramebuffer();
            }
            colorClock++;
            if (colorClock >= Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE) {
                colorClock = 0;
                scanline++;
                wsync = false;
                if (scanline >= Atari2600Constants.SCANLINES_PER_FRAME) {
                    scanline = 0;
                    frameCount++;
                    emitFrame();
                }
            } else {
                renderPixel();
            }
        }
    }

    private void renderPixel() {
        int x = colorClock - Atari2600Constants.VISIBLE_COLOR_CLOCK_START;
        if (x < 0 || x >= Atari2600Constants.VISIBLE_WIDTH) {
            return;
        }
        if (scanline < Atari2600Constants.VISIBLE_SCANLINE_START
                || scanline >= Atari2600Constants.VISIBLE_SCANLINE_START
                + Atari2600Constants.VISIBLE_HEIGHT) {
            return;
        }

        int y = scanline - Atari2600Constants.VISIBLE_SCANLINE_START;
        int colorIndex;
        if (vblank()) {
            colorIndex = 0;
        } else if (playfieldBit(x) != 0) {
            if ((ctrlPf & 0x06) == 0x02) {
                colorIndex = (x < Atari2600Constants.VISIBLE_WIDTH / 2 ? coluP0 : coluP1) & 0x7F;
            } else {
                colorIndex = coluPf & 0x7F;
            }
        } else {
            colorIndex = coluBk & 0x7F;
        }

        int offset = y * Atari2600Constants.VISIBLE_WIDTH + x;
        pixels[offset] = TiaPalette.ARGB[colorIndex];
        pixelIndices[offset] = (byte) colorIndex;
    }

    /**
     * Возвращает бит 20-битного playfield для пикселя x (0..159).
     */
    private int playfieldBit(int x) {
        int position;
        if (x < Atari2600Constants.VISIBLE_WIDTH / 2) {
            position = x >> 2;
        } else if ((ctrlPf & 0x01) != 0) {
            position = 19 - ((x - Atari2600Constants.VISIBLE_WIDTH / 2) >> 2);
        } else {
            position = (x - Atari2600Constants.VISIBLE_WIDTH / 2) >> 2;
        }

        if (position < 4) {
            return (pf0 >> (4 + position)) & 1;
        }
        if (position < 12) {
            return (pf1 >> (11 - position)) & 1;
        }
        return (pf2 >> (position - 12)) & 1;
    }

    /**
     * @return текущее значение CTRLPF
     */
    public int controlPf() {
        return ctrlPf;
    }

    private void emitFrame() {
        if (sink != null) {
            sink.frameReady(new VideoFrame(
                    Atari2600Constants.VISIBLE_WIDTH,
                    Atari2600Constants.VISIBLE_HEIGHT,
                    pixels.clone()));
        }
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeByte(vsyncRegister);
        writer.writeByte(vblankRegister);
        writer.writeByte(coluP0);
        writer.writeByte(coluP1);
        writer.writeByte(coluPf);
        writer.writeByte(coluBk);
        writer.writeByte(ctrlPf);
        writer.writeByte(pf0);
        writer.writeByte(pf1);
        writer.writeByte(pf2);
        writer.writeShort(scanline);
        writer.writeShort(colorClock);
        writer.writeByte(wsync ? 1 : 0);
        writer.writeLong(frameCount);
    }

    @Override
    public void restore(StateReader reader) {
        vsyncRegister = reader.readByte();
        vblankRegister = reader.readByte();
        coluP0 = reader.readByte();
        coluP1 = reader.readByte();
        coluPf = reader.readByte();
        coluBk = reader.readByte();
        ctrlPf = reader.readByte();
        pf0 = reader.readByte();
        pf1 = reader.readByte();
        pf2 = reader.readByte();
        scanline = reader.readShort();
        colorClock = reader.readShort();
        wsync = reader.readByte() != 0;
        frameCount = reader.readLong();
    }
}
