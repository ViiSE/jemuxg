package ru.viise.jemuxg.core.input;

import java.util.EnumSet;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.PaddleController;

/**
 * Простейший контроллер, который фронтенд заполняет нажатиями.
 *
 * <p>Одновременно является paddle-контроллером: положение ручки меняется
 * цифровыми клавишами влево/вправо.</p>
 */
public final class SimpleController implements Controller, PaddleController {

    public static final int PADDLE_MIN = 0;
    public static final int PADDLE_MAX = 1000;

    private final EnumSet<Button> pressed = EnumSet.noneOf(Button.class);
    private int paddlePosition = PADDLE_MAX / 2;
    private boolean paddleMode;

    public synchronized void press(Button button) {
        pressed.add(button);
    }

    public synchronized void release(Button button) {
        pressed.remove(button);
    }

    public synchronized void releaseAll() {
        pressed.clear();
    }

    @Override
    public synchronized boolean pressed(Button button) {
        return pressed.contains(button);
    }

    @Override
    public synchronized int paddlePosition() {
        return paddlePosition;
    }

    public synchronized void setPaddlePosition(int position) {
        paddlePosition = Math.max(PADDLE_MIN, Math.min(PADDLE_MAX, position));
    }

    public synchronized void advancePaddle(int delta) {
        setPaddlePosition(paddlePosition + delta);
    }

    @Override
    public synchronized boolean paddleMode() {
        return paddleMode;
    }

    public synchronized void setPaddleMode(boolean paddleMode) {
        this.paddleMode = paddleMode;
    }
}
