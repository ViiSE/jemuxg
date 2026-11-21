package ru.viise.jemuxg.console.sg1000;

import java.util.List;
import java.util.Optional;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleInfo;
import ru.viise.jemuxg.api.debug.ConsoleDebugger;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.StorageSlot;
import ru.viise.jemuxg.console.sg1000.debug.Sg1000Debugger;

/**
 * Консоль Sega SG-1000: корпус, слот картриджа и машина.
 */
public final class Sg1000Console implements Console {

    private static final int CONTROLLER_PORTS = 2;

    private final ConsoleInfo info;
    private final Sg1000Machine machine = new Sg1000Machine();
    private final Sg1000Debugger debugger = new Sg1000Debugger(machine);

    Sg1000Console(ConsoleInfo info) {
        this.info = info;
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
