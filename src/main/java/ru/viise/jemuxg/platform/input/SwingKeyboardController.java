package ru.viise.jemuxg.platform.input;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.core.input.SimpleController;

/**
 * Клавиатура как геймпад.
 * <p>
 * Стрелки — крестовина, Z/A/Space — A, X/S — B, Enter — Start,
 * Shift/Backspace — Select.
 */
public final class SwingKeyboardController extends KeyAdapter {

    private static final Map<Integer, Button> BINDINGS = Map.ofEntries(
            Map.entry(KeyEvent.VK_UP, Button.UP),
            Map.entry(KeyEvent.VK_DOWN, Button.DOWN),
            Map.entry(KeyEvent.VK_LEFT, Button.LEFT),
            Map.entry(KeyEvent.VK_RIGHT, Button.RIGHT),
            Map.entry(KeyEvent.VK_Z, Button.A),
            Map.entry(KeyEvent.VK_A, Button.A),
            Map.entry(KeyEvent.VK_SPACE, Button.A),
            Map.entry(KeyEvent.VK_X, Button.B),
            Map.entry(KeyEvent.VK_S, Button.B),
            Map.entry(KeyEvent.VK_ENTER, Button.START),
            Map.entry(KeyEvent.VK_SHIFT, Button.SELECT),
            Map.entry(KeyEvent.VK_BACK_SPACE, Button.SELECT));

    private final SimpleController controller;

    public SwingKeyboardController(SimpleController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override
    public void keyPressed(KeyEvent event) {
        Button button = BINDINGS.get(event.getKeyCode());
        if (button != null) {
            controller.press(button);
            event.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent event) {
        Button button = BINDINGS.get(event.getKeyCode());
        if (button != null) {
            controller.release(button);
            event.consume();
        }
    }

    /**
     * Сбросить все кнопки, например при потере фокуса окном.
     */
    public void releaseAll() {
        controller.releaseAll();
    }
}
