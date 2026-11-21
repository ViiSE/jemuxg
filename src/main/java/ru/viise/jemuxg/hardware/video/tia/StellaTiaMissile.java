package ru.viise.jemuxg.hardware.video.tia;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Порт Missile (M0/M1) TIA из проекта Stella.
 *
 * <p>Повторяет horizontal counter, render counter, NUSIZ-декоды,
 * ширину 1/2/4/8, ENAM, RESMP и starfield-поведение при HMOVE.</p>
 *
 * <p>Реализация основана на исходном коде
 * <a href="https://github.com/stella-emu/stella">Stella</a>
 * ({@code Missile.hxx/.cxx}). Особая благодарность авторам проекта.</p>
 */
public final class StellaTiaMissile {

    public static final int WIDTH = 160;
    private static final int RENDER_OFFSET = -4;

    private final int[] decodes = new int[WIDTH];
    private int counter;
    private int renderCounter;
    private int copy = 1;
    private int width = 1;
    private int effectiveWidth = 1;
    private boolean rendering;
    private boolean visible;
    private boolean on;
    private int resmp;
    private boolean enam;
    private boolean moving;
    private int hmmClocks;
    private int objectColor;
    private int color;

    public StellaTiaMissile() {
        reset();
    }

    public void reset() {
        counter = 0;
        renderCounter = 0;
        copy = 1;
        width = 1;
        effectiveWidth = 1;
        rendering = false;
        visible = false;
        on = false;
        resmp = 0;
        enam = false;
        moving = false;
        hmmClocks = 0;
        objectColor = 0;
        color = 0;
        setDecodes(0);
    }

    public void enam(int value) {
        enam = (value & 0x02) != 0;
        updateOn();
    }

    public void hmm(int value) {
        hmmClocks = ((value >>> 4) ^ 0x08) & 0x0F;
    }

    public void resm(int value, boolean hblank, boolean lateRespxCondition) {
        int target = value;
        if (lateRespxCondition) {
            target = Math.floorMod(target - 1, WIDTH);
        }
        counter = target;

        if (!rendering) {
            return;
        }
        if (renderCounter < 0) {
            renderCounter = RENDER_OFFSET + (target - 157);
            return;
        }

        switch (width) {
            case 8 -> renderCounter = (target - 157)
                    + (renderCounter >= 4 ? 4 : 0);
            case 4 -> renderCounter = target - 157;
            case 2 -> {
                if (hblank) {
                    rendering = renderCounter > 1;
                } else if (renderCounter == 0) {
                    ++renderCounter;
                }
            }
            default -> {
                if (hblank) {
                    rendering = renderCounter > 0;
                }
            }
        }
    }

    public void resmp(int value) {
        resmp = value & 0x02;
        updateOn();
    }

    public void nusiz(int value) {
        int mode = value & 0x07;
        width = switch ((value & 0x30) >>> 4) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 8;
            default -> 1;
        };
        setDecodes(mode);
        if (rendering && renderCounter >= width) {
            rendering = false;
        }
    }

    public void startMovement() {
        moving = true;
    }

    public void movementTick(int clock, int hclock, boolean hblank) {
        if (!moving) {
            return;
        }
        if (clock == hmmClocks) {
            moving = false;
            return;
        }
        if (hblank) {
            tick(hclock, false);
        }
    }

    public void tickInHblank() {
        visible = rendering && renderCounter >= 0;
        updateOn();
    }

    public void tick(int hclock) {
        tick(hclock, true);
    }

    public void tick(int hclock, boolean regularClock) {
        visible = rendering
                && (renderCounter >= 0
                || (moving && regularClock && renderCounter == -1
                && width < 4 && ((hclock + 1) % 4 == 3)));
        updateOn();

        if (decodes[counter] != 0 && resmp == 0) {
            rendering = true;
            renderCounter = RENDER_OFFSET;
            copy = decodes[counter];
        } else if (rendering) {
            if (renderCounter == -1) {
                if (moving && regularClock) {
                    switch ((hclock + 1) % 4) {
                        case 3 -> {
                            effectiveWidth = width == 1 ? 2 : width;
                            if (width < 4) {
                                ++renderCounter;
                            }
                        }
                        case 2 -> effectiveWidth = 0;
                        default -> effectiveWidth = width;
                    }
                } else {
                    effectiveWidth = width;
                }
            }
            if (++renderCounter >= (moving ? effectiveWidth : width)) {
                rendering = false;
            }
        }

        if (++counter >= WIDTH) {
            counter = 0;
        }
    }

    public void tick(int hclock, StellaTiaPlayer player) {
        tick(hclock);
        if (resmp != 0 && player.isDrawingMainCopyAt4()) {
            counter = player.getRespClock();
        }
    }

    public void nextLine() {
        visible = rendering && renderCounter >= 0;
        updateOn();
    }

    public void setColor(int value) {
        objectColor = value & 0xFE;
        color = objectColor;
    }

    public boolean isOn() {
        return on;
    }

    public boolean isEnabled() {
        return enam && resmp == 0;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isMoving() {
        return moving;
    }

    public int color() {
        return color;
    }

    public int counter() {
        return counter;
    }

    public int renderCounter() {
        return renderCounter;
    }

    public int width() {
        return width;
    }

    public int copy() {
        return copy;
    }

    public int hmmClocks() {
        return hmmClocks;
    }

    public int position() {
        return Math.floorMod(317 - counter - RENDER_OFFSET, WIDTH);
    }

    public void setPosition(int newPosition) {
        counter = Math.floorMod(317 - newPosition - RENDER_OFFSET, WIDTH);
    }

    public void setCounter(int value) {
        counter = Math.floorMod(value, WIDTH);
        rendering = false;
        visible = false;
        on = false;
    }

    private void updateOn() {
        on = visible && enam && resmp == 0;
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
        writer.writeShort(copy);
        writer.writeShort(width);
        writer.writeShort(effectiveWidth);
        writer.writeByte(rendering ? 1 : 0);
        writer.writeByte(visible ? 1 : 0);
        writer.writeByte(on ? 1 : 0);
        writer.writeShort(resmp);
        writer.writeByte(enam ? 1 : 0);
        writer.writeByte(moving ? 1 : 0);
        writer.writeShort(hmmClocks);
        writer.writeByte(objectColor);
        writer.writeByte(color);
        byte[] table = new byte[WIDTH];
        for (int i = 0; i < WIDTH; i++) {
            table[i] = (byte) decodes[i];
        }
        writer.writeBytes(table);
    }

    void restore(StateReader reader) {
        counter = reader.readShort();
        renderCounter = reader.readShort();
        copy = reader.readShort();
        width = reader.readShort();
        effectiveWidth = reader.readShort();
        rendering = reader.readByte() != 0;
        visible = reader.readByte() != 0;
        on = reader.readByte() != 0;
        resmp = reader.readShort();
        enam = reader.readByte() != 0;
        moving = reader.readByte() != 0;
        hmmClocks = reader.readShort();
        objectColor = reader.readByte();
        color = reader.readByte();
        byte[] table = reader.readBytes();
        for (int i = 0; i < WIDTH; i++) {
            decodes[i] = table[i];
        }
    }
}
