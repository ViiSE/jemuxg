package ru.viise.jemuxg.api.console;

import java.util.List;
import java.util.Optional;
import ru.viise.jemuxg.api.debug.ConsoleDebugger;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.StorageSlot;

/**
 * Собранная консоль: метаданные, машина, слоты носителей.
 */
public interface Console {

    ConsoleInfo info();

    Machine machine();

    List<StorageSlot> slots();

    int controllerPorts();

    /**
     * Отладочные снимки конкретной консоли. По умолчанию недоступны.
     */
    default Optional<ConsoleDebugger> debugger() {
        return Optional.empty();
    }
}
