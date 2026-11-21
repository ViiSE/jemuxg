package ru.viise.jemuxg.hardware.cpu.z80;

import java.util.Objects;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Cpu;
import ru.viise.jemuxg.api.hardware.InterruptType;

/**
 * Zilog Z80, цикл-точный на уровне инструкций.
 *
 * <p>Реализованы документированные команды, префиксы CB/ED/DD/FD,
 * недокументированные DDCB/FDCB, флаги, DAA, прерывания и HALT.
 * Память и порты ввода-вывода разделены: память читается через
 * {@link Z80Bus#read(int)}, порты — через {@link Z80Bus#in(int)} и
 * {@link Z80Bus#out(int, int)}.</p>
 */
public final class Z80 implements Cpu {

    public static final int FLAG_C = 0x01;
    public static final int FLAG_N = 0x02;
    public static final int FLAG_PV = 0x04;
    public static final int FLAG_X = 0x08;
    public static final int FLAG_H = 0x10;
    public static final int FLAG_Y = 0x20;
    public static final int FLAG_Z = 0x40;
    public static final int FLAG_S = 0x80;

    private static final int STATE_VERSION = 1;

    private final Z80Bus bus;
    private final Z80Registers regs = new Z80Registers();

    private int remainingCycles;
    private boolean halted;
    private boolean irqLine;
    private boolean nmiPending;
    private boolean eiPending;
    private int irqData = 0xFF;

    private int currentPrefix;
    private int currentDisp;

    public Z80(Z80Bus bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
        reset();
    }

    @Override
    public String name() {
        return "Zilog Z80";
    }

    @Override
    public void reset() {
        regs.reset();
        remainingCycles = 0;
        halted = false;
        irqLine = false;
        nmiPending = false;
        eiPending = false;
        irqData = 0xFF;
        currentPrefix = 0;
        currentDisp = 0;
    }

    public Z80Registers registers() {
        return regs;
    }

    public boolean instructionComplete() {
        return remainingCycles == 0;
    }

    public boolean halted() {
        return halted;
    }

    public boolean iff1() {
        return regs.iff1;
    }

    public int im() {
        return regs.im;
    }

    public int remainingCycles() {
        return remainingCycles;
    }

    /**
     * Данные для IM 2; на реальной шине их выставляет периферия.
     */
    public void setIrqData(int data) {
        irqData = data & 0xFF;
    }

    /**
     * Выполнить одну инструкцию и вернуть её длительность в T-тактах.
     */
    public int step() {
        if (remainingCycles != 0) {
            throw new IllegalStateException(
                    "CPU is in the middle of an instruction: " + remainingCycles + " cycles left");
        }
        return beginStep();
    }

    public int stepInstruction() {
        return step();
    }

    @Override
    public void tick(long cycles) {
        if (cycles < 0) {
            throw new IllegalArgumentException("cycles must be >= 0, got " + cycles);
        }
        while (cycles > 0) {
            if (remainingCycles == 0) {
                remainingCycles = beginStep();
            }
            long used = Math.min(cycles, remainingCycles);
            remainingCycles -= (int) used;
            cycles -= used;
        }
    }

