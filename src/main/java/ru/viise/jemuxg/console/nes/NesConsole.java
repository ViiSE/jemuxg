package ru.viise.jemuxg.console.nes;

import java.util.List;
import java.util.Optional;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleInfo;
import ru.viise.jemuxg.api.debug.ConsoleDebugger;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.StorageSlot;
import ru.viise.jemuxg.console.nes.debug.NesDebugger;

/**
 * Консоль NES: корпус, слоты и машина.
 */
public final class NesConsole implements Console {

    private static final int CONTROLLER_PORTS = 2;

    private final ConsoleInfo info;
    private final NesMachine machine = new NesMachine();
    private final ConsoleDebugger debugger = new NesDebugger(machine);

    NesConsole(ConsoleInfo info) {
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
