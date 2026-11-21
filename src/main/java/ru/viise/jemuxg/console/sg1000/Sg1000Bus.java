package ru.viise.jemuxg.console.sg1000;

import java.util.Objects;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.PanelSwitch;
import ru.viise.jemuxg.api.hardware.SwitchPanel;
import ru.viise.jemuxg.core.memory.Ram;
import ru.viise.jemuxg.console.sg1000.cartridge.Sg1000CartridgeSlot;
import ru.viise.jemuxg.hardware.audio.sn76489.Sn76489;
import ru.viise.jemuxg.hardware.cpu.z80.Z80Bus;
import ru.viise.jemuxg.hardware.video.tms9918.Tms9918;

/**
 * Карта памяти Sega SG-1000.
 *
 * <p>Адреса {@code $0000–$BFFF} отдаются картриджу, {@code $C000–$FFFF}
 * — 1 КБ RAM с зеркалами по формуле
 * {@code $C000 | (address & $03FF)}. Запись в ROM игнорируется,
 * пустой слот читается как {@code $FF}.</p>
 *
 * <p>Порты {@code $80–$BF} подключены к TMS9918A: чётные — данные,
 * нечётные — управление/статус. Порты {@code $40–$7F} подключены к
 * SN76489AN. Порты {@code $C0–$FF} отдают состояние джойстиков и
 * кнопки Reset: чётные — первый игрок и Up/Down второго, нечётные —
 * остальные кнопки второго игрока и Reset.</p>
 */
public final class Sg1000Bus implements Z80Bus {

    private static final int ADDRESS_MASK = 0xFFFF;
    private static final int RAM_MASK = Sg1000Constants.RAM_SIZE - 1;
    private static final int VDP_PORT_MASK = 0xC0;
    private static final int VDP_PORT_BASE = 0x80;
    private static final int PSG_PORT_MASK = 0xC0;
    private static final int PSG_PORT_BASE = 0x40;
    private static final int INPUT_PORT_MASK = 0xC0;
    private static final int INPUT_PORT_BASE = 0xC0;

    private final Sg1000CartridgeSlot cartridgeSlot;
    private final Ram ram;
    private final Tms9918 vdp;
    private final Sn76489 psg;
    private final Controller[] controllers = new Controller[2];
    private SwitchPanel panel;
    private Runnable irqClear = () -> {
    };

    public Sg1000Bus(Sg1000CartridgeSlot cartridgeSlot) {
        this(cartridgeSlot, new Tms9918(), new Sn76489());
    }

    public Sg1000Bus(Sg1000CartridgeSlot cartridgeSlot, Tms9918 vdp) {
        this(cartridgeSlot, vdp, new Sn76489());
    }

    public Sg1000Bus(Sg1000CartridgeSlot cartridgeSlot, Tms9918 vdp, Sn76489 psg) {
        this.cartridgeSlot = Objects.requireNonNull(cartridgeSlot, "cartridgeSlot");
        this.ram = new Ram(Sg1000Constants.RAM_SIZE);
        this.vdp = Objects.requireNonNull(vdp, "vdp");
        this.psg = Objects.requireNonNull(psg, "psg");
    }

    @Override
    public int read(int address) {
        if (address < 0 || address > ADDRESS_MASK) {
            return 0xFF;
        }
        if (address <= Sg1000Constants.ROM_END) {
            return cartridgeSlot.read(address);
        }
        return ram.read(address & RAM_MASK);
    }

    @Override
    public void write(int address, int value) {
        if (address < 0 || address > ADDRESS_MASK) {
            return;
        }
        if (address >= Sg1000Constants.RAM_START) {
            ram.write(address & RAM_MASK, value);
        }
    }

    /**
     * Сбрасывает RAM в ноль для детерминированного старта.
     */
    public void reset() {
        ram.clear();
    }

    @Override
    public int in(int port) {
        int normalized = port & 0xFF;
        if ((normalized & VDP_PORT_MASK) == VDP_PORT_BASE) {
            if ((normalized & 1) == 0) {
                return vdp.readData();
            }
            int status = vdp.readStatus();
            irqClear.run();
            return status;
        }
        if ((normalized & INPUT_PORT_MASK) == INPUT_PORT_BASE) {
            return (normalized & 1) == 0 ? readPlayerOne() : readPlayerTwo();
        }
        return 0xFF;
    }

    @Override
    public void out(int port, int value) {
        int normalized = port & 0xFF;
        if ((normalized & VDP_PORT_MASK) == VDP_PORT_BASE) {
            if ((normalized & 1) == 0) {
                vdp.writeData(value);
            } else {
                vdp.writeControl(value);
            }
            return;
        }
        if ((normalized & PSG_PORT_MASK) == PSG_PORT_BASE) {
            psg.write(value);
        }
    }

    public void connectController(int port, Controller controller) {
        if (port < 0 || port >= controllers.length) {
            throw new IllegalArgumentException(
                    "SG-1000 has " + controllers.length + " controller ports, got " + port);
        }
        controllers[port] = controller;
    }

    public void connectPanel(SwitchPanel panel) {
        this.panel = panel;
    }

    private int readPlayerOne() {
        int value = 0xFF;
        Controller first = controllers[0];
        if (first != null) {
            if (first.pressed(Button.UP)) {
                value &= ~0x01;
            }
            if (first.pressed(Button.DOWN)) {
                value &= ~0x02;
            }
            if (first.pressed(Button.LEFT)) {
                value &= ~0x04;
            }
            if (first.pressed(Button.RIGHT)) {
                value &= ~0x08;
            }
            if (first.pressed(Button.A)) {
                value &= ~0x10;
            }
            if (first.pressed(Button.B)) {
                value &= ~0x20;
            }
        }

        Controller second = controllers[1];
        if (second != null) {
            if (second.pressed(Button.UP)) {
                value &= ~0x40;
            }
            if (second.pressed(Button.DOWN)) {
                value &= ~0x80;
            }
        }
        return value;
    }

    private int readPlayerTwo() {
        int value = 0xFF;
        Controller second = controllers[1];
        if (second != null) {
            if (second.pressed(Button.LEFT)) {
                value &= ~0x01;
            }
            if (second.pressed(Button.RIGHT)) {
                value &= ~0x02;
            }
            if (second.pressed(Button.A)) {
                value &= ~0x04;
            }
            if (second.pressed(Button.B)) {
                value &= ~0x08;
            }
        }
        if (panel != null && panel.pressed(PanelSwitch.RESET)) {
            value &= ~0x10;
        }
        return value;
    }

    /**
     * Подключает сброс линии IRQ CPU при чтении статуса VDP.
     */
    public void setIrqClear(Runnable irqClear) {
        this.irqClear = Objects.requireNonNull(irqClear, "irqClear");
    }

    public Ram ram() {
        return ram;
    }

    public Tms9918 vdp() {
        return vdp;
    }

    public Sn76489 psg() {
        return psg;
    }

    public Sg1000CartridgeSlot cartridgeSlot() {
        return cartridgeSlot;
    }
}
