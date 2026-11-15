package ru.viise.jemuxg.api;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;

/**
 * Компонент, умеющий сохранять и восстанавливать своё состояние.
 * Используется для save state и rewind. Рефлексия не нужна.
 */
public interface Stateful {

    void save(StateWriter writer);

    void restore(StateReader reader);
}
