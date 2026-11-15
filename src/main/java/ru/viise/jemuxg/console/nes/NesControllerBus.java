package ru.viise.jemuxg.console.nes;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.Controller;

/**
 * Регистры {@code $4016/$4017} NES: стробирование и последовательное чтение
 * двух стандартных геймпадов.
 * <p>
 * Запись в {@code $4017} относится к APU (frame counter), поэтому она
 * пробрасывается в шину APU. Чтение {@code $4016/$4017} возвращает биты
 * контроллеров 1 и 2 соответственно.
 */
public final class NesControllerBus implements Bus {

    private static final Button[] ORDER = {
            Button.A,
            Button.B,
            Button.SELECT,
            Button.START,
            Button.UP,
            Button.DOWN,
            Button.LEFT,
            Button.RIGHT
    };

    private final Bus apuRegisters;
    private final Controller[] controllers = new Controller[2];
    private final int[] readIndex = new int[2];
    private boolean strobe;

    public NesControllerBus(Bus apuRegisters) {
        this.apuRegisters = Objects.requireNonNull(apuRegisters, "apuRegisters");
    }

    /**
     * Подключить или отключить геймпад. {@code null} означает пустой порт.
     */
    public void connect(int port, Controller controller) {
        if (port < 0 || port >= controllers.length) {
            throw new IllegalArgumentException(
                    "NES has " + controllers.length + " controller ports, got " + port);
        }
        controllers[port] = controller;
        readIndex[port] = 0;
    }

    public void reset() {
        strobe = false;
        readIndex[0] = 0;
        readIndex[1] = 0;
    }

    @Override
    public int read(int address) {
        int port = address == 0x4016 ? 0 : 1;
        if (strobe) {
            return button(port, 0) ? 1 : 0;
        }

        int index = readIndex[port];
        if (index >= ORDER.length) {
            return 1;
        }
        int value = button(port, index) ? 1 : 0;
        readIndex[port] = index + 1;
        return value;
    }

    @Override
    public void write(int address, int value) {
        if (address == 0x4016) {
            boolean high = (value & 1) != 0;
            if (high) {
                readIndex[0] = 0;
                readIndex[1] = 0;
            }
            strobe = high;
            return;
        }
        if (address == 0x4017) {
            apuRegisters.write(address, value);
        }
    }

    private boolean button(int port, int index) {
        Controller controller = controllers[port];
        return controller != null && controller.pressed(ORDER[index]);
    }
}
