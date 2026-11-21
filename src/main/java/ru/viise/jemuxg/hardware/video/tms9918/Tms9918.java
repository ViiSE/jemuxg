package ru.viise.jemuxg.hardware.video.tms9918;

import java.util.Arrays;
import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * TMS9918A — видеопроцессор SG-1000.
 *
 * <p>Реализует 16 КБ VRAM, регистры R0-R7, порты данных и управления,
 * режимы Graphics I/II, Multicolor и Text, спрайты с коллизиями и
 * пятым спрайтом, VBlank-статус и IRQ.</p>
 *
 * <p>Алгоритмы рендеринга и работа с регистрами портированы из проекта
 * <a href="https://github.com/visrealm/vrEmuTms9918">vrEmuTms9918</a>
 * (MIT, Troy Schrapel). Особая благодарность автору.</p>
 */
public final class Tms9918 implements Stateful {

    public static final int WIDTH = 256;
    public static final int HEIGHT = 192;
    public static final int VRAM_SIZE = 16 * 1024;
    public static final int DOTS_PER_LINE = 342;

    public static final int STATUS_INT = 0x80;
    public static final int STATUS_5S = 0x40;
    public static final int STATUS_COL = 0x20;

    private static final int VRAM_MASK = VRAM_SIZE - 1;
    private static final int STATE_MAGIC = 0x544D5339;
    private static final int STATE_VERSION = 1;

    public enum Mode {
        GRAPHICS_I,
        GRAPHICS_II,
        MULTICOLOR,
        TEXT
    }

    private final byte[] vram = new byte[VRAM_SIZE];
    private final int[] registers = new int[8];
    private final byte[] framebufferIndices = new byte[WIDTH * HEIGHT];
    private final int[] framebufferArgb = new int[WIDTH * HEIGHT];
    private final byte[] rowSpriteBits = new byte[WIDTH];

    private int status;
    private int currentAddress;
    private int latchStage;
    private int latchValue;
    private int readAhead;
    private int line;
    private int dot;
    private long frame;
    private boolean irq;

    public Tms9918() {
        reset();
    }

    public void reset() {
        Arrays.fill(vram, (byte) 0);
        Arrays.fill(registers, 0);
        Arrays.fill(framebufferIndices, (byte) 0);
        Arrays.fill(framebufferArgb, Tms9918Palette.argb(0));
        status = 0;
        currentAddress = 0;
        latchStage = 0;
        latchValue = 0;
        readAhead = 0;
        line = 0;
        dot = 0;
        frame = 0;
        irq = false;
    }

    /**
     * Начало кадра: очищает флаги пятого спрайта и коллизии, сохраняя
     * необработанный VBlank-флаг предыдущего кадра.
     */
    public void beginFrame() {
        status &= STATUS_INT;
        line = 0;
        dot = 0;
    }

    /**
     * Начало строки. На строке 192 выставляется VBlank-флаг и, если
     * разрешено прерывание, поднимается IRQ.
     */
    public void beginLine(int line) {
        this.line = line;
        this.dot = 0;
        if (line == 192) {
            status |= STATUS_INT;
            if ((registers[1] & 0x20) != 0) {
                irq = true;
            }
        }
    }

    /**
     * Продвигает счётчик точек внутри строки. Машина вызывает этот метод
     * на каждом отрезке тактов CPU, чтобы отладочный снимок видел
     * реальную позицию луча. Полная строка — {@value #DOTS_PER_LINE} точек.
     */
    public void advanceDots(int dots) {
        if (dots < 0) {
            throw new IllegalArgumentException("dots must be non-negative: " + dots);
        }
        this.dot += dots;
        while (this.dot >= DOTS_PER_LINE) {
            this.dot -= DOTS_PER_LINE;
        }
    }

    /**
     * Конец кадра: увеличивает счётчик кадров.
     */
    public void endFrame() {
        frame++;
    }

    public void writeData(int value) {
        latchStage = 0;
        vram[currentAddress & VRAM_MASK] = (byte) value;
        readAhead = value & 0xFF;
        currentAddress = (currentAddress + 1) & VRAM_MASK;
    }

