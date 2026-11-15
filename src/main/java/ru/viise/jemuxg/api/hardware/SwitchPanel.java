package ru.viise.jemuxg.api.hardware;

/**
 * Доступ к переключателям на корпусе консоли.
 */
public interface SwitchPanel {

    boolean pressed(PanelSwitch button);
}
