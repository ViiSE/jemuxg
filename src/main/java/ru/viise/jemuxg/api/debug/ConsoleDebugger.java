package ru.viise.jemuxg.api.debug;

import java.io.IOException;

/**
 * Отладочный снимок конкретной консоли.
 * <p>
 * Каждая консоль предоставляет свою реализацию, которая знает, какие
 * устройства и области памяти имеет смысл сохранять. Общая часть —
 * только запись артефактов через {@link DebugSession}.
 */
public interface ConsoleDebugger {

    /**
     * Идентификатор консоли, например {@code nes}.
     */
    String consoleId();

    /**
     * Сделать снимок текущего состояния и записать его в сессию.
     * Вызывается в конце кадра, когда состояние стабильно.
     */
    void capture(DebugSession session) throws IOException;
}
