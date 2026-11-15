package ru.viise.jemuxg.api.machine;

/**
 * Центральный планировщик событий по циклам.
 * Всё время в ядре идёт только через него.
 */
public interface Scheduler {

    long now();

    void schedule(long cycle, Runnable event);

    void advanceTo(long cycle);
}
