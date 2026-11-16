package ru.viise.jemuxg.hardware.cpu.mos6507;

import java.util.Objects;
import ru.viise.jemuxg.api.debug.CpuDebugger;
import ru.viise.jemuxg.api.debug.CpuRegisters;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Cpu;
import ru.viise.jemuxg.api.hardware.InterruptType;
import ru.viise.jemuxg.hardware.cpu.mos6502.Mos6502;

/**
 * Адаптер MOS 6507 поверх ядра MOS 6502.
 *
 * <p>6507 отличается 13-битной адресной шиной и отсутствием внешних линий
 * IRQ/NMI. Адреса маскируются в {@link Mos6507Bus}; команды и регистры
 * полностью переиспользуются из {@link Mos6502}.</p>
 */
public final class Mos6507 implements Cpu, CpuDebugger {

    private final Mos6507Bus bus;
    private final Mos6502 cpu;

    public Mos6507(Bus bus) {
        Objects.requireNonNull(bus, "bus");
        this.bus = new Mos6507Bus(bus);
        this.cpu = new Mos6502(this.bus, Mos6502.Variant.NMOS_6502);
    }

    /**
     * @return маскирующая шина, через которую работает CPU
     */
    public Mos6507Bus bus() {
        return bus;
    }

    @Override
    public String name() {
        return "MOS 6507";
    }

    @Override
    public void reset() {
        cpu.reset();
    }

    @Override
    public void tick(long cycles) {
        cpu.tick(cycles);
    }

    /**
     * У 6507 нет внешних IRQ/NMI. Поддерживается только сброс.
     */
    @Override
    public void interrupt(InterruptType type) {
        Objects.requireNonNull(type, "type");
        if (type == InterruptType.RESET) {
            cpu.reset();
        }
    }

    @Override
    public void clearInterrupt(InterruptType type) {
        // У 6507 нет линий IRQ/NMI, снимать нечего.
    }

    @Override
    public void save(StateWriter writer) {
        cpu.save(writer);
    }

    @Override
    public void restore(StateReader reader) {
        cpu.restore(reader);
    }

    @Override
    public CpuRegisters registers() {
        return cpu.registers();
    }

    @Override
    public void setRegisters(CpuRegisters registers) {
        cpu.setRegisters(registers);
    }

    @Override
    public void setProgramCounter(int pc) {
        cpu.setProgramCounter(pc);
    }

    @Override
    public int readMemory(int address) {
        return cpu.readMemory(address);
    }

    @Override
    public int stepInstruction() {
        return cpu.stepInstruction();
    }
}
