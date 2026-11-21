package ru.viise.jemuxg.hardware.video.tia;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Порт Ball TIA из проекта Stella.
 *
 * <p>Ширина задаётся CTRLPF (1/2/4/8), включение — ENABL с VDELBL-задержкой,
 * позиция — RESBL. Также повторяет starfield-поведение при HMOVE.</p>
 *
 * <p>Реализация основана на исходном коде
 * <a href="https://github.com/stella-emu/stella">Stella</a>
 * ({@code Ball.hxx/.cxx}). Особая благодарность авторам проекта.</p>
 */
public final class StellaTiaBall {

    public static final int WIDTH = 160;
    private static final int RENDER_OFFSET = -4;

    private int counter;
    private int renderCounter;
    private int width = 1;
    private int effectiveWidth = 1;
    private int lastMovementTick;
    private boolean rendering;
    private boolean signalActive;
    private boolean on;
    private boolean enabledOld;
    private boolean enabledNew;
    private boolean enabled;
    private boolean delaying;
    private boolean moving;
    private int hmmClocks;
    private int objectColor;
    private int color;

    public StellaTiaBall() {
        reset();
    }

    public void reset() {
        counter = 0;
        renderCounter = 0;
        width = 1;
        effectiveWidth = 1;
        lastMovementTick = 0;
        rendering = false;
        signalActive = false;
        on = false;
        enabledOld = false;
        enabledNew = false;
        enabled = false;
        delaying = false;
        moving = false;
        hmmClocks = 0;
        objectColor = 0;
        color = 0;
    }

    public void enabl(int value) {
        boolean previous = enabledNew;
        enabledNew = (value & 0x02) != 0;
        if (enabledNew != previous && !delaying) {
            updateEnabled();
        }
    }

    public void hmbl(int value) {
        hmmClocks = ((value >>> 4) ^ 0x08) & 0x0F;
    }

    public void resbl(int value, boolean lateRespxCondition) {
        int target = value;
        if (lateRespxCondition) {
            target = Math.floorMod(target - 1, WIDTH);
        }
        counter = target;
        rendering = true;
        renderCounter = RENDER_OFFSET + (target - 157);
    }

    public void ctrlpf(int value) {
        width = switch ((value & 0x30) >>> 4) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 8;
            default -> 1;
        };
    }

    public void vdelbl(int value) {
        delaying = (value & 0x01) != 0;
        updateEnabled();
    }

    public void startMovement() {
        moving = true;
    }

    public void movementTick(int clock, int hclock, boolean hblank) {
        lastMovementTick = counter;
        if (!moving) {
            return;
        }
        if (clock == hmmClocks) {
            moving = false;
            return;
        }
        if (hblank) {
            tick(false);
        }
    }

    public void tickInHblank() {
        signalActive = rendering && renderCounter >= 0;
        updateOn();
    }

    public void tick() {
        tick(true);
    }

    public void tick(boolean regularClock) {
        signalActive = rendering && renderCounter >= 0;
        updateOn();

        boolean starfield = moving && regularClock;
        if (counter == 156) {
            rendering = true;
            renderCounter = RENDER_OFFSET;

            int starfieldDelta = (counter + WIDTH - lastMovementTick) % 4;
            if (starfield && starfieldDelta == 3 && width < 4) {
                ++renderCounter;
            }
            switch (starfieldDelta) {
                case 3 -> effectiveWidth = width == 1 ? 2 : width;
                case 2 -> effectiveWidth = 0;
                default -> effectiveWidth = width;
            }
        } else if (rendering
                && ++renderCounter >= (starfield ? effectiveWidth : width)) {
            rendering = false;
        }

        if (++counter >= WIDTH) {
            counter = 0;
        }
    }

    public void nextLine() {
        signalActive = rendering && renderCounter >= 0;
        updateOn();
    }

    public void shuffleStatus() {
        boolean previous = enabledOld;
        enabledOld = enabledNew;
        if (enabledOld != previous && delaying) {
            updateEnabled();
        }
    }

    public void setColor(int value) {
        objectColor = value & 0xFE;
        color = objectColor;
    }

    public boolean isOn() {
        return on;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSignalActive() {
        return signalActive;
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
        signalActive = false;
        on = false;
    }

    private void updateEnabled() {
        enabled = delaying ? enabledOld : enabledNew;
        updateOn();
    }

    private void updateOn() {
        on = signalActive && enabled;
    }

    void save(StateWriter writer) {
        writer.writeShort(counter);
        writer.writeShort(renderCounter);
        writer.writeShort(width);
        writer.writeShort(effectiveWidth);
        writer.writeShort(lastMovementTick);
        writer.writeByte(rendering ? 1 : 0);
        writer.writeByte(signalActive ? 1 : 0);
        writer.writeByte(on ? 1 : 0);
        writer.writeByte(enabledOld ? 1 : 0);
        writer.writeByte(enabledNew ? 1 : 0);
        writer.writeByte(enabled ? 1 : 0);
        writer.writeByte(delaying ? 1 : 0);
        writer.writeByte(moving ? 1 : 0);
        writer.writeShort(hmmClocks);
        writer.writeByte(objectColor);
        writer.writeByte(color);
    }

    void restore(StateReader reader) {
        counter = reader.readShort();
        renderCounter = reader.readShort();
        width = reader.readShort();
        effectiveWidth = reader.readShort();
        lastMovementTick = reader.readShort();
        rendering = reader.readByte() != 0;
        signalActive = reader.readByte() != 0;
        on = reader.readByte() != 0;
        enabledOld = reader.readByte() != 0;
        enabledNew = reader.readByte() != 0;
        enabled = reader.readByte() != 0;
        delaying = reader.readByte() != 0;
        moving = reader.readByte() != 0;
        hmmClocks = reader.readShort();
        objectColor = reader.readByte();
        color = reader.readByte();
    }
}
