package ru.viise.jemuxg.hardware.riot.riot6532;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.Memory;
import ru.viise.jemuxg.api.hardware.SwitchPanel;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;

/**
 * Микросхема RIOT 6532 целиком: RAM, таймер и порты.
 *
 * <p>Регистры RIOT занимают смещения {@code $00..$1F} от {@code $0280}.
 * Порты используют {@code $00..$03}, таймер — {@code $14..$17};
 * остальные смещения читаются как 0.</p>
 */
public final class Riot6532Chip implements Riot6532, Bus {

    private static final int PORT_REGISTER_END = 0x03;
    private static final int REGISTER_MASK = 0x1F;

    private final RiotRam ram = new RiotRam();
    private final RiotTimer timer = new RiotTimer();
    private final RiotPorts ports = new RiotPorts();

    @Override
    public Bus registers() {
        return this;
    }

    @Override
    public Memory ram() {
        return ram;
    }

    public RiotTimer timer() {
        return timer;
    }

    public RiotPorts ports() {
        return ports;
    }

    /**
     * @return копия 128 байт встроенной RAM
     */
    public byte[] ramSnapshot() {
        return ram.snapshot();
    }

    /**
     * Снимок регистров RIOT без побочных эффектов.
     *
     * <p>Чтение этого массива не сбрасывает флаг таймера, в отличие от
     * обычного чтения {@code INTIM} через шину.</p>
     */
    public byte[] registerSnapshot() {
        byte[] registers = new byte[Atari2600Constants.RIOT_REGISTER_COUNT];
        registers[RiotPorts.SWCHA] = (byte) ports.read(RiotPorts.SWCHA);
        registers[RiotPorts.SWACNT] = (byte) ports.read(RiotPorts.SWACNT);
        registers[RiotPorts.SWCHB] = (byte) ports.read(RiotPorts.SWCHB);
        registers[RiotPorts.SWBCNT] = (byte) ports.read(RiotPorts.SWBCNT);
        registers[RiotTimer.INTIM] = (byte) timer.timer();
        registers[RiotTimer.INTFLG] = (byte) (timer.underflow() ? 0x80 : 0);
        return registers;
    }

    public void connectPlayer(int port, Controller controller) {
        ports.connectPlayer(port, controller);
    }

    public void connectPanel(SwitchPanel panel) {
        ports.connectPanel(panel);
    }

    @Override
    public int read(int address) {
        int register = address & REGISTER_MASK;
        if (register <= PORT_REGISTER_END) {
            return ports.read(register);
        }
        return timer.registers().read(register);
    }

    @Override
    public void write(int address, int value) {
        int register = address & REGISTER_MASK;
        if (register <= PORT_REGISTER_END) {
            ports.write(register, value);
            return;
        }
        timer.registers().write(register, value);
    }

    @Override
    public String name() {
        return "RIOT 6532";
    }

    @Override
    public void reset() {
        ram.clear();
        timer.reset();
        ports.reset();
    }

    @Override
    public void tick(long cycles) {
        timer.tick(cycles);
    }

    @Override
    public void save(StateWriter writer) {
        ram.save(writer);
        timer.save(writer);
        ports.save(writer);
    }

    @Override
    public void restore(StateReader reader) {
        ram.restore(reader);
        timer.restore(reader);
        ports.restore(reader);
    }
}
