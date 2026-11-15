package ru.viise.jemuxg.api.hardware;

import ru.viise.jemuxg.api.Clocked;
import ru.viise.jemuxg.api.Component;
import ru.viise.jemuxg.api.Stateful;

/**
 * Процессор. Регистры наружу не отдаются: для отладки есть отдельный контракт.
 */
public interface Cpu extends Component, Clocked, Stateful {

    /**
     * Запросить прерывание. RESET выполняется сразу, NMI запоминается фронтом,
     * IRQ поднимает уровень линии до {@link #clearInterrupt(InterruptType)}.
     */
    void interrupt(InterruptType type);

    /**
     * Снять уровень IRQ. Для NMI/RESET ничего не делает.
     */
    void clearInterrupt(InterruptType type);
}