    public void writeControl(int value) {
        value &= 0xFF;
        if (latchStage == 0) {
            latchValue = value;
            latchStage = 1;
            return;
        }
        latchStage = 0;
        if ((value & 0x80) != 0) {
            registers[value & 0x07] = latchValue;
            return;
        }
        currentAddress = (latchValue | ((value & 0x3F) << 8)) & VRAM_MASK;
        if ((value & 0x40) == 0) {
            readAhead = vram[currentAddress] & 0xFF;
            currentAddress = (currentAddress + 1) & VRAM_MASK;
        }
    }

    public int readData() {
        latchStage = 0;
        int result = readAhead & 0xFF;
        readAhead = vram[currentAddress & VRAM_MASK] & 0xFF;
        currentAddress = (currentAddress + 1) & VRAM_MASK;
        return result;
    }

    /**
     * Чтение статуса сбрасывает все флаги и IRQ.
     */
    public int readStatus() {
        latchStage = 0;
        int result = status;
        status = 0;
        irq = false;
        return result;
    }

    /**
     * Рендерит одну видимую строку в индексированный и ARGB-буферы.
     */
    public void renderLine(int y) {
        if (y < 0 || y >= HEIGHT) {
            return;
        }
        int base = y * WIDTH;
        Mode mode = mode();
        if (!displayEnabled()) {
            fillLine(base, backdrop());
        } else {
            switch (mode) {
                case GRAPHICS_I -> renderGraphicsI(y, base);
                case GRAPHICS_II -> renderGraphicsII(y, base);
                case MULTICOLOR -> renderMulticolor(y, base);
                case TEXT -> renderText(y, base);
            }
            if (mode != Mode.TEXT) {
                renderSprites(y, base);
            }
        }
        for (int i = 0; i < WIDTH; i++) {
            framebufferArgb[base + i] = Tms9918Palette.argb(
                    framebufferIndices[base + i] & 0xFF);
        }
    }

    private void renderGraphicsI(int y, int base) {
        int tileY = y >> 3;
        int row = y & 0x07;
        int names = nameBase() + tileY * 32;
        int patterns = patternBase();
        int colors = colorBase();
        for (int tileX = 0; tileX < 32; tileX++) {
            int name = vram[(names + tileX) & VRAM_MASK] & 0xFF;
            int pattern = vram[(patterns + name * 8 + row) & VRAM_MASK] & 0xFF;
            int color = vram[(colors + (name >> 3)) & VRAM_MASK] & 0xFF;
            int fg = fgColor(color);
            int bg = bgColor(color);
            int x = base + tileX * 8;
            for (int bit = 0; bit < 8; bit++) {
                framebufferIndices[x + bit] = (byte) ((pattern & (0x80 >> bit)) != 0 ? fg : bg);
            }
        }
    }

    private void renderGraphicsII(int y, int base) {
        int tileY = y >> 3;
        int row = y & 0x07;
        int nameMask = ((registers[3] & 0x7F) << 3) | 0x07;
        int pageThird = ((tileY & 0x18) >> 3) & (registers[4] & 0x03);
        int pageOffset = pageThird << 11;
        int patterns = patternBase() + pageOffset;
        int colors = colorBase() + (pageOffset & ((registers[3] & 0x60) << 6));
        int names = nameBase() + tileY * 32;
        for (int tileX = 0; tileX < 32; tileX++) {
            int name = (vram[(names + tileX) & VRAM_MASK] & 0xFF) & nameMask;
            int offset = name * 8 + row;
            int pattern = vram[(patterns + offset) & VRAM_MASK] & 0xFF;
            int color = vram[(colors + offset) & VRAM_MASK] & 0xFF;
            int fg = fgColor(color);
            int bg = bgColor(color);
            int x = base + tileX * 8;
            for (int bit = 0; bit < 8; bit++) {
                framebufferIndices[x + bit] = (byte) ((pattern & (0x80 >> bit)) != 0 ? fg : bg);
            }
        }
    }

    private void renderMulticolor(int y, int base) {
        int tileY = y >> 3;
        int patternRow = ((y >> 2) & 1) + ((tileY & 0x03) * 2);
        int names = nameBase() + tileY * 32;
        int patterns = patternBase();
        for (int tileX = 0; tileX < 32; tileX++) {
            int name = vram[(names + tileX) & VRAM_MASK] & 0xFF;
            int colorByte = vram[(patterns + name * 8 + patternRow) & VRAM_MASK] & 0xFF;
            int left = fgColor(colorByte);
            int right = bgColor(colorByte);
            int x = base + tileX * 8;
            for (int i = 0; i < 4; i++) {
                framebufferIndices[x + i] = (byte) left;
                framebufferIndices[x + 4 + i] = (byte) right;
            }
        }
    }

