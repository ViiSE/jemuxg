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
 * {@code VSYNC}, {@code VBLANK} и {@code WSYNC}, а также палитра TIA.
 * Playfield, игроки, ракеты и мяч появятся на следующих этапах.</p>
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

    private static final int VSYNC_BIT = 0x02;
    private static final int VBLANK_BIT = 0x02;

    private final int[] pixels = new int[
            Atari2600Constants.VISIBLE_WIDTH * Atari2600Constants.VISIBLE_HEIGHT];

    private final byte[] pixelIndices = new byte[pixels.length];

    private VideoSink sink;
    private int vsyncRegister;
    private int vblankRegister;
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
            default -> {
                // Остальные регистры TIA будут добавлены на этапах 4.2-4.6.
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
            }
        }
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
        writer.writeShort(scanline);
        writer.writeShort(colorClock);
        writer.writeByte(wsync ? 1 : 0);
        writer.writeLong(frameCount);
    }

    @Override
    public void restore(StateReader reader) {
        vsyncRegister = reader.readByte();
        vblankRegister = reader.readByte();
        scanline = reader.readShort();
        colorClock = reader.readShort();
        wsync = reader.readByte() != 0;
        frameCount = reader.readLong();
    }
}
