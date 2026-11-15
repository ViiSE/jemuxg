package ru.viise.jemuxg.hardware.cpu.mos6502;

import java.util.Objects;
import ru.viise.jemuxg.api.debug.CpuDebugger;
import ru.viise.jemuxg.api.debug.CpuRegisters;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Cpu;
import ru.viise.jemuxg.api.hardware.InterruptType;

/**
 * Ядро семейства 6502 (NMOS 6502 и Ricoh 2A03).
 * <p>
 * Исполнение потактовое по бюджету циклов: {@link #tick(long)} расходует
 * бюджет, инструкция выбирается только когда предыдущая доработала. Это
 * позволяет синхронизировать CPU с PPU/APU на уровне тактов, хотя сами
 * обращения к памяти внутри инструкции не размазаны по циклам.
 * <p>
 * Реализован полный официальный набор инструкций, включая десятичную
 * арифметику NMOS 6502, штрафы за пересечение страницы, баг JMP (ind)
 * и прерывания. Неофициальные опкоды (LAX, SAX, DCP и т.п.) — TODO.
 */
public final class Mos6502 implements Cpu, CpuDebugger {

    public enum Variant {
        NMOS_6502,
        RICOH_2A03
    }

    private static final int FLAG_CARRY = 0x01;
    private static final int FLAG_ZERO = 0x02;
    private static final int FLAG_INTERRUPT = 0x04;
    private static final int FLAG_DECIMAL = 0x08;
    private static final int FLAG_BREAK = 0x10;
    private static final int FLAG_UNUSED = 0x20;
    private static final int FLAG_OVERFLOW = 0x40;
    private static final int FLAG_NEGATIVE = 0x80;

    private static final int VECTOR_NMI = 0xFFFA;
    private static final int VECTOR_RESET = 0xFFFC;
    private static final int VECTOR_IRQ = 0xFFFE;

    private enum AddressMode {
        ACCUMULATOR,
        IMMEDIATE,
        ZERO_PAGE,
        ZERO_PAGE_X,
        ZERO_PAGE_Y,
        ABSOLUTE,
        ABSOLUTE_X,
        ABSOLUTE_Y,
        INDIRECT_X,
        INDIRECT_Y
    }

    private final Bus bus;
    private final Variant variant;

    private int a;
    private int x;
    private int y;
    private int sp;
    private int pc;
    private int status;

    private int remainingCycles;
    private boolean pageCrossed;
    private boolean irqLine;
    private boolean nmiPending;

