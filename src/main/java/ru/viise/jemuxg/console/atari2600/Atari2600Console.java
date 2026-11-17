package ru.viise.jemuxg.console.atari2600;

import java.util.List;
import java.util.Optional;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleInfo;
import ru.viise.jemuxg.api.debug.ConsoleDebugger;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.StorageSlot;
import ru.viise.jemuxg.console.atari2600.debug.AtariDebugger;

/**
 * Консоль Atari 2600: корпус, слот и машина.
 */
public final class Atari2600Console implements Console {

    private static final int CONTROLLER_PORTS = 2;

    private final ConsoleInfo info;
    private final Atari2600Machine machine;
    private final ConsoleDebugger debugger;

    Atari2600Console(ConsoleInfo info) {
        this(info, null);
    }

    Atari2600Console(ConsoleInfo info, String mapperName) {
        this.info = info;
        this.machine = new Atari2600Machine(mapperName);
        this.debugger = new AtariDebugger(machine);
    }

    @Override
    public ConsoleInfo info() {
        return info;
    }

    @Override
    public Machine machine() {
        return machine;
    }

    @Override
    public List<StorageSlot> slots() {
        return machine.slots();
    }

    @Override
    public int controllerPorts() {
        return CONTROLLER_PORTS;
    }

    @Override
    public Optional<ConsoleDebugger> debugger() {
        return Optional.of(debugger);
    }
}
