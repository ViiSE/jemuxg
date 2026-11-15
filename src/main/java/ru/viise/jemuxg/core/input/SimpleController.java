package ru.viise.jemuxg.core.input;

import java.util.EnumSet;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.Controller;

/**
 * Простейший контроллер, который фронтенд заполняет нажатиями.
 */
public final class SimpleController implements Controller {

    private final EnumSet<Button> pressed = EnumSet.noneOf(Button.class);

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
}