    public Mos6502(Bus bus, Variant variant) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.variant = Objects.requireNonNull(variant, "variant");
        reset();
    }

    @Override
    public String name() {
        return "MOS 6502 (" + variant + ")";
    }

    @Override
    public void reset() {
        a = 0;
        x = 0;
        y = 0;
        sp = 0xFD;
        status = FLAG_INTERRUPT | FLAG_UNUSED;
        pc = read(VECTOR_RESET) | (read(VECTOR_RESET + 1) << 8);
        remainingCycles = 0;
        pageCrossed = false;
        irqLine = false;
        nmiPending = false;
    }

    @Override
    public void tick(long cycles) {
        while (cycles > 0) {
            if (remainingCycles == 0) {
                remainingCycles = beginNextStep();
            }
            long used = Math.min(cycles, remainingCycles);
            remainingCycles -= (int) used;
            cycles -= used;
        }
    }

    private int beginNextStep() {
        if (nmiPending) {
            nmiPending = false;
            serviceInterrupt(VECTOR_NMI);
            return 7;
        }
        if (irqLine && !flag(FLAG_INTERRUPT)) {
            serviceInterrupt(VECTOR_IRQ);
            return 7;
        }
        return executeInstruction();
    }

    @Override
    public void interrupt(InterruptType type) {
        Objects.requireNonNull(type, "type");
        switch (type) {
            case RESET -> reset();
            case NMI -> nmiPending = true;
            case IRQ -> irqLine = true;
        }
    }

    @Override
    public void clearInterrupt(InterruptType type) {
        if (type == InterruptType.IRQ) {
            irqLine = false;
        } else if (type == InterruptType.NMI) {
            nmiPending = false;
        }
    }

    private void serviceInterrupt(int vector) {
        push((pc >> 8) & 0xFF);
        push(pc & 0xFF);
        push((status & ~FLAG_BREAK) | FLAG_UNUSED);
        setFlag(FLAG_INTERRUPT, true);
        pc = read(vector) | (read(vector + 1) << 8);
    }

    private int executeInstruction() {
        int opcode = fetch();
        pageCrossed = false;
        return switch (opcode) {
            case 0x00 -> brk();

            // ORA
            case 0x09 -> ora(AddressMode.IMMEDIATE, 2);
            case 0x05 -> ora(AddressMode.ZERO_PAGE, 3);
            case 0x15 -> ora(AddressMode.ZERO_PAGE_X, 4);
            case 0x0D -> ora(AddressMode.ABSOLUTE, 4);
            case 0x1D -> ora(AddressMode.ABSOLUTE_X, 4);
            case 0x19 -> ora(AddressMode.ABSOLUTE_Y, 4);
            case 0x01 -> ora(AddressMode.INDIRECT_X, 6);
            case 0x11 -> ora(AddressMode.INDIRECT_Y, 5);

            // AND
            case 0x29 -> and(AddressMode.IMMEDIATE, 2);
            case 0x25 -> and(AddressMode.ZERO_PAGE, 3);
            case 0x35 -> and(AddressMode.ZERO_PAGE_X, 4);
            case 0x2D -> and(AddressMode.ABSOLUTE, 4);
            case 0x3D -> and(AddressMode.ABSOLUTE_X, 4);
            case 0x39 -> and(AddressMode.ABSOLUTE_Y, 4);
            case 0x21 -> and(AddressMode.INDIRECT_X, 6);
            case 0x31 -> and(AddressMode.INDIRECT_Y, 5);

            // EOR
            case 0x49 -> eor(AddressMode.IMMEDIATE, 2);
            case 0x45 -> eor(AddressMode.ZERO_PAGE, 3);
            case 0x55 -> eor(AddressMode.ZERO_PAGE_X, 4);
            case 0x4D -> eor(AddressMode.ABSOLUTE, 4);
            case 0x5D -> eor(AddressMode.ABSOLUTE_X, 4);
            case 0x59 -> eor(AddressMode.ABSOLUTE_Y, 4);
            case 0x41 -> eor(AddressMode.INDIRECT_X, 6);
            case 0x51 -> eor(AddressMode.INDIRECT_Y, 5);

            // ADC
            case 0x69 -> adc(AddressMode.IMMEDIATE, 2);
            case 0x65 -> adc(AddressMode.ZERO_PAGE, 3);
            case 0x75 -> adc(AddressMode.ZERO_PAGE_X, 4);
            case 0x6D -> adc(AddressMode.ABSOLUTE, 4);
            case 0x7D -> adc(AddressMode.ABSOLUTE_X, 4);
            case 0x79 -> adc(AddressMode.ABSOLUTE_Y, 4);
            case 0x61 -> adc(AddressMode.INDIRECT_X, 6);
            case 0x71 -> adc(AddressMode.INDIRECT_Y, 5);

            // SBC
            case 0xE9 -> sbc(AddressMode.IMMEDIATE, 2);
            case 0xE5 -> sbc(AddressMode.ZERO_PAGE, 3);
            case 0xF5 -> sbc(AddressMode.ZERO_PAGE_X, 4);
            case 0xED -> sbc(AddressMode.ABSOLUTE, 4);
            case 0xFD -> sbc(AddressMode.ABSOLUTE_X, 4);
            case 0xF9 -> sbc(AddressMode.ABSOLUTE_Y, 4);
            case 0xE1 -> sbc(AddressMode.INDIRECT_X, 6);
            case 0xF1 -> sbc(AddressMode.INDIRECT_Y, 5);

            // CMP / CPX / CPY
            case 0xC9 -> compare(a, AddressMode.IMMEDIATE, 2);
            case 0xC5 -> compare(a, AddressMode.ZERO_PAGE, 3);
            case 0xD5 -> compare(a, AddressMode.ZERO_PAGE_X, 4);
            case 0xCD -> compare(a, AddressMode.ABSOLUTE, 4);
            case 0xDD -> compare(a, AddressMode.ABSOLUTE_X, 4);
            case 0xD9 -> compare(a, AddressMode.ABSOLUTE_Y, 4);
            case 0xC1 -> compare(a, AddressMode.INDIRECT_X, 6);
            case 0xD1 -> compare(a, AddressMode.INDIRECT_Y, 5);
            case 0xE0 -> compare(x, AddressMode.IMMEDIATE, 2);
            case 0xE4 -> compare(x, AddressMode.ZERO_PAGE, 3);
            case 0xEC -> compare(x, AddressMode.ABSOLUTE, 4);
            case 0xC0 -> compare(y, AddressMode.IMMEDIATE, 2);
            case 0xC4 -> compare(y, AddressMode.ZERO_PAGE, 3);
            case 0xCC -> compare(y, AddressMode.ABSOLUTE, 4);

            // LDA
            case 0xA9 -> lda(AddressMode.IMMEDIATE, 2);
            case 0xA5 -> lda(AddressMode.ZERO_PAGE, 3);
            case 0xB5 -> lda(AddressMode.ZERO_PAGE_X, 4);
            case 0xAD -> lda(AddressMode.ABSOLUTE, 4);
            case 0xBD -> lda(AddressMode.ABSOLUTE_X, 4);
            case 0xB9 -> lda(AddressMode.ABSOLUTE_Y, 4);
            case 0xA1 -> lda(AddressMode.INDIRECT_X, 6);
            case 0xB1 -> lda(AddressMode.INDIRECT_Y, 5);

            // LDX
            case 0xA2 -> ldx(AddressMode.IMMEDIATE, 2);
            case 0xA6 -> ldx(AddressMode.ZERO_PAGE, 3);
            case 0xB6 -> ldx(AddressMode.ZERO_PAGE_Y, 4);
            case 0xAE -> ldx(AddressMode.ABSOLUTE, 4);
            case 0xBE -> ldx(AddressMode.ABSOLUTE_Y, 4);

            // LDY
            case 0xA0 -> ldy(AddressMode.IMMEDIATE, 2);
            case 0xA4 -> ldy(AddressMode.ZERO_PAGE, 3);
            case 0xB4 -> ldy(AddressMode.ZERO_PAGE_X, 4);
            case 0xAC -> ldy(AddressMode.ABSOLUTE, 4);
            case 0xBC -> ldy(AddressMode.ABSOLUTE_X, 4);

            // STA
            case 0x85 -> sta(AddressMode.ZERO_PAGE, 3);
            case 0x95 -> sta(AddressMode.ZERO_PAGE_X, 4);
            case 0x8D -> sta(AddressMode.ABSOLUTE, 4);
            case 0x9D -> sta(AddressMode.ABSOLUTE_X, 5);
            case 0x99 -> sta(AddressMode.ABSOLUTE_Y, 5);
            case 0x81 -> sta(AddressMode.INDIRECT_X, 6);
            case 0x91 -> sta(AddressMode.INDIRECT_Y, 6);

            // STX / STY
            case 0x86 -> stx(AddressMode.ZERO_PAGE, 3);
            case 0x96 -> stx(AddressMode.ZERO_PAGE_Y, 4);
            case 0x8E -> stx(AddressMode.ABSOLUTE, 4);
            case 0x84 -> sty(AddressMode.ZERO_PAGE, 3);
            case 0x94 -> sty(AddressMode.ZERO_PAGE_X, 4);
            case 0x8C -> sty(AddressMode.ABSOLUTE, 4);

            // Transfers
            case 0xAA -> transferAtoX();
            case 0x8A -> transferXtoA();
            case 0xA8 -> transferAtoY();
            case 0x98 -> transferYtoA();
            case 0xBA -> transferSPtoX();
            case 0x9A -> transferXtoSP();

            // Stack
            case 0x48 -> pushA();
            case 0x68 -> pullA();
            case 0x08 -> pushStatus();
            case 0x28 -> pullStatus();

            // Increments / decrements
            case 0xE8 -> incrementX();
            case 0xC8 -> incrementY();
            case 0xCA -> decrementX();
            case 0x88 -> decrementY();
            case 0xE6 -> incrementMemory(AddressMode.ZERO_PAGE, 5);
            case 0xF6 -> incrementMemory(AddressMode.ZERO_PAGE_X, 6);
            case 0xEE -> incrementMemory(AddressMode.ABSOLUTE, 6);
            case 0xFE -> incrementMemory(AddressMode.ABSOLUTE_X, 7);
            case 0xC6 -> decrementMemory(AddressMode.ZERO_PAGE, 5);
            case 0xD6 -> decrementMemory(AddressMode.ZERO_PAGE_X, 6);
            case 0xCE -> decrementMemory(AddressMode.ABSOLUTE, 6);
            case 0xDE -> decrementMemory(AddressMode.ABSOLUTE_X, 7);

            // Shifts
            case 0x0A -> asl(AddressMode.ACCUMULATOR, 2);
            case 0x06 -> asl(AddressMode.ZERO_PAGE, 5);
            case 0x16 -> asl(AddressMode.ZERO_PAGE_X, 6);
            case 0x0E -> asl(AddressMode.ABSOLUTE, 6);
            case 0x1E -> asl(AddressMode.ABSOLUTE_X, 7);
            case 0x4A -> lsr(AddressMode.ACCUMULATOR, 2);
            case 0x46 -> lsr(AddressMode.ZERO_PAGE, 5);
            case 0x56 -> lsr(AddressMode.ZERO_PAGE_X, 6);
            case 0x4E -> lsr(AddressMode.ABSOLUTE, 6);
            case 0x5E -> lsr(AddressMode.ABSOLUTE_X, 7);
            case 0x2A -> rol(AddressMode.ACCUMULATOR, 2);
            case 0x26 -> rol(AddressMode.ZERO_PAGE, 5);
            case 0x36 -> rol(AddressMode.ZERO_PAGE_X, 6);
            case 0x2E -> rol(AddressMode.ABSOLUTE, 6);
            case 0x3E -> rol(AddressMode.ABSOLUTE_X, 7);
            case 0x6A -> ror(AddressMode.ACCUMULATOR, 2);
            case 0x66 -> ror(AddressMode.ZERO_PAGE, 5);
            case 0x76 -> ror(AddressMode.ZERO_PAGE_X, 6);
            case 0x6E -> ror(AddressMode.ABSOLUTE, 6);
            case 0x7E -> ror(AddressMode.ABSOLUTE_X, 7);

            // BIT
            case 0x24 -> bit(AddressMode.ZERO_PAGE, 3);
            case 0x2C -> bit(AddressMode.ABSOLUTE, 4);

            // Jumps and subroutines
            case 0x4C -> jmpAbsolute();
            case 0x6C -> jmpIndirect();
            case 0x20 -> jsr();
            case 0x60 -> rts();
            case 0x40 -> rti();

            // Branches
            case 0x10 -> branch(!flag(FLAG_NEGATIVE)); // BPL
            case 0x30 -> branch(flag(FLAG_NEGATIVE));  // BMI
            case 0x50 -> branch(!flag(FLAG_OVERFLOW)); // BVC
            case 0x70 -> branch(flag(FLAG_OVERFLOW));  // BVS
            case 0x90 -> branch(!flag(FLAG_CARRY));    // BCC
            case 0xB0 -> branch(flag(FLAG_CARRY));     // BCS
            case 0xD0 -> branch(!flag(FLAG_ZERO));     // BNE
            case 0xF0 -> branch(flag(FLAG_ZERO));      // BEQ

            // Flags
            case 0x18 -> setFlagAndReturn(FLAG_CARRY, false);
            case 0x38 -> setFlagAndReturn(FLAG_CARRY, true);
            case 0x58 -> setFlagAndReturn(FLAG_INTERRUPT, false);
            case 0x78 -> setFlagAndReturn(FLAG_INTERRUPT, true);
            case 0xB8 -> setFlagAndReturn(FLAG_OVERFLOW, false);
            case 0xD8 -> setFlagAndReturn(FLAG_DECIMAL, false);
            case 0xF8 -> setFlagAndReturn(FLAG_DECIMAL, true);

            // Unofficial opcodes exercised by nestest
            case 0xA7 -> lax(AddressMode.ZERO_PAGE, 3);
            case 0xB7 -> lax(AddressMode.ZERO_PAGE_Y, 4);
            case 0xAF -> lax(AddressMode.ABSOLUTE, 4);
            case 0xBF -> lax(AddressMode.ABSOLUTE_Y, 4);
            case 0xA3 -> lax(AddressMode.INDIRECT_X, 6);
            case 0xB3 -> lax(AddressMode.INDIRECT_Y, 5);

            case 0x87 -> sax(AddressMode.ZERO_PAGE, 3);
            case 0x97 -> sax(AddressMode.ZERO_PAGE_Y, 4);
            case 0x8F -> sax(AddressMode.ABSOLUTE, 4);
            case 0x83 -> sax(AddressMode.INDIRECT_X, 6);

            case 0xC7 -> dcp(AddressMode.ZERO_PAGE, 5);
            case 0xD7 -> dcp(AddressMode.ZERO_PAGE_X, 6);
            case 0xCF -> dcp(AddressMode.ABSOLUTE, 6);
            case 0xDF -> dcp(AddressMode.ABSOLUTE_X, 7);
            case 0xDB -> dcp(AddressMode.ABSOLUTE_Y, 7);
            case 0xC3 -> dcp(AddressMode.INDIRECT_X, 8);
            case 0xD3 -> dcp(AddressMode.INDIRECT_Y, 8);

            case 0xE7 -> isb(AddressMode.ZERO_PAGE, 5);
            case 0xF7 -> isb(AddressMode.ZERO_PAGE_X, 6);
            case 0xEF -> isb(AddressMode.ABSOLUTE, 6);
            case 0xFF -> isb(AddressMode.ABSOLUTE_X, 7);
            case 0xFB -> isb(AddressMode.ABSOLUTE_Y, 7);
            case 0xE3 -> isb(AddressMode.INDIRECT_X, 8);
            case 0xF3 -> isb(AddressMode.INDIRECT_Y, 8);

            case 0x07 -> slo(AddressMode.ZERO_PAGE, 5);
            case 0x17 -> slo(AddressMode.ZERO_PAGE_X, 6);
            case 0x0F -> slo(AddressMode.ABSOLUTE, 6);
            case 0x1F -> slo(AddressMode.ABSOLUTE_X, 7);
            case 0x1B -> slo(AddressMode.ABSOLUTE_Y, 7);
            case 0x03 -> slo(AddressMode.INDIRECT_X, 8);
            case 0x13 -> slo(AddressMode.INDIRECT_Y, 8);

            case 0x27 -> rla(AddressMode.ZERO_PAGE, 5);
            case 0x37 -> rla(AddressMode.ZERO_PAGE_X, 6);
            case 0x2F -> rla(AddressMode.ABSOLUTE, 6);
            case 0x3F -> rla(AddressMode.ABSOLUTE_X, 7);
            case 0x3B -> rla(AddressMode.ABSOLUTE_Y, 7);
            case 0x23 -> rla(AddressMode.INDIRECT_X, 8);
            case 0x33 -> rla(AddressMode.INDIRECT_Y, 8);

            case 0x47 -> sre(AddressMode.ZERO_PAGE, 5);
            case 0x57 -> sre(AddressMode.ZERO_PAGE_X, 6);
            case 0x4F -> sre(AddressMode.ABSOLUTE, 6);
            case 0x5F -> sre(AddressMode.ABSOLUTE_X, 7);
            case 0x5B -> sre(AddressMode.ABSOLUTE_Y, 7);
            case 0x43 -> sre(AddressMode.INDIRECT_X, 8);
            case 0x53 -> sre(AddressMode.INDIRECT_Y, 8);

            case 0x67 -> rra(AddressMode.ZERO_PAGE, 5);
            case 0x77 -> rra(AddressMode.ZERO_PAGE_X, 6);
            case 0x6F -> rra(AddressMode.ABSOLUTE, 6);
            case 0x7F -> rra(AddressMode.ABSOLUTE_X, 7);
            case 0x7B -> rra(AddressMode.ABSOLUTE_Y, 7);
            case 0x63 -> rra(AddressMode.INDIRECT_X, 8);
            case 0x73 -> rra(AddressMode.INDIRECT_Y, 8);

            case 0xEB -> sbc(AddressMode.IMMEDIATE, 2);

            // NOP and unofficial NOPs
            case 0xEA -> 2;
            case 0x1A, 0x3A, 0x5A, 0x7A, 0xDA, 0xFA -> nop(null, 2);
            case 0x80, 0x82, 0x89, 0xC2, 0xE2 -> nop(AddressMode.IMMEDIATE, 2);
            case 0x04, 0x44, 0x64 -> nop(AddressMode.ZERO_PAGE, 3);
            case 0x0C -> nop(AddressMode.ABSOLUTE, 4);
            case 0x14, 0x34, 0x54, 0x74, 0xD4, 0xF4 -> nop(AddressMode.ZERO_PAGE_X, 4);
            case 0x1C, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC -> nop(AddressMode.ABSOLUTE_X, 4);

            default -> throw new IllegalStateException(String.format(
                    "Unimplemented opcode $%02X at $%04X", opcode, (pc - 1) & 0xFFFF));
        };
    }

    private int lda(AddressMode mode, int cycles) {
        a = readOperand(mode);
        setZN(a);
        return penalty(cycles);
    }

    private int ldx(AddressMode mode, int cycles) {
        x = readOperand(mode);
        setZN(x);
        return penalty(cycles);
    }

    private int ldy(AddressMode mode, int cycles) {
        y = readOperand(mode);
        setZN(y);
        return penalty(cycles);
    }

    private int sta(AddressMode mode, int cycles) {
        writeOperand(mode, a);
        return cycles;
    }

    private int stx(AddressMode mode, int cycles) {
        writeOperand(mode, x);
        return cycles;
    }

    private int sty(AddressMode mode, int cycles) {
        writeOperand(mode, y);
        return cycles;
    }

    private int ora(AddressMode mode, int cycles) {
        a |= readOperand(mode);
        setZN(a);
        return penalty(cycles);
    }

    private int and(AddressMode mode, int cycles) {
        a &= readOperand(mode);
        setZN(a);
        return penalty(cycles);
    }

    private int eor(AddressMode mode, int cycles) {
        a ^= readOperand(mode);
        setZN(a);
        return penalty(cycles);
    }

    private int adc(AddressMode mode, int cycles) {
        adcValue(readOperand(mode));
        return penalty(cycles);
    }

    private void adcValue(int value) {
        int carry = flag(FLAG_CARRY) ? 1 : 0;
        int sum = a + value + carry;
        boolean overflow = ((a ^ sum) & (value ^ sum) & 0x80) != 0;
        boolean decimal = variant == Variant.NMOS_6502 && flag(FLAG_DECIMAL);

        int result;
        if (decimal) {
            int low = (a & 0x0F) + (value & 0x0F) + carry;
            int high = (a >> 4) + (value >> 4);
            if (low > 9) {
                low += 6;
                high++;
            }
            if (high > 9) {
                high += 6;
            }
            setFlag(FLAG_CARRY, high > 15);
            result = ((high << 4) | (low & 0x0F)) & 0xFF;
            setZN(sum & 0xFF);
        } else {
            setFlag(FLAG_CARRY, sum > 0xFF);
            result = sum & 0xFF;
            setZN(result);
        }

        a = result;
        setFlag(FLAG_OVERFLOW, overflow);
    }

    private int sbc(AddressMode mode, int cycles) {
        sbcValue(readOperand(mode));
        return penalty(cycles);
    }

    private void sbcValue(int value) {
        int borrow = flag(FLAG_CARRY) ? 0 : 1;
        int difference = a - value - borrow;
        boolean overflow = ((a ^ value) & (a ^ difference) & 0x80) != 0;
        boolean decimal = variant == Variant.NMOS_6502 && flag(FLAG_DECIMAL);

        int result;
        if (decimal) {
            int low = (a & 0x0F) - (value & 0x0F) - borrow;
            int high = (a >> 4) - (value >> 4);
            if ((low & 0x10) != 0) {
                low -= 6;
                high--;
            }
            if ((high & 0x10) != 0) {
                high -= 6;
            }
            result = ((high << 4) | (low & 0x0F)) & 0xFF;
            setFlag(FLAG_CARRY, difference >= 0);
            setZN(difference & 0xFF);
        } else {
            result = difference & 0xFF;
            setFlag(FLAG_CARRY, difference >= 0);
            setZN(result);
        }

        a = result;
        setFlag(FLAG_OVERFLOW, overflow);
    }

    private int compare(int register, AddressMode mode, int cycles) {
        int value = readOperand(mode);
        setFlag(FLAG_CARRY, register >= value);
        setZN((register - value) & 0xFF);
        return penalty(cycles);
    }

    private int asl(AddressMode mode, int cycles) {
        if (mode == AddressMode.ACCUMULATOR) {
            setFlag(FLAG_CARRY, (a & 0x80) != 0);
            a = (a << 1) & 0xFF;
            setZN(a);
            return 2;
        }
        int address = resolveAddress(mode);
        int value = read(address);
        setFlag(FLAG_CARRY, (value & 0x80) != 0);
        value = (value << 1) & 0xFF;
        write(address, value);
        setZN(value);
        return cycles;
    }

    private int lsr(AddressMode mode, int cycles) {
        if (mode == AddressMode.ACCUMULATOR) {
            setFlag(FLAG_CARRY, (a & 0x01) != 0);
            a >>= 1;
            setZN(a);
            return 2;
        }
        int address = resolveAddress(mode);
        int value = read(address);
        setFlag(FLAG_CARRY, (value & 0x01) != 0);
        value >>= 1;
        write(address, value);
        setZN(value);
        return cycles;
    }

    private int rol(AddressMode mode, int cycles) {
        if (mode == AddressMode.ACCUMULATOR) {
            int carryIn = flag(FLAG_CARRY) ? 1 : 0;
            setFlag(FLAG_CARRY, (a & 0x80) != 0);
            a = ((a << 1) | carryIn) & 0xFF;
            setZN(a);
            return 2;
        }
        int address = resolveAddress(mode);
        int value = read(address);
        int carryIn = flag(FLAG_CARRY) ? 1 : 0;
        setFlag(FLAG_CARRY, (value & 0x80) != 0);
        value = ((value << 1) | carryIn) & 0xFF;
        write(address, value);
        setZN(value);
        return cycles;
    }

    private int ror(AddressMode mode, int cycles) {
        if (mode == AddressMode.ACCUMULATOR) {
            int carryIn = flag(FLAG_CARRY) ? 0x80 : 0;
            setFlag(FLAG_CARRY, (a & 0x01) != 0);
            a = (a >> 1) | carryIn;
            setZN(a);
            return 2;
        }
        int address = resolveAddress(mode);
        int value = read(address);
        int carryIn = flag(FLAG_CARRY) ? 0x80 : 0;
        setFlag(FLAG_CARRY, (value & 0x01) != 0);
        value = (value >> 1) | carryIn;
        write(address, value);
        setZN(value);
        return cycles;
    }

    private int bit(AddressMode mode, int cycles) {
        int value = readOperand(mode);
        setFlag(FLAG_ZERO, (a & value) == 0);
        setFlag(FLAG_NEGATIVE, (value & 0x80) != 0);
        setFlag(FLAG_OVERFLOW, (value & 0x40) != 0);
        return cycles;
    }

    private int incrementMemory(AddressMode mode, int cycles) {
        int address = resolveAddress(mode);
        int value = (read(address) + 1) & 0xFF;
        write(address, value);
        setZN(value);
        return cycles;
    }

    private int decrementMemory(AddressMode mode, int cycles) {
        int address = resolveAddress(mode);
        int value = (read(address) - 1) & 0xFF;
        write(address, value);
        setZN(value);
        return cycles;
    }

    private int incrementX() {
        x = (x + 1) & 0xFF;
        setZN(x);
        return 2;
    }

    private int incrementY() {
        y = (y + 1) & 0xFF;
        setZN(y);
        return 2;
    }

    private int decrementX() {
        x = (x - 1) & 0xFF;
        setZN(x);
        return 2;
    }

    private int decrementY() {
        y = (y - 1) & 0xFF;
        setZN(y);
        return 2;
    }

    private int transferAtoX() {
        x = a;
        setZN(x);
        return 2;
    }

    private int transferXtoA() {
        a = x;
        setZN(a);
        return 2;
    }

    private int transferAtoY() {
        y = a;
        setZN(y);
        return 2;
    }

    private int transferYtoA() {
        a = y;
        setZN(a);
        return 2;
    }

    private int transferSPtoX() {
        x = sp;
        setZN(x);
        return 2;
    }

    private int transferXtoSP() {
        sp = x;
        return 2;
    }

    private int pushA() {
        push(a);
        return 3;
    }

    private int pullA() {
        a = pull();
        setZN(a);
        return 4;
    }

    private int pushStatus() {
        push(status | FLAG_BREAK | FLAG_UNUSED);
        return 3;
    }

    private int pullStatus() {
        status = (pull() & ~FLAG_BREAK) | FLAG_UNUSED;
        return 4;
    }

    private int jmpAbsolute() {
        pc = fetchWord();
        return 3;
    }

    private int jmpIndirect() {
        int pointer = fetchWord();
        int low = read(pointer);
        int high = read((pointer & 0xFF00) | ((pointer + 1) & 0xFF));
        pc = low | (high << 8);
        return 5;
    }

    private int jsr() {
        int target = fetchWord();
        int returnAddress = (pc - 1) & 0xFFFF;
        push((returnAddress >> 8) & 0xFF);
        push(returnAddress & 0xFF);
        pc = target;
        return 6;
    }

    private int rts() {
        int low = pull();
        int high = pull();
        pc = ((low | (high << 8)) + 1) & 0xFFFF;
        return 6;
    }

    private int rti() {
        status = (pull() & ~FLAG_BREAK) | FLAG_UNUSED;
        int low = pull();
        int high = pull();
        pc = low | (high << 8);
        return 6;
    }

    private int brk() {
        pc = (pc + 1) & 0xFFFF;
        push((pc >> 8) & 0xFF);
        push(pc & 0xFF);
        push(status | FLAG_BREAK | FLAG_UNUSED);
        setFlag(FLAG_INTERRUPT, true);
        pc = read(VECTOR_IRQ) | (read(VECTOR_IRQ + 1) << 8);
        return 7;
    }

    private int branch(boolean taken) {
        int offset = (byte) fetch();
        if (!taken) {
            return 2;
        }
        int oldPc = pc;
        pc = (pc + offset) & 0xFFFF;
        int cycles = 3;
        if ((oldPc & 0xFF00) != (pc & 0xFF00)) {
            cycles++;
        }
        return cycles;
    }

    private int setFlagAndReturn(int flag, boolean value) {
        setFlag(flag, value);
        return 2;
    }

    private int nop(AddressMode mode, int cycles) {
        if (mode != null) {
            readOperand(mode);
        }
        return penalty(cycles);
    }

    private int lax(AddressMode mode, int cycles) {
        int value = readOperand(mode);
        a = value;
        x = value;
        setZN(value);
        return penalty(cycles);
    }

    private int sax(AddressMode mode, int cycles) {
        writeOperand(mode, a & x);
        return cycles;
    }

    private int dcp(AddressMode mode, int cycles) {
        int address = resolveAddress(mode);
        int value = (read(address) - 1) & 0xFF;
        write(address, value);
        compareValue(value);
        return cycles;
    }

    private int isb(AddressMode mode, int cycles) {
        int address = resolveAddress(mode);
        int value = (read(address) + 1) & 0xFF;
        write(address, value);
        sbcValue(value);
        return cycles;
    }

    private int slo(AddressMode mode, int cycles) {
        int address = resolveAddress(mode);
        int old = read(address);
        setFlag(FLAG_CARRY, (old & 0x80) != 0);
        int value = (old << 1) & 0xFF;
        write(address, value);
        a |= value;
        setZN(a);
        return cycles;
    }

    private int rla(AddressMode mode, int cycles) {
        int address = resolveAddress(mode);
        int old = read(address);
        int carryIn = flag(FLAG_CARRY) ? 1 : 0;
        setFlag(FLAG_CARRY, (old & 0x80) != 0);
        int value = ((old << 1) | carryIn) & 0xFF;
        write(address, value);
        a &= value;
        setZN(a);
        return cycles;
    }

    private int sre(AddressMode mode, int cycles) {
        int address = resolveAddress(mode);
        int old = read(address);
        setFlag(FLAG_CARRY, (old & 0x01) != 0);
        int value = old >> 1;
        write(address, value);
        a ^= value;
        setZN(a);
        return cycles;
    }

    private int rra(AddressMode mode, int cycles) {
        int address = resolveAddress(mode);
        int old = read(address);
        int carryIn = flag(FLAG_CARRY) ? 0x80 : 0;
        setFlag(FLAG_CARRY, (old & 0x01) != 0);
        int value = (old >> 1) | carryIn;
        write(address, value);
        adcValue(value);
        return cycles;
    }

    private void compareValue(int value) {
        setFlag(FLAG_CARRY, a >= value);
        setZN((a - value) & 0xFF);
    }

    private int penalty(int cycles) {
        return pageCrossed ? cycles + 1 : cycles;
    }

    private int readOperand(AddressMode mode) {
        if (mode == AddressMode.IMMEDIATE) {
            return fetch();
        }
        return read(resolveAddress(mode));
    }

    private void writeOperand(AddressMode mode, int value) {
        if (mode == AddressMode.ABSOLUTE_X || mode == AddressMode.ABSOLUTE_Y) {
            int base = fetchWord();
            int index = mode == AddressMode.ABSOLUTE_X ? x : y;
            int address = (base + index) & 0xFFFF;
            boolean pageCrossed = (base & 0xFF00) != (address & 0xFF00);
            int dummy = pageCrossed
                    ? (base & 0xFF00) | ((base + index) & 0xFF)
                    : address;
            read(dummy);
            write(address, value);
            return;
        }
        write(resolveAddress(mode), value);
    }

    private int resolveAddress(AddressMode mode) {
        return switch (mode) {
            case ZERO_PAGE -> fetch();
            case ZERO_PAGE_X -> (fetch() + x) & 0xFF;
            case ZERO_PAGE_Y -> (fetch() + y) & 0xFF;
            case ABSOLUTE -> fetchWord();
            case ABSOLUTE_X -> {
                int base = fetchWord();
                int address = (base + x) & 0xFFFF;
                pageCrossed = (base & 0xFF00) != (address & 0xFF00);
                yield address;
            }
            case ABSOLUTE_Y -> {
                int base = fetchWord();
                int address = (base + y) & 0xFFFF;
                pageCrossed = (base & 0xFF00) != (address & 0xFF00);
                yield address;
            }
            case INDIRECT_X -> {
                int zeroPage = (fetch() + x) & 0xFF;
                int low = read(zeroPage);
                int high = read((zeroPage + 1) & 0xFF);
                yield low | (high << 8);
            }
            case INDIRECT_Y -> {
                int zeroPage = fetch();
                int base = read(zeroPage) | (read((zeroPage + 1) & 0xFF) << 8);
                int address = (base + y) & 0xFFFF;
                pageCrossed = (base & 0xFF00) != (address & 0xFF00);
                yield address;
            }
            case ACCUMULATOR -> throw new IllegalArgumentException("ACCUMULATOR has no address");
            default -> throw new IllegalArgumentException("Unsupported addressing mode " + mode);
        };
    }

    private int fetch() {
        int value = read(pc);
        pc = (pc + 1) & 0xFFFF;
        return value;
    }

    private int fetchWord() {
        int low = fetch();
        int high = fetch();
        return low | (high << 8);
    }

    private int read(int address) {
        return bus.read(address) & 0xFF;
    }

    private void write(int address, int value) {
        bus.write(address, value & 0xFF);
    }

    private void push(int value) {
        write(0x0100 | sp, value);
        sp = (sp - 1) & 0xFF;
    }

    private int pull() {
        sp = (sp + 1) & 0xFF;
        return read(0x0100 | sp);
    }

    private boolean flag(int flag) {
        return (status & flag) != 0;
    }

    private void setFlag(int flag, boolean value) {
        if (value) {
            status |= flag;
        } else {
            status &= ~flag;
        }
    }

    private void setZN(int value) {
        setFlag(FLAG_ZERO, (value & 0xFF) == 0);
        setFlag(FLAG_NEGATIVE, (value & 0x80) != 0);
    }

    @Override
    public CpuRegisters registers() {
        return new CpuRegisters(a, x, y, sp, pc, status);
    }

    @Override
    public void setRegisters(CpuRegisters registers) {
        Objects.requireNonNull(registers, "registers");
        a = registers.a() & 0xFF;
        x = registers.x() & 0xFF;
        y = registers.y() & 0xFF;
        sp = registers.sp() & 0xFF;
        pc = registers.pc() & 0xFFFF;
        status = (registers.status() & 0xFF) | FLAG_UNUSED;
        remainingCycles = 0;
    }

    @Override
    public void setProgramCounter(int pc) {
        this.pc = pc & 0xFFFF;
        remainingCycles = 0;
    }

    @Override
    public int readMemory(int address) {
        return read(address & 0xFFFF);
    }

    @Override
    public int stepInstruction() {
        if (remainingCycles != 0) {
            throw new IllegalStateException(
                    "CPU is in the middle of an instruction: " + remainingCycles + " cycles left");
        }
        int cycles = beginNextStep();
        remainingCycles = 0;
        return cycles;
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeInt(a);
        writer.writeInt(x);
        writer.writeInt(y);
        writer.writeInt(sp);
        writer.writeInt(pc);
        writer.writeInt(status);
        writer.writeInt(remainingCycles);
        writer.writeByte(nmiPending ? 1 : 0);
        writer.writeByte(irqLine ? 1 : 0);
    }

    @Override
    public void restore(StateReader reader) {
        a = reader.readInt() & 0xFF;
        x = reader.readInt() & 0xFF;
        y = reader.readInt() & 0xFF;
        sp = reader.readInt() & 0xFF;
        pc = reader.readInt() & 0xFFFF;
        status = reader.readInt() & 0xFF;
        remainingCycles = reader.readInt();
        nmiPending = reader.readByte() != 0;
        irqLine = reader.readByte() != 0;
        pageCrossed = false;
    }
}
