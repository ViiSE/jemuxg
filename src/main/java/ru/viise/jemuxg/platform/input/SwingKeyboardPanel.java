package ru.viise.jemuxg.platform.input;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import ru.viise.jemuxg.api.hardware.PanelSwitch;
import ru.viise.jemuxg.api.hardware.SwitchPanel;

/**
 * Клавиатура как переключатели на корпусе консоли.
 * <p>
 * F2 — Reset, F3 — Select, F4 — переключение Color/B&amp;W. Reset и Select
 * удерживаются, пока нажата клавиша; Color/B&amp;W меняется по нажатию.
 * Остальные переключатели (сложность, TV type) остаются в положении по
 * умолчанию.
 */
public final class SwingKeyboardPanel extends KeyAdapter implements SwitchPanel {

    private static final int RESET_MASK = 1;
    private static final int SELECT_MASK = 2;

    private volatile int pressedMask;
    private volatile boolean colorEnabled = true;
    private boolean colorKeyDown;

    @Override
    public boolean pressed(PanelSwitch button) {
        return switch (button) {
            case RESET -> (pressedMask & RESET_MASK) != 0;
            case SELECT -> (pressedMask & SELECT_MASK) != 0;
            case COLOR -> colorEnabled;
            default -> false;
        };
    }

    @Override
    public void keyPressed(KeyEvent event) {
        if (handleKeyPressed(event.getKeyCode())) {
            event.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent event) {
        if (handleKeyReleased(event.getKeyCode())) {
            event.consume();
        }
    }

    /**
     * Обрабатывает нажатие клавиши без AWT-события.
     *
     * @return {@code true}, если клавиша относится к переключателям консоли
     */
    public boolean handleKeyPressed(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_F2:
                pressedMask |= RESET_MASK;
                return true;
            case KeyEvent.VK_F3:
                pressedMask |= SELECT_MASK;
                return true;
            case KeyEvent.VK_F4:
                if (!colorKeyDown) {
                    colorKeyDown = true;
                    colorEnabled = !colorEnabled;
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * Обрабатывает отпускание клавиши без AWT-события.
     *
     * @return {@code true}, если клавиша относится к переключателям консоли
     */
    public boolean handleKeyReleased(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_F2:
                pressedMask &= ~RESET_MASK;
                return true;
            case KeyEvent.VK_F3:
                pressedMask &= ~SELECT_MASK;
                return true;
            case KeyEvent.VK_F4:
                colorKeyDown = false;
                return true;
            default:
                return false;
        }
    }

    /**
     * Сбрасывает удерживаемые переключатели, не меняя Color/B&amp;W.
     */
    public void releaseAll() {
        pressedMask = 0;
        colorKeyDown = false;
    }

    public boolean colorEnabled() {
        return colorEnabled;
    }

    public void setColor(boolean enabled) {
        colorEnabled = enabled;
    }
}