    @Override
    public void interrupt(InterruptType type) {
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
        }
    }

    public int readMemory(int address) {
        return read(address);
    }

    private int beginStep() {
        if (halted) {
            if (nmiPending) {
                nmiPending = false;
                halted = false;
                return serviceNmi();
            }
            if (irqLine && regs.iff1 && !eiPending) {
                halted = false;
                return serviceIrq();
            }
            // HALT выполняет NOP-подобные M1-циклы.
            regs.r = (regs.r + 1) & 0x7F;
            return 4;
        }

        if (nmiPending) {
            nmiPending = false;
            return serviceNmi();
        }
        if (irqLine && regs.iff1 && !eiPending) {
            return serviceIrq();
        }

        eiPending = false;
        return executeInstruction();
    }

    private int serviceNmi() {
        push16(regs.pc);
        regs.iff2 = regs.iff1;
        regs.iff1 = false;
        regs.pc = 0x0066;
        regs.memptr = 0x0066;
        eiPending = false;
        return 11;
    }

    private int serviceIrq() {
        push16(regs.pc);
        regs.iff2 = regs.iff1;
        regs.iff1 = false;
        eiPending = false;

        return switch (regs.im) {
            case 2 -> {
                int vector = ((regs.i & 0xFF) << 8) | (irqData & 0xFF);
                int low = read(vector);
                int high = read((vector + 1) & 0xFFFF);
                regs.pc = low | (high << 8);
                regs.memptr = regs.pc;
                yield 19;
            }
            case 1 -> {
                regs.pc = 0x0038;
                regs.memptr = 0x0038;
                yield 13;
            }
            default -> {
                // IM 0: без внешней команды ведём себя как RST 38h.
                regs.pc = 0x0038;
                regs.memptr = 0x0038;
                yield 13;
            }
        };
    }

    private int executeInstruction() {
        currentPrefix = 0;
        currentDisp = 0;

        int opcode = fetchOpcode();
        int prefix = 0;

        while (opcode == 0xDD || opcode == 0xFD) {
            prefix = opcode == 0xDD ? 1 : 2;
            opcode = fetchOpcode();
        }

        if (opcode == 0xCB) {
            if (prefix == 0) {
                return executeCb(fetchOpcode());
            }
            currentPrefix = prefix;
            int disp = (byte) fetchByte();
            currentDisp = disp;
            int cbOpcode = fetchByte();
            return executeDdCb(disp, cbOpcode);
        }

        if (opcode == 0xED) {
            currentPrefix = 0;
            currentDisp = 0;
            return executeEd(fetchOpcode());
        }

        currentPrefix = prefix;
        currentDisp = 0;
        return executeBase(opcode);
    }

    private int executeBase(int opcode) {
        switch (opcode) {
            case 0x00:
                return 4 + prefixExtra();

            case 0x01, 0x11, 0x21, 0x31: {
                int pair = (opcode >> 4) & 0x03;
                setPair(pair, fetchWord());
                return 10 + prefixExtra();
            }

            case 0x02:
                write(regs.bc(), regs.a);
                return 7 + prefixExtra();
            case 0x12:
                write(regs.de(), regs.a);
                return 7 + prefixExtra();
            case 0x0A:
                regs.a = read(regs.bc());
                return 7 + prefixExtra();
            case 0x1A:
                regs.a = read(regs.de());
                return 7 + prefixExtra();

            case 0x03, 0x13, 0x23, 0x33: {
                int pair = (opcode >> 4) & 0x03;
                setPair(pair, (pair(pair) + 1) & 0xFFFF);
                return 6 + prefixExtra();
            }
            case 0x0B, 0x1B, 0x2B, 0x3B: {
                int pair = (opcode >> 4) & 0x03;
                setPair(pair, (pair(pair) - 1) & 0xFFFF);
                return 6 + prefixExtra();
            }

            case 0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C: {
                int index = (opcode >> 3) & 0x07;
                if (index == 6) {
                    readIndexedDisp();
                    int address = addressHL();
                    write(address, inc8(read(address)));
                    return 11 + indexedExtra();
                }
                writeR(index, inc8(readR(index)));
                return 4 + prefixExtra();
            }
            case 0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D: {
                int index = (opcode >> 3) & 0x07;
                if (index == 6) {
                    readIndexedDisp();
                    int address = addressHL();
                    write(address, dec8(read(address)));
                    return 11 + indexedExtra();
                }
                writeR(index, dec8(readR(index)));
                return 4 + prefixExtra();
            }
            case 0x06, 0x0E, 0x16, 0x1E, 0x26, 0x2E, 0x36, 0x3E: {
                int index = (opcode >> 3) & 0x07;
                if (index == 6) {
                    readIndexedDisp();
                    write(addressHL(), fetchByte());
                    return currentPrefix == 0 ? 10 : 19;
                }
                writeR(index, fetchByte());
                return 7 + prefixExtra();
            }

            case 0x07:
                rotateAccumulator(0);
                return 4 + prefixExtra();
            case 0x0F:
                rotateAccumulator(1);
                return 4 + prefixExtra();
            case 0x17:
                rotateAccumulator(2);
                return 4 + prefixExtra();
            case 0x1F:
                rotateAccumulator(3);
                return 4 + prefixExtra();

            case 0x08: {
                int af = regs.af();
                regs.setAf(regs.af2());
                regs.setAf2(af);
                return 4 + prefixExtra();
            }

            case 0x10: {
                int displacement = (byte) fetchByte();
                regs.b = (regs.b - 1) & 0xFF;
                if (regs.b != 0) {
                    regs.pc = (regs.pc + displacement) & 0xFFFF;
                    regs.memptr = regs.pc;
                    return 13 + prefixExtra();
                }
                return 8 + prefixExtra();
            }

            case 0x18: {
                int displacement = (byte) fetchByte();
                regs.pc = (regs.pc + displacement) & 0xFFFF;
                regs.memptr = regs.pc;
                return 12 + prefixExtra();
            }

            case 0x20, 0x28, 0x30, 0x38: {
                int displacement = (byte) fetchByte();
                if (condition((opcode >> 3) & 0x03)) {
                    regs.pc = (regs.pc + displacement) & 0xFFFF;
                    regs.memptr = regs.pc;
                    return 12 + prefixExtra();
                }
                return 7 + prefixExtra();
            }

            case 0x09, 0x19, 0x29, 0x39: {
                int source = (opcode >> 4) & 0x03;
                add16(getHL(), pair(source));
                return 11 + prefixExtra();
            }

            case 0x22: {
                int address = fetchWord();
                write16(address, getHL());
                regs.memptr = (address + 1) & 0xFFFF;
                return 16 + prefixExtra();
            }
            case 0x2A: {
                int address = fetchWord();
                setHL(read16(address));
                regs.memptr = (address + 1) & 0xFFFF;
                return 16 + prefixExtra();
            }
            case 0x32: {
                int address = fetchWord();
                write(address, regs.a);
                regs.memptr = (address + 1) & 0xFFFF;
                return 13 + prefixExtra();
            }
            case 0x3A: {
                int address = fetchWord();
                regs.a = read(address);
                regs.memptr = (address + 1) & 0xFFFF;
                return 13 + prefixExtra();
            }

            case 0x27:
                daa();
                return 4 + prefixExtra();
            case 0x2F:
                cpl();
                return 4 + prefixExtra();
            case 0x37:
                scf();
                return 4 + prefixExtra();
            case 0x3F:
                ccf();
                return 4 + prefixExtra();

            case 0x76:
                halted = true;
                return 4 + prefixExtra();

            case 0xC0, 0xC8, 0xD0, 0xD8, 0xE0, 0xE8, 0xF0, 0xF8:
                if (condition((opcode >> 3) & 0x07)) {
                    regs.pc = pop16();
                    regs.memptr = regs.pc;
                    return 11 + prefixExtra();
                }
                return 5 + prefixExtra();
            case 0xC9:
                regs.pc = pop16();
                regs.memptr = regs.pc;
                return 10 + prefixExtra();

            case 0xC1, 0xD1, 0xE1, 0xF1: {
                int pair = (opcode >> 4) & 0x03;
                int value = pop16();
                if (pair == 3) {
                    regs.a = (value >> 8) & 0xFF;
                    regs.f = value & 0xFF;
                } else {
                    setPair(pair, value);
                }
                return 10 + prefixExtra();
            }
            case 0xC5, 0xD5, 0xE5, 0xF5: {
                int pair = (opcode >> 4) & 0x03;
                push16(pair == 3 ? regs.af() : pair(pair));
                return 11 + prefixExtra();
            }

            case 0xC2, 0xCA, 0xD2, 0xDA, 0xE2, 0xEA, 0xF2, 0xFA: {
                int address = fetchWord();
                regs.memptr = address;
                if (condition((opcode >> 3) & 0x07)) {
                    regs.pc = address;
                }
                return 10 + prefixExtra();
            }
            case 0xC3: {
                int address = fetchWord();
                regs.pc = address;
                regs.memptr = address;
                return 10 + prefixExtra();
            }
            case 0xC4, 0xCC, 0xD4, 0xDC, 0xE4, 0xEC, 0xF4, 0xFC: {
                int address = fetchWord();
                regs.memptr = address;
                if (condition((opcode >> 3) & 0x07)) {
                    push16(regs.pc);
                    regs.pc = address;
                    return 17 + prefixExtra();
                }
                return 10 + prefixExtra();
            }
            case 0xCD: {
                int address = fetchWord();
                push16(regs.pc);
                regs.pc = address;
                regs.memptr = address;
                return 17 + prefixExtra();
            }

            case 0xC6:
                add8(fetchByte(), 0);
                return 7 + prefixExtra();
            case 0xCE:
                add8(fetchByte(), carry());
                return 7 + prefixExtra();
            case 0xD6:
                sub8(fetchByte(), 0);
                return 7 + prefixExtra();
            case 0xDE:
                sub8(fetchByte(), carry());
                return 7 + prefixExtra();
            case 0xE6:
                and8(fetchByte());
                return 7 + prefixExtra();
            case 0xEE:
                xor8(fetchByte());
                return 7 + prefixExtra();
            case 0xF6:
                or8(fetchByte());
                return 7 + prefixExtra();
            case 0xFE:
                cp8(fetchByte());
                return 7 + prefixExtra();

            case 0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF: {
                int vector = opcode & 0x38;
                push16(regs.pc);
                regs.pc = vector;
                regs.memptr = vector;
                return 11 + prefixExtra();
            }

            case 0xD3: {
                int port = ((regs.a & 0xFF) << 8) | fetchByte();
                out(port, regs.a);
                return 11 + prefixExtra();
            }
            case 0xDB: {
                int port = ((regs.a & 0xFF) << 8) | fetchByte();
                regs.a = in(port);
                return 11 + prefixExtra();
            }

            case 0xD9:
                exx();
                return 4 + prefixExtra();

            case 0xE3: {
                int low = read(regs.sp);
                int high = read((regs.sp + 1) & 0xFFFF);
                int old = getHL();
                write16(regs.sp, old);
                setHL(low | (high << 8));
                regs.memptr = old;
                return 19 + prefixExtra();
            }

            case 0xE9:
                regs.pc = getHL();
                return 4 + prefixExtra();

            case 0xEB: {
                int de = regs.de();
                regs.setDe(regs.hl());
                regs.setHl(de);
                return 4 + prefixExtra();
            }

            case 0xF3:
                regs.iff1 = false;
                regs.iff2 = false;
                return 4 + prefixExtra();

            case 0xF9:
                regs.sp = getHL();
                return 6 + prefixExtra();

            case 0xFB:
                regs.iff1 = true;
                regs.iff2 = true;
                eiPending = true;
                return 4 + prefixExtra();

            default:
                return executeRegisterBlock(opcode);
        }
    }

    private int executeRegisterBlock(int opcode) {
        if (opcode >= 0x40 && opcode <= 0x7F) {
            int destination = (opcode >> 3) & 0x07;
            int source = opcode & 0x07;

            if (source == 6) {
                readIndexedDisp();
                int value = read(addressHL());
                writeRPlain(destination, value);
                return 7 + indexedExtra();
            }
            if (destination == 6) {
                readIndexedDisp();
                write(addressHL(), readRPlain(source));
                return 7 + indexedExtra();
            }
            writeR(destination, readR(source));
            return 4 + prefixExtra();
        }

        if (opcode >= 0x80 && opcode <= 0xBF) {
            int source = opcode & 0x07;
            int value;
            int cycles;
            if (source == 6) {
                readIndexedDisp();
                value = read(addressHL());
                cycles = 7 + indexedExtra();
            } else {
                value = readR(source);
                cycles = 4 + prefixExtra();
            }
            alu((opcode >> 3) & 0x07, value);
            return cycles;
        }

        throw new IllegalStateException("Unhandled Z80 opcode: " + hex(opcode));
    }

    private int executeCb(int opcode) {
        int group = opcode >> 6;
        int bit = (opcode >> 3) & 0x07;
        int index = opcode & 0x07;
        int value = readR(index);

        return switch (group) {
            case 0 -> {
                writeR(index, shiftRotate(bit, value));
                yield index == 6 ? 15 : 8;
            }
            case 1 -> {
                bitFlags(bit, value);
                yield index == 6 ? 12 : 8;
            }
            case 2 -> {
                writeR(index, value & ~(1 << bit));
                yield index == 6 ? 15 : 8;
            }
            default -> {
                writeR(index, value | (1 << bit));
                yield index == 6 ? 15 : 8;
            }
        };
    }

    private int executeDdCb(int displacement, int opcode) {
        int address = (getHL() + displacement) & 0xFFFF;
        int value = read(address);
        int group = opcode >> 6;
        int bit = (opcode >> 3) & 0x07;
        int index = opcode & 0x07;

        if (group == 1) {
            bitFlags(bit, value);
            return 20;
        }

        int result = switch (group) {
            case 0 -> shiftRotate(bit, value);
            case 2 -> value & ~(1 << bit);
            default -> value | (1 << bit);
        };

        write(address, result);
        if (index != 6) {
            writeR(index, result);
        }
        return 23;
    }

    private int executeEd(int opcode) {
        return switch (opcode) {
            case 0x40, 0x48, 0x50, 0x58, 0x60, 0x68, 0x78 -> {
                int index = (opcode >> 3) & 0x07;
                int port = (regs.b << 8) | regs.c;
                int value = in(port);
                writeR(index, value);
                flagsIn(value);
                yield 12;
            }
            case 0x70 -> {
                int port = (regs.b << 8) | regs.c;
                int value = in(port);
                flagsIn(value);
                yield 12;
            }
            case 0x41, 0x49, 0x51, 0x59, 0x61, 0x69, 0x79 -> {
                int index = (opcode >> 3) & 0x07;
                int port = (regs.b << 8) | regs.c;
                out(port, readR(index));
                yield 12;
            }
            case 0x71 -> {
                int port = (regs.b << 8) | regs.c;
                out(port, 0);
                yield 12;
            }

            case 0x42, 0x52, 0x62, 0x72 -> {
                int source = (opcode >> 4) & 0x03;
                sbc16(pair(source));
                yield 15;
            }
            case 0x4A, 0x5A, 0x6A, 0x7A -> {
                int source = (opcode >> 4) & 0x03;
                adc16(pair(source));
                yield 15;
            }

            case 0x43, 0x53, 0x63, 0x73 -> {
                int source = (opcode >> 4) & 0x03;
                int address = fetchWord();
                write16(address, pair(source));
                regs.memptr = (address + 1) & 0xFFFF;
                yield 20;
            }
            case 0x4B, 0x5B, 0x6B, 0x7B -> {
                int source = (opcode >> 4) & 0x03;
                int address = fetchWord();
                setPair(source, read16(address));
                regs.memptr = (address + 1) & 0xFFFF;
                yield 20;
            }

            case 0x44, 0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C -> {
                neg();
                yield 8;
            }

            case 0x45, 0x55, 0x65, 0x75, 0x5D, 0x6D, 0x7D, 0x4D -> {
                regs.pc = pop16();
                regs.iff1 = regs.iff2;
                regs.memptr = regs.pc;
                yield 14;
            }

            case 0x46, 0x4E, 0x56, 0x5E, 0x66, 0x6E, 0x76, 0x7E -> {
                int mode = switch (opcode) {
                    case 0x56, 0x76 -> 1;
                    case 0x5E, 0x7E -> 2;
                    default -> 0;
                };
                regs.im = mode;
                yield 8;
            }

            case 0x47 -> {
                regs.i = regs.a;
                yield 9;
            }
            case 0x4F -> {
                regs.r = regs.a;
                yield 9;
            }
            case 0x57 -> {
                regs.a = regs.i;
                flagsLoadIorR();
                yield 9;
            }
            case 0x5F -> {
                regs.a = regs.r;
                flagsLoadIorR();
                yield 9;
            }

            case 0x67 -> {
                rrd();
                yield 18;
            }
            case 0x6F -> {
                rld();
                yield 18;
            }

            case 0xA0 -> {
                blockLoad(1, false);
                yield 16;
            }
            case 0xA8 -> {
                blockLoad(-1, false);
                yield 16;
            }
            case 0xB0 -> {
                yield blockLoad(1, true);
            }
            case 0xB8 -> {
                yield blockLoad(-1, true);
            }

            case 0xA1 -> {
                blockCompare(1, false);
                yield 16;
            }
            case 0xA9 -> {
                blockCompare(-1, false);
                yield 16;
            }
            case 0xB1 -> {
                yield blockCompare(1, true);
            }
            case 0xB9 -> {
                yield blockCompare(-1, true);
            }

            case 0xA2 -> {
                blockIn(1, false, false);
                yield 16;
            }
            case 0xAA -> {
                blockIn(-1, false, false);
                yield 16;
            }
            case 0xB2 -> {
                yield blockIn(1, true, false);
            }
            case 0xBA -> {
                yield blockIn(-1, true, false);
            }

            case 0xA3 -> {
                blockIn(1, false, true);
                yield 16;
            }
            case 0xAB -> {
                blockIn(-1, false, true);
                yield 16;
            }
            case 0xB3 -> {
                yield blockIn(1, true, true);
            }
            case 0xBB -> {
                yield blockIn(-1, true, true);
            }

            default -> 8;
        };
    }

    private int fetchOpcode() {
        regs.r = (regs.r & 0x80) | ((regs.r + 1) & 0x7F);
        return fetchByte();
    }

    private int fetchByte() {
        int value = read(regs.pc);
        regs.pc = (regs.pc + 1) & 0xFFFF;
        return value;
    }

    private int fetchWord() {
        int low = fetchByte();
        int high = fetchByte();
        return low | (high << 8);
    }

    private int read(int address) {
        return bus.read(address & 0xFFFF) & 0xFF;
    }

    private void write(int address, int value) {
        bus.write(address & 0xFFFF, value & 0xFF);
    }

    private int read16(int address) {
        return read(address) | (read((address + 1) & 0xFFFF) << 8);
    }

    private void write16(int address, int value) {
        write(address, value & 0xFF);
        write((address + 1) & 0xFFFF, (value >> 8) & 0xFF);
    }

    private int in(int port) {
        return bus.in(port & 0xFFFF) & 0xFF;
    }

    private void out(int port, int value) {
        bus.out(port & 0xFFFF, value & 0xFF);
    }

    private void push16(int value) {
        regs.sp = (regs.sp - 1) & 0xFFFF;
        write(regs.sp, (value >> 8) & 0xFF);
        regs.sp = (regs.sp - 1) & 0xFFFF;
        write(regs.sp, value & 0xFF);
    }

    private int pop16() {
        int low = read(regs.sp);
        regs.sp = (regs.sp + 1) & 0xFFFF;
        int high = read(regs.sp);
        regs.sp = (regs.sp + 1) & 0xFFFF;
        return low | (high << 8);
    }

    private int getHL() {
        return switch (currentPrefix) {
            case 1 -> regs.ix;
            case 2 -> regs.iy;
            default -> regs.hl();
        };
    }

    private void setHL(int value) {
        value &= 0xFFFF;
        switch (currentPrefix) {
            case 1 -> regs.ix = value;
            case 2 -> regs.iy = value;
            default -> regs.setHl(value);
        }
    }

    private int pair(int index) {
        return switch (index) {
            case 0 -> regs.bc();
            case 1 -> regs.de();
            case 2 -> getHL();
            default -> regs.sp;
        };
    }

    private void setPair(int index, int value) {
        value &= 0xFFFF;
        switch (index) {
            case 0 -> regs.setBc(value);
            case 1 -> regs.setDe(value);
            case 2 -> setHL(value);
            default -> regs.sp = value;
        }
    }

    private void readIndexedDisp() {
        if (currentPrefix != 0) {
            currentDisp = (byte) fetchByte();
        }
    }

    private int addressHL() {
        if (currentPrefix == 0) {
            return regs.hl();
        }
        return (getHL() + currentDisp) & 0xFFFF;
    }

    private int readR(int index) {
        return switch (index) {
            case 0 -> regs.b;
            case 1 -> regs.c;
            case 2 -> regs.d;
            case 3 -> regs.e;
            case 4 -> currentPrefix == 0 ? regs.h : high(getHL());
            case 5 -> currentPrefix == 0 ? regs.l : low(getHL());
            case 6 -> read(addressHL());
            default -> regs.a;
        };
    }

    private void writeR(int index, int value) {
        value &= 0xFF;
        switch (index) {
            case 0 -> regs.b = value;
            case 1 -> regs.c = value;
            case 2 -> regs.d = value;
            case 3 -> regs.e = value;
            case 4 -> {
                if (currentPrefix == 0) {
                    regs.h = value;
                } else {
                    setHL((value << 8) | low(getHL()));
                }
            }
            case 5 -> {
                if (currentPrefix == 0) {
                    regs.l = value;
                } else {
                    setHL((high(getHL()) << 8) | value);
                }
            }
            case 6 -> write(addressHL(), value);
            default -> regs.a = value;
        }
    }

    private int readRPlain(int index) {
        return switch (index) {
            case 0 -> regs.b;
            case 1 -> regs.c;
            case 2 -> regs.d;
            case 3 -> regs.e;
            case 4 -> regs.h;
            case 5 -> regs.l;
            case 6 -> read(addressHL());
            default -> regs.a;
        };
    }

    private void writeRPlain(int index, int value) {
        value &= 0xFF;
        switch (index) {
            case 0 -> regs.b = value;
            case 1 -> regs.c = value;
            case 2 -> regs.d = value;
            case 3 -> regs.e = value;
            case 4 -> regs.h = value;
            case 5 -> regs.l = value;
            case 6 -> write(addressHL(), value);
            default -> regs.a = value;
        }
    }

    private static int high(int value) {
        return (value >> 8) & 0xFF;
    }

    private static int low(int value) {
        return value & 0xFF;
    }

    private int prefixExtra() {
        return currentPrefix == 0 ? 0 : 4;
    }

    private int indexedExtra() {
        return currentPrefix == 0 ? 0 : 12;
    }

    private boolean condition(int code) {
        return switch (code) {
            case 0 -> (regs.f & FLAG_Z) == 0;
            case 1 -> (regs.f & FLAG_Z) != 0;
            case 2 -> (regs.f & FLAG_C) == 0;
            case 3 -> (regs.f & FLAG_C) != 0;
            case 4 -> (regs.f & FLAG_PV) == 0;
            case 5 -> (regs.f & FLAG_PV) != 0;
            case 6 -> (regs.f & FLAG_S) == 0;
            default -> (regs.f & FLAG_S) != 0;
        };
    }

    private int carry() {
        return (regs.f & FLAG_C) != 0 ? 1 : 0;
    }

    private void add8(int value, int carry) {
        int a = regs.a;
        int result = a + value + carry;
        int r = result & 0xFF;
        int flags = r & (FLAG_S | FLAG_X | FLAG_Y);
        if (r == 0) {
            flags |= FLAG_Z;
        }
        if (((a ^ value ^ result) & FLAG_H) != 0) {
            flags |= FLAG_H;
        }
        if (((a ^ ~value) & (a ^ result) & FLAG_S) != 0) {
            flags |= FLAG_PV;
        }
        if (result > 0xFF) {
            flags |= FLAG_C;
        }
        regs.a = r;
        regs.f = flags;
    }

    private void sub8(int value, int carry) {
        int a = regs.a;
        int result = a - value - carry;
        int r = result & 0xFF;
        int flags = r & (FLAG_S | FLAG_X | FLAG_Y);
        if (r == 0) {
            flags |= FLAG_Z;
        }
        if (((a ^ value ^ result) & FLAG_H) != 0) {
            flags |= FLAG_H;
        }
        if (((a ^ value) & (a ^ result) & FLAG_S) != 0) {
            flags |= FLAG_PV;
        }
        flags |= FLAG_N;
        if (result < 0) {
            flags |= FLAG_C;
        }
        regs.a = r;
        regs.f = flags;
    }

    private void cp8(int value) {
        int a = regs.a;
        int result = a - value;
        int r = result & 0xFF;
        int flags = r & (FLAG_S | FLAG_X | FLAG_Y);
        if (r == 0) {
            flags |= FLAG_Z;
        }
        if (((a ^ value ^ result) & FLAG_H) != 0) {
            flags |= FLAG_H;
        }
        if (((a ^ value) & (a ^ result) & FLAG_S) != 0) {
            flags |= FLAG_PV;
        }
        flags |= FLAG_N;
        if (result < 0) {
            flags |= FLAG_C;
        }
        regs.f = flags;
    }

    private void and8(int value) {
        regs.a &= value;
        regs.f = (regs.a & (FLAG_S | FLAG_X | FLAG_Y))
                | (regs.a == 0 ? FLAG_Z : 0)
                | FLAG_H
                | (parity(regs.a) ? FLAG_PV : 0);
    }

    private void or8(int value) {
        regs.a |= value;
        regs.f = (regs.a & (FLAG_S | FLAG_X | FLAG_Y))
                | (regs.a == 0 ? FLAG_Z : 0)
                | (parity(regs.a) ? FLAG_PV : 0);
    }

    private void xor8(int value) {
        regs.a ^= value;
        regs.f = (regs.a & (FLAG_S | FLAG_X | FLAG_Y))
                | (regs.a == 0 ? FLAG_Z : 0)
                | (parity(regs.a) ? FLAG_PV : 0);
    }

    private void alu(int operation, int value) {
        switch (operation) {
            case 0 -> add8(value, 0);
            case 1 -> add8(value, carry());
            case 2 -> sub8(value, 0);
            case 3 -> sub8(value, carry());
            case 4 -> and8(value);
            case 5 -> xor8(value);
            case 6 -> or8(value);
            default -> cp8(value);
        }
    }

    private int inc8(int value) {
        int result = (value + 1) & 0xFF;
        int flags = (regs.f & FLAG_C)
                | (result & (FLAG_S | FLAG_X | FLAG_Y))
                | (result == 0 ? FLAG_Z : 0)
                | (value == 0x7F ? FLAG_PV : 0)
                | (((value & 0x0F) == 0x0F) ? FLAG_H : 0);
        regs.f = flags;
        return result;
    }

    private int dec8(int value) {
        int result = (value - 1) & 0xFF;
        int flags = (regs.f & FLAG_C)
                | (result & (FLAG_S | FLAG_X | FLAG_Y))
                | (result == 0 ? FLAG_Z : 0)
                | (value == 0x80 ? FLAG_PV : 0)
                | FLAG_N
                | (((value & 0x0F) == 0x00) ? FLAG_H : 0);
        regs.f = flags;
        return result;
    }

    private void neg() {
        int value = regs.a;
        int result = (0 - value) & 0xFF;
        int flags = (result & (FLAG_S | FLAG_X | FLAG_Y))
                | (result == 0 ? FLAG_Z : 0)
                | (((value & 0x0F) != 0) ? FLAG_H : 0)
                | (value == 0x80 ? FLAG_PV : 0)
                | FLAG_N
                | (value != 0 ? FLAG_C : 0);
        regs.a = result;
        regs.f = flags;
    }

    private void add16(int left, int right) {
        int result = left + right;
        int flags = regs.f & (FLAG_S | FLAG_Z | FLAG_PV);
        flags |= (result >> 8) & (FLAG_X | FLAG_Y);
        if (((left ^ right ^ result) & 0x1000) != 0) {
            flags |= FLAG_H;
        }
        if (result > 0xFFFF) {
            flags |= FLAG_C;
        }
        regs.f = flags;
        setHL(result & 0xFFFF);
    }

    private void adc16(int right) {
        int left = getHL();
        int carry = carry();
        int result = left + right + carry;
        int r = result & 0xFFFF;
        int flags = (r >> 8) & (FLAG_S | FLAG_X | FLAG_Y);
        if (r == 0) {
            flags |= FLAG_Z;
        }
        if (((left ^ right ^ result) & 0x1000) != 0) {
            flags |= FLAG_H;
        }
        if (((left ^ ~right) & (left ^ result) & 0x8000) != 0) {
            flags |= FLAG_PV;
        }
        if (result > 0xFFFF) {
            flags |= FLAG_C;
        }
        regs.f = flags;
        setHL(r);
    }

    private void sbc16(int right) {
        int left = getHL();
        int carry = carry();
        int result = left - right - carry;
        int r = result & 0xFFFF;
        int flags = (r >> 8) & (FLAG_S | FLAG_X | FLAG_Y);
        if (r == 0) {
            flags |= FLAG_Z;
        }
        if (((left ^ right ^ result) & 0x1000) != 0) {
            flags |= FLAG_H;
        }
        if (((left ^ right) & (left ^ result) & 0x8000) != 0) {
            flags |= FLAG_PV;
        }
        flags |= FLAG_N;
        if (result < 0) {
            flags |= FLAG_C;
        }
        regs.f = flags;
        setHL(r);
    }

    private void rotateAccumulator(int type) {
        int value = regs.a;
        int oldCarry = carry();
        int result;
        int newCarry;

        switch (type) {
            case 0 -> {
                newCarry = (value & 0x80) != 0 ? 1 : 0;
                result = ((value << 1) | newCarry) & 0xFF;
            }
            case 1 -> {
                newCarry = value & 1;
                result = (value >> 1) | (newCarry != 0 ? 0x80 : 0);
            }
            case 2 -> {
                newCarry = (value & 0x80) != 0 ? 1 : 0;
                result = ((value << 1) | oldCarry) & 0xFF;
            }
            default -> {
                newCarry = value & 1;
                result = (value >> 1) | (oldCarry != 0 ? 0x80 : 0);
            }
        }

        regs.a = result;
        regs.f = (regs.f & (FLAG_S | FLAG_Z | FLAG_PV))
                | (result & (FLAG_X | FLAG_Y))
                | (newCarry != 0 ? FLAG_C : 0);
    }

    private int shiftRotate(int type, int value) {
        int result;
        int newCarry;
        int oldCarry = carry();

        switch (type) {
            case 0 -> {
                newCarry = (value & 0x80) != 0 ? 1 : 0;
                result = ((value << 1) | newCarry) & 0xFF;
            }
            case 1 -> {
                newCarry = value & 1;
                result = (value >> 1) | (newCarry != 0 ? 0x80 : 0);
            }
            case 2 -> {
                newCarry = (value & 0x80) != 0 ? 1 : 0;
                result = ((value << 1) | oldCarry) & 0xFF;
            }
            case 3 -> {
                newCarry = value & 1;
                result = (value >> 1) | (oldCarry != 0 ? 0x80 : 0);
            }
            case 4 -> {
                newCarry = (value & 0x80) != 0 ? 1 : 0;
                result = (value << 1) & 0xFF;
            }
            case 5 -> {
                newCarry = value & 1;
                result = (value >> 1) | (value & 0x80);
            }
            case 6 -> {
                newCarry = (value & 0x80) != 0 ? 1 : 0;
                result = ((value << 1) | 1) & 0xFF;
            }
            default -> {
                newCarry = value & 1;
                result = value >> 1;
            }
        }

        int flags = (result & (FLAG_S | FLAG_X | FLAG_Y))
                | (result == 0 ? FLAG_Z : 0)
                | (parity(result) ? FLAG_PV : 0)
                | (newCarry != 0 ? FLAG_C : 0);
        regs.f = flags;
        return result;
    }

    private void bitFlags(int bit, int value) {
        int flags = regs.f & FLAG_C;
        if ((value & (1 << bit)) == 0) {
            flags |= FLAG_Z | FLAG_PV;
        }
        if (bit == 7 && (value & 0x80) != 0) {
            flags |= FLAG_S;
        }
        flags |= FLAG_H;
        flags |= value & (FLAG_X | FLAG_Y);
        regs.f = flags;
    }

    private void daa() {
        int value = regs.a;
        int correction = 0;
        boolean carry = (regs.f & FLAG_C) != 0;

        if ((regs.f & FLAG_H) != 0 || (value & 0x0F) > 9) {
            correction |= 0x06;
        }
        if (carry || value > 0x99) {
            correction |= 0x60;
            carry = true;
        }

        int result;
        if ((regs.f & FLAG_N) != 0) {
            result = (value - correction) & 0xFF;
        } else {
            result = (value + correction) & 0xFF;
        }

        int flags = (regs.f & FLAG_N)
                | (result & (FLAG_S | FLAG_X | FLAG_Y))
                | (result == 0 ? FLAG_Z : 0)
                | (parity(result) ? FLAG_PV : 0)
                | (carry ? FLAG_C : 0);
        if (((value ^ result ^ correction) & FLAG_H) != 0) {
            flags |= FLAG_H;
        }
        regs.a = result;
        regs.f = flags;
    }

    private void cpl() {
        regs.a = (~regs.a) & 0xFF;
        regs.f = (regs.f & (FLAG_S | FLAG_Z | FLAG_PV | FLAG_C))
                | (regs.a & (FLAG_X | FLAG_Y))
                | FLAG_H
                | FLAG_N;
    }

    private void scf() {
        regs.f = (regs.f & (FLAG_S | FLAG_Z | FLAG_PV))
                | (regs.a & (FLAG_X | FLAG_Y))
                | FLAG_C;
    }

    private void ccf() {
        int oldCarry = carry();
        regs.f = (regs.f & (FLAG_S | FLAG_Z | FLAG_PV))
                | (regs.a & (FLAG_X | FLAG_Y))
                | (oldCarry != 0 ? FLAG_H : FLAG_C);
    }

    private void flagsIn(int value) {
        regs.f = (value & (FLAG_S | FLAG_X | FLAG_Y))
                | (value == 0 ? FLAG_Z : 0)
                | (parity(value) ? FLAG_PV : 0)
                | (regs.f & FLAG_C);
    }

    private void flagsLoadIorR() {
        regs.f = (regs.a & (FLAG_S | FLAG_X | FLAG_Y))
                | (regs.a == 0 ? FLAG_Z : 0)
                | (regs.iff2 ? FLAG_PV : 0)
                | (regs.f & FLAG_C);
    }

    private void exx() {
        int temp = regs.bc();
        regs.setBc(regs.bc2());
        regs.setBc2(temp);
        temp = regs.de();
        regs.setDe(regs.de2());
        regs.setDe2(temp);
        temp = regs.hl();
        regs.setHl(regs.hl2());
        regs.setHl2(temp);
    }

    private void rrd() {
        int address = regs.hl();
        int value = read(address);
        int result = ((regs.a & 0x0F) << 4) | (value >> 4);
        write(address, result);
        regs.a = (regs.a & 0xF0) | (value & 0x0F);
        flagsRldRrd();
    }

    private void rld() {
        int address = regs.hl();
        int value = read(address);
        int result = ((value << 4) & 0xF0) | (regs.a & 0x0F);
        write(address, result);
        regs.a = (regs.a & 0xF0) | (value >> 4);
        flagsRldRrd();
    }

    private void flagsRldRrd() {
        regs.f = (regs.a & (FLAG_S | FLAG_X | FLAG_Y))
                | (regs.a == 0 ? FLAG_Z : 0)
                | (parity(regs.a) ? FLAG_PV : 0)
                | (regs.f & FLAG_C);
    }

    private int blockLoad(int delta, boolean repeat) {
        int value = read(regs.hl());
        write(regs.de(), value);
        regs.setHl((regs.hl() + delta) & 0xFFFF);
        regs.setDe((regs.de() + delta) & 0xFFFF);
        regs.setBc((regs.bc() - 1) & 0xFFFF);

        int sum = (regs.a + value) & 0xFF;
        int flags = regs.f & (FLAG_S | FLAG_Z | FLAG_C);
        if (regs.bc() != 0) {
            flags |= FLAG_PV;
        }
        flags |= sum & FLAG_X;
        if ((sum & 0x02) != 0) {
            flags |= FLAG_Y;
        }
        regs.f = flags;

        if (repeat && regs.bc() != 0) {
            regs.pc = (regs.pc - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    private int blockCompare(int delta, boolean repeat) {
        int value = read(regs.hl());
        int result = (regs.a - value) & 0xFF;
        regs.setHl((regs.hl() + delta) & 0xFFFF);
        regs.setBc((regs.bc() - 1) & 0xFFFF);

        int flags = (result & (FLAG_S | FLAG_X | FLAG_Y))
                | (result == 0 ? FLAG_Z : 0)
                | (regs.bc() != 0 ? FLAG_PV : 0)
                | FLAG_N
                | (regs.f & FLAG_C);
        if (((regs.a ^ value ^ result) & FLAG_H) != 0) {
            flags |= FLAG_H;
        }
        if ((result & 0x02) != 0) {
            flags &= ~FLAG_Y;
            flags |= FLAG_Y;
        }
        regs.f = flags;
        regs.memptr = (regs.hl() + delta) & 0xFFFF;

        if (repeat && regs.bc() != 0 && (flags & FLAG_Z) == 0) {
            regs.pc = (regs.pc - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    private int blockIn(int delta, boolean repeat, boolean output) {
        int port = (regs.b << 8) | regs.c;
        int value;
        if (output) {
            value = read(regs.hl());
            regs.b = (regs.b - 1) & 0xFF;
            out((regs.b << 8) | regs.c, value);
            regs.setHl((regs.hl() + delta) & 0xFFFF);
        } else {
            value = in(port);
            write(regs.hl(), value);
            regs.b = (regs.b - 1) & 0xFF;
            regs.setHl((regs.hl() + delta) & 0xFFFF);
        }

        int flags = (regs.f & (FLAG_C | FLAG_H));
        if (regs.b == 0) {
            flags |= FLAG_Z;
        }
        flags |= FLAG_N;
        if (parity((value + ((regs.c + 1) & 0xFF)) & 0xFF)) {
            flags |= FLAG_PV;
        }
        regs.f = flags;

        if (repeat && regs.b != 0) {
            regs.pc = (regs.pc - 2) & 0xFFFF;
            return 21;
        }
        return 16;
    }

    private static boolean parity(int value) {
        return (Integer.bitCount(value & 0xFF) & 1) == 0;
    }

    private static String hex(int value) {
        return String.format("0x%02X", value & 0xFF);
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeInt(STATE_VERSION);
        writer.writeInt(regs.a);
        writer.writeInt(regs.f);
        writer.writeInt(regs.b);
        writer.writeInt(regs.c);
        writer.writeInt(regs.d);
        writer.writeInt(regs.e);
        writer.writeInt(regs.h);
        writer.writeInt(regs.l);

        writer.writeInt(regs.a2);
        writer.writeInt(regs.f2);
        writer.writeInt(regs.b2);
        writer.writeInt(regs.c2);
        writer.writeInt(regs.d2);
        writer.writeInt(regs.e2);
        writer.writeInt(regs.h2);
        writer.writeInt(regs.l2);

        writer.writeInt(regs.ix);
        writer.writeInt(regs.iy);
        writer.writeInt(regs.sp);
        writer.writeInt(regs.pc);
        writer.writeInt(regs.i);
        writer.writeInt(regs.r);
        writer.writeInt(regs.memptr);
        writer.writeByte(regs.iff1 ? 1 : 0);
        writer.writeByte(regs.iff2 ? 1 : 0);
        writer.writeInt(regs.im);

        writer.writeInt(remainingCycles);
        writer.writeByte(halted ? 1 : 0);
        writer.writeByte(irqLine ? 1 : 0);
        writer.writeByte(nmiPending ? 1 : 0);
        writer.writeByte(eiPending ? 1 : 0);
        writer.writeInt(irqData);
    }

    @Override
    public void restore(StateReader reader) {
        int version = reader.readInt();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException("unsupported Z80 state version: " + version);
        }

        regs.a = reader.readInt() & 0xFF;
        regs.f = reader.readInt() & 0xFF;
        regs.b = reader.readInt() & 0xFF;
        regs.c = reader.readInt() & 0xFF;
        regs.d = reader.readInt() & 0xFF;
        regs.e = reader.readInt() & 0xFF;
        regs.h = reader.readInt() & 0xFF;
        regs.l = reader.readInt() & 0xFF;

        regs.a2 = reader.readInt() & 0xFF;
        regs.f2 = reader.readInt() & 0xFF;
        regs.b2 = reader.readInt() & 0xFF;
        regs.c2 = reader.readInt() & 0xFF;
        regs.d2 = reader.readInt() & 0xFF;
        regs.e2 = reader.readInt() & 0xFF;
        regs.h2 = reader.readInt() & 0xFF;
        regs.l2 = reader.readInt() & 0xFF;

        regs.ix = reader.readInt() & 0xFFFF;
        regs.iy = reader.readInt() & 0xFFFF;
        regs.sp = reader.readInt() & 0xFFFF;
        regs.pc = reader.readInt() & 0xFFFF;
        regs.i = reader.readInt() & 0xFF;
        regs.r = reader.readInt() & 0xFF;
        regs.memptr = reader.readInt() & 0xFFFF;
        regs.iff1 = reader.readByte() != 0;
        regs.iff2 = reader.readByte() != 0;
        regs.im = reader.readInt();

        remainingCycles = reader.readInt();
        halted = reader.readByte() != 0;
        irqLine = reader.readByte() != 0;
        nmiPending = reader.readByte() != 0;
        eiPending = reader.readByte() != 0;
        irqData = reader.readInt() & 0xFF;
        currentPrefix = 0;
        currentDisp = 0;
    }
}