    private void renderText(int y, int base) {
        int tileY = y >> 3;
        int row = y & 0x07;
        int bg = bgColor(registers[7]);
        int fg = fgColor(registers[7]);
        fillLine(base, bg);
        int names = nameBase() + tileY * 40;
        int patterns = patternBase();
        for (int tileX = 0; tileX < 40; tileX++) {
            int name = vram[(names + tileX) & VRAM_MASK] & 0xFF;
            int pattern = vram[(patterns + name * 8 + row) & VRAM_MASK] & 0xFF;
            int x = base + 8 + tileX * 6;
            for (int bit = 0; bit < 6; bit++) {
                framebufferIndices[x + bit] =
                        (byte) ((pattern & (0x80 >> bit)) != 0 ? fg : bg);
            }
        }
    }

    private void renderSprites(int y, int base) {
        Arrays.fill(rowSpriteBits, (byte) 0);
        int attributes = (registers[5] & 0x7F) << 7;
        int patterns = (registers[6] & 0x07) << 11;
        int size = (registers[1] & 0x02) != 0 ? 16 : 8;
        boolean magnified = (registers[1] & 0x01) != 0;
        int shown = 0;

        for (int index = 0; index < 32; index++) {
            int attribute = (attributes + index * 4) & VRAM_MASK;
            int yPos = vram[attribute] & 0xFF;
            if (yPos == 0xD0) {
                if ((status & STATUS_5S) == 0) {
                    status |= index & 0x1F;
                }
                break;
            }
            if (yPos > 0xE0) {
                yPos -= 256;
            }
            yPos += 1;
            int patternRow = y - yPos;
            if (magnified) {
                patternRow >>= 1;
            }
            if (patternRow < 0 || patternRow >= size) {
                continue;
            }

            shown++;
            if (shown > 4) {
                if ((status & STATUS_5S) == 0) {
                    status |= STATUS_5S | (index & 0x1F);
                }
                break;
            }

            int colorControl = vram[(attribute + 3) & VRAM_MASK] & 0xFF;
            int color = colorControl & 0x0F;
            int patternIndex = vram[(attribute + 2) & VRAM_MASK] & 0xFF;
            int earlyClock = (colorControl & 0x80) != 0 ? -32 : 0;
            int xPos = (vram[(attribute + 1) & VRAM_MASK] & 0xFF) + earlyClock;
            int patternOffset = (patterns + patternIndex * 8 + patternRow) & VRAM_MASK;
            int patternByte = vram[patternOffset] & 0xFF;
            int screenBit = 0;
            int patternBit = 0;
            int endX = Math.min(WIDTH, xPos + size * (magnified ? 2 : 1));

            for (int screenX = xPos; screenX < endX; screenX++, screenBit++) {
                if (screenX >= 0 && (patternByte & 0x80) != 0) {
                    if (color != 0 && (rowSpriteBits[screenX] & 0xFF) < 2) {
                        framebufferIndices[base + screenX] = (byte) color;
                    }
                    if (rowSpriteBits[screenX] != 0) {
                        status |= STATUS_COL;
                    } else {
                        rowSpriteBits[screenX] = (byte) (color + 1);
                    }
                }
                if (!magnified || (screenBit & 1) == 1) {
                    patternByte = (patternByte << 1) & 0xFF;
                    patternBit++;
                    if (patternBit == 8 && size == 16) {
                        patternBit = 0;
                        patternByte = vram[(patternOffset + 16) & VRAM_MASK] & 0xFF;
                    }
                }
            }
        }
    }

    private void fillLine(int base, int color) {
        Arrays.fill(framebufferIndices, base, base + WIDTH, (byte) color);
    }

    public Mode mode() {
        if ((registers[0] & 0x02) != 0) {
            return Mode.GRAPHICS_II;
        }
        return switch ((registers[1] & 0x18) >> 3) {
            case 0 -> Mode.GRAPHICS_I;
            case 1 -> Mode.MULTICOLOR;
            case 2 -> Mode.TEXT;
            default -> Mode.GRAPHICS_I;
        };
    }

