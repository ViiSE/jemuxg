package ru.viise.jemuxg.hardware.riot.riot6532;

import ru.viise.jemuxg.api.Component;
import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.PaddleController;
import ru.viise.jemuxg.api.hardware.PanelSwitch;
import ru.viise.jemuxg.api.hardware.SwitchPanel;

/**
 * Порты A/B RIOT 6532.
 *
 * <p>Регистры RIOT (смещения от {@code $0280}):</p>
 * <ul>
 *   <li>{@code $00} — SWCHA, джойстики; активный уровень низкий;</li>
 *   <li>{@code $01} — SWACNT, направление линий порта A (1 — выход);</li>
 *   <li>{@code $02} — SWCHB, консольные переключатели;</li>
 *   <li>{@code $03} — SWBCNT, направление линий порта B.</li>
 * </ul>
 *
 * <p>Кнопка Fire у Atari 2600 подключена не к RIOT, а к TIA
 * ({@code INPT4/INPT5}), поэтому здесь она не обрабатывается.</p>
 */
public final class RiotPorts implements Component, Stateful, Bus {

    public static final int SWCHA = 0x00;
    public static final int SWACNT = 0x01;
    public static final int SWCHB = 0x02;
    public static final int SWBCNT = 0x03;

    private static final int REGISTER_MASK = 0x03;
    private static final int DEFAULT_SWITCHES = 0x3F;
    private static final int BYTE_MASK = 0xFF;

    private int outputA;
    private int ddrA;
    private int outputB;
    private int ddrB;

    private Controller player0;
    private Controller player1;
    private SwitchPanel panel;

    /**
     * @return шина регистров портов
     */
    public Bus registers() {
        return this;
    }

    public void connectPlayer(int port, Controller controller) {
        switch (port) {
            case 0 -> player0 = controller;
            case 1 -> player1 = controller;
            default -> throw new IllegalArgumentException("Unknown RIOT port: " + port);
        }
    }

    public void connectPanel(SwitchPanel panel) {
        this.panel = panel;
    }

    @Override
    public int read(int address) {
        int value = switch (address & REGISTER_MASK) {
            case SWCHA -> (outputA | (~ddrA & BYTE_MASK)) & portAInput();
            case SWACNT -> ddrA;
            case SWCHB -> (outputB | (~ddrB & BYTE_MASK)) & (switchInput() | ddrB);
            case SWBCNT -> ddrB;
            default -> 0;
        };
        return value;
    }

    @Override
    public void write(int address, int value) {
        int data = value & BYTE_MASK;
        switch (address & REGISTER_MASK) {
            case SWCHA -> outputA = data;
            case SWACNT -> ddrA = data;
            case SWCHB -> outputB = data;
            case SWBCNT -> ddrB = data;
            default -> {
            }
        }
    }

    private int portAInput() {
        int value = BYTE_MASK;
        // Левая колонка (player 0) подключена к старшей тетраде PA4..PA7,
        // правая (player 1) — к младшей PA0..PA3. Внутри тетрады порядок
        // выводов джойстика: up, down, left, right.
        if (player0 != null) {
            if (paddleMode(player0)) {
                if (triggerPressed(player0)) {
                    value &= ~0x80;
                }
            } else {
                if (player0.pressed(Button.UP)) {
                    value &= ~0x10;
                }
                if (player0.pressed(Button.DOWN)) {
                    value &= ~0x20;
                }
                if (player0.pressed(Button.LEFT)) {
                    value &= ~0x40;
                }
                if (player0.pressed(Button.RIGHT)) {
                    value &= ~0x80;
                }
            }
        }
        if (player1 != null) {
            if (paddleMode(player1)) {
                if (triggerPressed(player1)) {
                    value &= ~0x40;
                }
            } else {
                if (player1.pressed(Button.UP)) {
                    value &= ~0x01;
                }
                if (player1.pressed(Button.DOWN)) {
                    value &= ~0x02;
                }
                if (player1.pressed(Button.LEFT)) {
                    value &= ~0x04;
                }
                if (player1.pressed(Button.RIGHT)) {
                    value &= ~0x08;
                }
            }
        }
        return value & BYTE_MASK;
    }

    /**
     * Некоторые paddle-игры читают trigger с линии SWCHA: P0 — бит 7,
     * P1 — бит 6. В обычном joystick-режиме линия не затрагивается.
     */
    private static boolean paddleMode(Controller controller) {
        return controller instanceof PaddleController paddle && paddle.paddleMode();
    }

    private static boolean triggerPressed(Controller controller) {
        return paddleMode(controller) && controller.pressed(Button.A);
    }

    private int switchInput() {
        int value = DEFAULT_SWITCHES;
        if (panel != null) {
            if (panel.pressed(PanelSwitch.RESET)) {
                value &= ~0x01;
            }
            if (panel.pressed(PanelSwitch.SELECT)) {
                value &= ~0x02;
            }
            if (panel.pressed(PanelSwitch.COLOR)) {
                value |= 0x08;
            } else {
                value &= ~0x08;
            }
            if (panel.pressed(PanelSwitch.DIFFICULTY_LEFT)) {
                value |= 0x40;
            } else {
                value &= ~0x40;
            }
            if (panel.pressed(PanelSwitch.DIFFICULTY_RIGHT)) {
                value |= 0x80;
            } else {
                value &= ~0x80;
            }
        }
        return value & BYTE_MASK;
    }

    @Override
    public String name() {
        return "RIOT 6532 Ports";
    }

    @Override
    public void reset() {
        outputA = 0;
        ddrA = 0;
        outputB = 0;
        ddrB = 0;
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeByte(outputA);
        writer.writeByte(ddrA);
        writer.writeByte(outputB);
        writer.writeByte(ddrB);
    }

    @Override
    public void restore(StateReader reader) {
        outputA = reader.readByte();
        ddrA = reader.readByte();
        outputB = reader.readByte();
        ddrB = reader.readByte();
    }
}
