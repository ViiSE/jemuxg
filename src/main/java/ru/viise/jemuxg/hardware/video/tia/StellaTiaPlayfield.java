package ru.viise.jemuxg.hardware.video.tia;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Порт Playfield TIA из проекта Stella.
 *
 * <p>Повторяет 20-битный шаблон PF0/PF1/PF2, режимы CTRLPF (reflect,
 * score, priority), защёлку отражения на x=0/79 и цвета
 * COLUPF/COLUP0/COLUP1.</p>
 *
 * <p>Playfield тикает с частотой 1/4 цветового такта: пиксель вычисляется
 * только при {@code x % 4 == 0}, а между этими тактами значение {@link #isOn()}
 * сохраняется, как в Stella.</p>
 *
 * <p>Реализация основана на исходном коде
 * <a href="https://github.com/stella-emu/stella">Stella</a>
 * ({@code Playfield.hxx/.cxx}). Особая благодарность авторам проекта.</p>
 */
public final class StellaTiaPlayfield {

    public static final int WIDTH = 160;
    public static final int HALF = WIDTH / 2;

    /** CTRLPF D2=0, D1=0: P0/M0 => P1/M1 => BL/PF => BK. */
    public static final int PRIORITY_NORMAL = 0;
    /** CTRLPF D2=0, D1=1: P0/M0 => PF/P1/M1 => BL => BK. */
    public static final int PRIORITY_SCORE = 1;
    /** CTRLPF D2=1: BL/PF => P0/M0 => P1/M1 => BK. */
    public static final int PRIORITY_PFP = 2;

    private int pattern;
    private int effectivePattern;

    private int pf0;
    private int pf1;
    private int pf2;

    private boolean reflected;
    private boolean refp;
    private int priority = PRIORITY_NORMAL;
    private boolean scoreMode;

    private int colupf;
    private int colup0;
    private int colup1;
    private int colorLeft;
    private int colorRight;

    private int x;
    private boolean on;

    public StellaTiaPlayfield() {
        reset();
    }

    public void reset() {
        pattern = 0;
        effectivePattern = 0;
        pf0 = 0;
        pf1 = 0;
        pf2 = 0;
        reflected = false;
        refp = false;
        priority = PRIORITY_NORMAL;
        scoreMode = false;
        colupf = 0;
        colup0 = 0;
        colup1 = 0;
        colorLeft = 0;
        colorRight = 0;
        x = 0;
        on = false;
        applyColors();
    }

    /** PF0 write: D4..D7 become pattern bits 0..3. */
    public void pf0(int value) {
        value &= 0xFF;
        int nibble = value >>> 4;
        if (pf0 == nibble) {
            return;
        }
        pf0 = nibble;
        pattern = (pattern & 0x000FFFF0) | nibble;
        updatePattern();
    }

    /** PF1 write: the 8 bits are shuffled into pattern bits 4..11. */
    public void pf1(int value) {
        value &= 0xFF;
        if (pf1 == value) {
            return;
        }
        pf1 = value;
        pattern = (pattern & 0x000FF00F)
                | ((value & 0x80) >>> 3)
                | ((value & 0x40) >>> 1)
                | ((value & 0x20) << 1)
                | ((value & 0x10) << 3)
                | ((value & 0x08) << 5)
                | ((value & 0x04) << 7)
                | ((value & 0x02) << 9)
                | ((value & 0x01) << 11);
        updatePattern();
    }

    /** PF2 write: D0..D7 become pattern bits 12..19. */
    public void pf2(int value) {
        value &= 0xFF;
        if (pf2 == value) {
            return;
        }
        pf2 = value;
        pattern = (pattern & 0x00000FFF) | (value << 12);
        updatePattern();
    }

    /** CTRLPF write: D0 reflect, D1 score, D2 priority. */
    public void ctrlpf(int value) {
        value &= 0xFF;
        reflected = (value & 0x01) != 0;
        scoreMode = (value & 0x06) == 0x02;
        priority = (value & 0x04) != 0
                ? PRIORITY_PFP
                : (value & 0x02) != 0 ? PRIORITY_SCORE : PRIORITY_NORMAL;
        applyColors();
    }

    /** COLUPF write (normal mode color for both halves). */
    public void setColor(int value) {
        colupf = value & 0xFE;
        applyColors();
    }

    /** COLUP0 write (left half in score mode). */
    public void setColorP0(int value) {
        colup0 = value & 0xFE;
        applyColors();
    }

    /** COLUP1 write (right half in score mode). */
    public void setColorP1(int value) {
        colup1 = value & 0xFE;
        applyColors();
    }

    /** Tick one color clock at visible x (0..159). */
    public void tick(int visibleX) {
        x = visibleX;

        // Reflect is latched only at the start of the line and at x=79.
        if (x == 0 || x == HALF - 1) {
            refp = reflected;
        }

        // The playfield runs at 1/4 the color clock rate.
        if ((x & 0x03) != 0) {
            return;
        }

        int currentPixel;
        if (effectivePattern == 0) {
            currentPixel = 0;
        } else if (x < HALF - 1) {
            currentPixel = effectivePattern & (1 << (x >>> 2));
        } else if (refp) {
            int column = x >>> 2;
            currentPixel = column <= 39
                    ? effectivePattern & (1 << (39 - column))
                    : 0;
        } else {
            currentPixel = effectivePattern & (1 << ((x >>> 2) - 20));
        }

        on = currentPixel != 0;
    }

    /** Called when a scanline ends. */
    public void nextLine() {
        on = false;
    }

    public boolean isOn() {
        return on;
    }

    /** Current pixel color, matching Stella's {@code Playfield::getColor()}. */
    public int color() {
        return colorAt(x);
    }

    public int colorAt(int visibleX) {
        return visibleX < HALF ? colorLeft : colorRight;
    }

    public int pattern() {
        return pattern;
    }

    public int pf0() {
        return pf0;
    }

    public int pf1() {
        return pf1;
    }

    public int pf2() {
        return pf2;
    }

    public boolean reflected() {
        return reflected;
    }

    /** Reflect flag currently latched for this line. */
    public boolean refp() {
        return refp;
    }

    public boolean scoreMode() {
        return scoreMode;
    }

    public int priority() {
        return priority;
    }

    public int x() {
        return x;
    }

    private void applyColors() {
        if (scoreMode) {
            colorLeft = colup0;
            colorRight = colup1;
        } else {
            colorLeft = colupf;
            colorRight = colupf;
        }
    }

    private void updatePattern() {
        effectivePattern = pattern;
    }

    void save(StateWriter writer) {
        writer.writeInt(pattern);
        writer.writeInt(effectivePattern);
        writer.writeByte(pf0);
        writer.writeByte(pf1);
        writer.writeByte(pf2);
        writer.writeByte(reflected ? 1 : 0);
        writer.writeByte(refp ? 1 : 0);
        writer.writeByte(priority);
        writer.writeByte(scoreMode ? 1 : 0);
        writer.writeByte(colupf);
        writer.writeByte(colup0);
        writer.writeByte(colup1);
        writer.writeByte(colorLeft);
        writer.writeByte(colorRight);
        writer.writeShort(x);
        writer.writeByte(on ? 1 : 0);
    }

    void restore(StateReader reader) {
        pattern = reader.readInt();
        effectivePattern = reader.readInt();
        pf0 = reader.readByte();
        pf1 = reader.readByte();
        pf2 = reader.readByte();
        reflected = reader.readByte() != 0;
        refp = reader.readByte() != 0;
        priority = reader.readByte();
        scoreMode = reader.readByte() != 0;
        colupf = reader.readByte();
        colup0 = reader.readByte();
        colup1 = reader.readByte();
        colorLeft = reader.readByte();
        colorRight = reader.readByte();
        x = reader.readShort();
        on = reader.readByte() != 0;
    }
}