    public boolean displayEnabled() {
        return (registers[1] & 0x40) != 0;
    }

    public int nameBase() {
        return (registers[2] & 0x0F) << 10;
    }

    public int colorBase() {
        if (mode() == Mode.GRAPHICS_II) {
            return (registers[3] & 0x80) << 6;
        }
        return (registers[3] & 0xFF) << 6;
    }

    public int patternBase() {
        if (mode() == Mode.GRAPHICS_II) {
            return (registers[4] & 0x04) << 11;
        }
        return (registers[4] & 0x07) << 11;
    }

    public int spriteAttributeBase() {
        return (registers[5] & 0x7F) << 7;
    }

    public int spritePatternBase() {
        return (registers[6] & 0x07) << 11;
    }

    private int backdrop() {
        return registers[7] & 0x0F;
    }

    private int fgColor(int colorByte) {
        int color = (colorByte >> 4) & 0x0F;
        return color == 0 ? backdrop() : color;
    }

    private int bgColor(int colorByte) {
        int color = colorByte & 0x0F;
        return color == 0 ? backdrop() : color;
    }

    public int register(int index) {
        return registers[index & 0x07];
    }

    public void setRegister(int index, int value) {
        registers[index & 0x07] = value & 0xFF;
    }

    public int vramValue(int address) {
        return vram[address & VRAM_MASK] & 0xFF;
    }

    public void setVram(int address, int value) {
        vram[address & VRAM_MASK] = (byte) value;
    }

    public int status() {
        return status;
    }

    public boolean irq() {
        return irq;
    }

    public int currentAddress() {
        return currentAddress;
    }

    public int line() {
        return line;
    }

    public int dot() {
        return dot;
    }

    public long frame() {
        return frame;
    }

    /**
     * Копия VRAM для отладочных снимков.
     */
    public byte[] vramSnapshot() {
        return vram.clone();
    }

    public byte[] framebufferIndices() {
        return framebufferIndices;
    }

    public int[] framebufferArgb() {
        return framebufferArgb;
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeInt(STATE_MAGIC);
        writer.writeByte(STATE_VERSION);
        writer.writeBytes(vram);
        for (int register : registers) {
            writer.writeByte(register);
        }
        writer.writeByte(status);
        writer.writeShort(currentAddress);
        writer.writeByte(latchStage);
        writer.writeByte(latchValue);
        writer.writeByte(readAhead);
        writer.writeShort(line);
        writer.writeShort(dot);
        writer.writeLong(frame);
        writer.writeByte(irq ? 1 : 0);
        writer.writeBytes(framebufferIndices);
    }

    @Override
    public void restore(StateReader reader) {
        if (reader.readInt() != STATE_MAGIC) {
            throw new IllegalArgumentException("not a TMS9918 save state");
        }
        int version = reader.readByte();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported TMS9918 save state version: " + version);
        }
        byte[] savedVram = reader.readBytes();
        if (savedVram.length != VRAM_SIZE) {
            throw new IllegalArgumentException(
                    "TMS9918 VRAM size mismatch: " + savedVram.length);
        }
        System.arraycopy(savedVram, 0, vram, 0, VRAM_SIZE);
        for (int i = 0; i < registers.length; i++) {
            registers[i] = reader.readByte();
        }
        status = reader.readByte();
        currentAddress = reader.readShort() & VRAM_MASK;
        latchStage = reader.readByte();
        latchValue = reader.readByte();
        readAhead = reader.readByte();
        line = reader.readShort();
        dot = reader.readShort();
        frame = reader.readLong();
        irq = reader.readByte() != 0;
        byte[] savedFrame = reader.readBytes();
        if (savedFrame.length != framebufferIndices.length) {
            throw new IllegalArgumentException(
                    "TMS9918 framebuffer size mismatch: " + savedFrame.length);
        }
        System.arraycopy(savedFrame, 0, framebufferIndices, 0, framebufferIndices.length);
        for (int i = 0; i < framebufferIndices.length; i++) {
            framebufferArgb[i] = Tms9918Palette.argb(framebufferIndices[i] & 0xFF);
        }
    }
}
