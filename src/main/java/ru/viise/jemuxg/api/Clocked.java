package ru.viise.jemuxg.api;

/**
 * Компонент, который продвигается мастер-тактовым генератором.
 * {@code cycles} — число тактов, прошедших с предыдущего вызова.
 */
public interface Clocked {

    void tick(long cycles);
}
