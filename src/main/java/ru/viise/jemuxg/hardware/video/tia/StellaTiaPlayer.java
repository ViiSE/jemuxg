package ru.viise.jemuxg.hardware.video.tia;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Порт Player TIA из проекта Stella.
 *
 * <p>Повторяет horizontal counter, декоды NUSIZ, sample/render counter
 * и VDEL-буферы.</p>
 *
 * <p>Реализация основана на исходном коде
 * <a href="https://github.com/stella-emu/stella">Stella</a>
 * ({@code Player.hxx/.cxx}). Особая благодарность авторам проекта.</p>
 */
public final class StellaTiaPlayer {

    public static final int WIDTH = 160;
    private static final int RENDER_OFFSET = -5;

    private final int[] decodes = new int[WIDTH];
    private int counter;
    private int renderCounter;
    private int renderCounterTripPoint;
    private int sampleCounter;
    private int divider = 1;
    private int dividerPending = 1;
    private int dividerChangeCounter = -1;
    private int copy;
    private boolean rendering;
    private boolean on;
    private int patternNew;
    private int patternOld;
    private int pattern;
    private boolean delaying;
    private boolean reflected;
    private int nusiz;
    private int hmpClocks;
    private int objectColor;
    private int color;
    private boolean moving;

    public StellaTiaPlayer() {
        reset();
    }

    public void reset() {
        counter = 0;
        renderCounter = 0;
        renderCounterTripPoint = 0;
        sampleCounter = 0;
        divider = 1;
        dividerPending = 1;
        dividerChangeCounter = -1;
        copy = 0;
        rendering = false;
        on = false;
        patternNew = 0;
        patternOld = 0;
        pattern = 0;
        delaying = false;
        reflected = false;
        nusiz = 0;
        hmpClocks = 0;
        objectColor = 0;
        color = 0;
        moving = false;
        setDecodes(0);
    }

    public void grp(int value) {
        patternNew = value & 0xFF;
        if (!delaying) {
            updatePattern();
        }
    }

    public void shufflePatterns() {
        patternOld = patternNew;
        if (delaying) {
            updatePattern();
        }
    }

    public void setVdel(int value) {
        delaying = (value & 0x01) != 0;
        updatePattern();
    }

    public void setRefp(int value) {
        reflected = (value & 0x08) != 0;
        updatePattern();
    }

    public void setNusiz(int value) {
        nusiz = value & 0xFF;
        int mode = nusiz & 0x07;
        dividerPending = mode == 5 ? 2 : mode == 7 ? 4 : 1;
        dividerChangeCounter = -1;
        setDecodes(mode);
    }

    public int nusiz() {
        return nusiz;
    }

    public int patternNew() {
        return patternNew;
    }

    public int patternOld() {
        return patternOld;
    }

    public boolean reflected() {
        return reflected;
    }

    public boolean delaying() {
        return delaying;
    }

    public void hmp(int value) {
        hmpClocks = ((value >>> 4) ^ 0x08) & 0x0F;
    }

    public void startMovement() {
        moving = true;
    }

    /**
     * Every fourth color clock while HMOVE is active. Ticks only while the
     * TIA is in HBLANK; outside HBLANK the regular clock keeps the phase.
     */
    public void movementTick(int clock, int hclock, boolean hblank) {
        if (!moving) {
            return;
        }
        if (clock == hmpClocks) {
            moving = false;
            return;
        }
        if (hblank) {
            tick();
        }
    }

    public boolean isMoving() {
        return moving;
    }

    public void setColor(int value) {
        objectColor = value & 0xFE;
        color = objectColor;
    }

    public int color() {
        return color;
    }

    public int hmpClocks() {
        return hmpClocks;
    }

    /**
     * FSTOB condition: main copy is being drawn and the sample counter
     * has just reached pixel 4.
     */
    public boolean isDrawingMainCopyAt4() {
        return rendering && sampleCounter == 4 && copy == 1;
    }

    /**
     * Counter value that positions a RESMP-locked missile at the center
     * of the main copy.
     */
    public int getRespClock() {
        return switch (divider) {
            case 2 -> Math.floorMod(counter - 8, WIDTH);
            case 4 -> Math.floorMod(counter - 12, WIDTH);
            default -> Math.floorMod(counter - 5, WIDTH);
        };
    }

    public void resp(int value, boolean lateHblank) {
        counter = Math.floorMod(lateHblank ? value - 1 : value, WIDTH);
        if (rendering && renderCounter - RENDER_OFFSET < 4) {
            renderCounter = RENDER_OFFSET + (counter - 157);
        }
    }

    public void setCounter(int value) {
        counter = Math.floorMod(value, WIDTH);
        rendering = false;
        on = false;
    }

    public int counter() {
        return counter;
    }

    public int position() {
        int shift = divider == 1 ? 0 : 1;
        return Math.floorMod(317 - counter - RENDER_OFFSET + shift, WIDTH);
    }

    public boolean on() {
        return on;
    }

    public int colorIndexBit() {
        return sampleCounter;
    }

    public void tick() {
        int current = delaying ? patternOld : patternNew;
        int effective = reflected ? current : reverse8(current);
        on = rendering
                && renderCounter >= renderCounterTripPoint
                && sampleCounter <= 7
                && ((effective >> sampleCounter) & 1) != 0;

        if (decodes[counter] != 0) {
            rendering = true;
            sampleCounter = 0;
            renderCounter = RENDER_OFFSET;
            copy = decodes[counter];
            divider = dividerPending;
        } else if (rendering) {
            ++renderCounter;
            if (divider == 1) {
                if (renderCounter > 0) {
                    ++sampleCounter;
                }
            } else if (renderCounter > 1
                    && ((renderCounter - 1) & (divider - 1)) == 0) {
                ++sampleCounter;
            }
            if (dividerChangeCounter >= 0 && dividerChangeCounter-- == 0) {
                divider = dividerPending;
            }
            if (sampleCounter > 7) {
                rendering = false;
            }
        }
        if (++counter >= WIDTH) {
            counter = 0;
        }
    }

    public void tickInHblank() {
        // В HBLANK счётчик и render counter не двигаются.
    }

    private void updatePattern() {
        pattern = delaying ? patternOld : patternNew;
    }

    private static int reverse8(int value) {
        int v = value & 0xFF;
        int result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 1) | (v & 1);
            v >>>= 1;
        }
        return result;
    }

    private void setDecodes(int mode) {
        for (int i = 0; i < WIDTH; i++) {
            decodes[i] = 0;
        }
        decodes[156] = 1;
        switch (mode) {
            case 1 -> decodes[12] = 2;
            case 2 -> decodes[28] = 2;
            case 3 -> {
                decodes[12] = 2;
                decodes[28] = 3;
            }
            case 4 -> decodes[60] = 2;
            case 6 -> {
                decodes[28] = 2;
                decodes[60] = 3;
            }
            default -> {
            }
        }
    }

    void save(StateWriter writer) {
        writer.writeShort(counter);
        writer.writeShort(renderCounter);
        writer.writeShort(renderCounterTripPoint);
        writer.writeShort(sampleCounter);
        writer.writeShort(divider);
        writer.writeShort(dividerPending);
        writer.writeShort(dividerChangeCounter);
        writer.writeShort(copy);
        writer.writeByte(rendering ? 1 : 0);
        writer.writeByte(on ? 1 : 0);
        writer.writeByte(patternNew);
        writer.writeByte(patternOld);
        writer.writeByte(pattern);
        writer.writeByte(delaying ? 1 : 0);
        writer.writeByte(reflected ? 1 : 0);
        writer.writeByte(nusiz);
        writer.writeShort(hmpClocks);
        writer.writeByte(objectColor);
        writer.writeByte(color);
        writer.writeByte(moving ? 1 : 0);
    }

    void restore(StateReader reader) {
        counter = reader.readShort();
        renderCounter = reader.readShort();
        renderCounterTripPoint = reader.readShort();
        sampleCounter = reader.readShort();
        divider = reader.readShort();
        dividerPending = reader.readShort();
        dividerChangeCounter = reader.readShort();
        copy = reader.readShort();
        rendering = reader.readByte() != 0;
        on = reader.readByte() != 0;
        patternNew = reader.readByte();
        patternOld = reader.readByte();
        pattern = reader.readByte();
        delaying = reader.readByte() != 0;
        reflected = reader.readByte() != 0;
        nusiz = reader.readByte();
        hmpClocks = reader.readShort();
        objectColor = reader.readByte();
        color = reader.readByte();
        moving = reader.readByte() != 0;
        setDecodes(nusiz & 0x07);
    }
}
