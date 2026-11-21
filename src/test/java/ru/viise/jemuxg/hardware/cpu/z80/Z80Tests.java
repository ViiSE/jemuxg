package ru.viise.jemuxg.hardware.cpu.z80;

import java.util.Arrays;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;

/**
 * Контрактные проверки Z80: команды, флаги, префиксы, прерывания и такты.
 */
public final class Z80Tests {

    private static int checks;

    private Z80Tests() {
    }

    public static void main(String[] args) {
        resetState();
        opcodeCoverage();
        loadsAndStack();
        exchangeInstructions();
        arithmeticAndFlags();
        decimalAdjust();
        sixteenBitArithmetic();
        accumulatorRotates();
        cbOperations();
        controlFlow();
        indexedOperations();
        ddcbOperations();
        edOperations();
        blockOperations();
        interrupts();
        haltAndEiDelay();
        cyclesAndRefresh();
        saveRestore();
        System.out.println("Z80 tests OK, checks=" + checks);
    }

    private static void opcodeCoverage() {
        int cases = 0;

        for (int opcode = 0; opcode < 256; opcode++) {
            runOpcode(new int[] {opcode, 0, 0, 0});
            cases++;
        }
        for (int opcode = 0; opcode < 256; opcode++) {
            runOpcode(new int[] {0xCB, opcode, 0, 0});
            cases++;
        }
        for (int opcode = 0; opcode < 256; opcode++) {
            runOpcode(new int[] {0xED, opcode, 0, 0});
            cases++;
        }
        for (int opcode = 0; opcode < 256; opcode++) {
            runOpcode(new int[] {0xDD, opcode, 0, 0});
            runOpcode(new int[] {0xFD, opcode, 0, 0});
            cases += 2;
        }
        for (int opcode = 0; opcode < 256; opcode++) {
            runOpcode(new int[] {0xDD, 0xCB, 0, opcode});
            runOpcode(new int[] {0xFD, 0xCB, 0, opcode});
            cases += 2;
        }

        check(true, "all Z80 opcode groups execute (" + cases + " cases)");
    }

    private static void runOpcode(int[] code) {
        TestBus bus = new TestBus();
        bus.load(0, code);
        Z80 cpu = new Z80(bus);
        cpu.registers().setBc(0x4000);
        cpu.registers().setDe(0x4000);
        cpu.registers().setHl(0x4000);
        cpu.registers().ix = 0x4000;
        cpu.registers().iy = 0x4000;
        cpu.registers().sp = 0x8000;
        try {
            int cycles = cpu.step();
            if (cycles <= 0) {
                throw new AssertionError("non-positive cycles for opcode sequence "
                        + Arrays.toString(code));
            }
        } catch (RuntimeException error) {
            throw new AssertionError(
                    "opcode sequence failed: " + Arrays.toString(code), error);
        }
    }

    private static void resetState() {
        TestBus bus = new TestBus();
        Z80 cpu = new Z80(bus);
        check("Zilog Z80".equals(cpu.name()), "Z80 name");
        check(cpu.registers().pc == 0, "reset PC=0");
        check(cpu.registers().sp == 0xFFFF, "reset SP=FFFF");
        check(cpu.registers().i == 0 && cpu.registers().r == 0, "reset I/R=0");
        check(!cpu.iff1() && !cpu.registers().iff2, "reset IFF1/IFF2=0");
        check(cpu.im() == 0, "reset IM=0");

        cpu.registers().a = 0x12;
        cpu.registers().f = 0x34;
        cpu.registers().ix = 0x1234;
        cpu.registers().iff1 = true;
        cpu.registers().im = 2;
        cpu.reset();
        check(cpu.registers().a == 0 && cpu.registers().f == 0
                && cpu.registers().ix == 0, "reset clears registers");
        check(!cpu.iff1() && cpu.im() == 0, "reset clears interrupt state");
    }

    private static void loadsAndStack() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus,
                0x01, 0x34, 0x12,       // LD BC,1234
                0x11, 0x78, 0x56,       // LD DE,5678
                0x21, 0xBC, 0x9A,       // LD HL,9ABC
                0x31, 0x00, 0x80,       // LD SP,8000
                0x3E, 0x5A,             // LD A,5A
                0x32, 0x00, 0x20,       // LD (2000),A
                0x3A, 0x00, 0x20,       // LD A,(2000)
                0x36, 0x77,             // LD (HL),77
                0x7E,                   // LD A,(HL)
                0x46,                   // LD B,(HL)
                0x70,                   // LD (HL),B
                0x44,                   // LD B,H
                0x4D,                   // LD C,L
                0x62,                   // LD H,D
                0x6B,                   // LD L,E
                0x22, 0x00, 0x30,       // LD (3000),HL
                0x2A, 0x00, 0x30,       // LD HL,(3000)
                0x00);

        check(cpu.step() == 10 && cpu.registers().bc() == 0x1234, "LD BC,nn");
        check(cpu.step() == 10 && cpu.registers().de() == 0x5678, "LD DE,nn");
        check(cpu.step() == 10 && cpu.registers().hl() == 0x9ABC, "LD HL,nn");
        check(cpu.step() == 10 && cpu.registers().sp == 0x8000, "LD SP,nn");
        check(cpu.step() == 7 && cpu.registers().a == 0x5A, "LD A,n");
        check(cpu.step() == 13 && bus.read(0x2000) == 0x5A
                && cpu.registers().memptr == 0x2001, "LD (nn),A and MEMPTR");
        check(cpu.step() == 13 && cpu.registers().a == 0x5A, "LD A,(nn)");
        check(cpu.step() == 10 && bus.read(0x9ABC) == 0x77, "LD (HL),n");
        check(cpu.step() == 7 && cpu.registers().a == 0x77, "LD A,(HL)");
        check(cpu.step() == 7 && cpu.registers().b == 0x77, "LD B,(HL)");
        check(cpu.step() == 7 && bus.read(0x9ABC) == 0x77, "LD (HL),B");
        check(cpu.step() == 4 && cpu.registers().b == 0x9A, "LD B,H");
        check(cpu.step() == 4 && cpu.registers().c == 0xBC, "LD C,L");
        check(cpu.step() == 4 && cpu.registers().h == 0x56, "LD H,D");
        check(cpu.step() == 4 && cpu.registers().l == 0x78
                && cpu.registers().hl() == 0x5678, "LD L,E");
        check(cpu.step() == 16 && bus.read(0x3000) == 0x78
                && bus.read(0x3001) == 0x56, "LD (nn),HL");
        check(cpu.step() == 16 && cpu.registers().hl() == 0x5678, "LD HL,(nn)");

        TestBus stackBus = new TestBus();
        Z80 stack = cpu(stackBus,
                0x31, 0x00, 0x80,       // LD SP,8000
                0x3E, 0x12,             // LD A,12
                0x06, 0x34,             // LD B,34
                0x0E, 0x56,             // LD C,56
                0xF5,                   // PUSH AF
                0xC5,                   // PUSH BC
                0x3E, 0x00,             // LD A,0
                0x06, 0x00,             // LD B,0
                0x0E, 0x00,             // LD C,0
                0xC1,                   // POP BC
                0xF1,                   // POP AF
                0x00);
        stack.step();
        stack.step();
        stack.step();
        stack.step();
        check(stack.step() == 11 && stack.registers().sp == 0x7FFE
                && stackBus.read(0x7FFE) == 0x00
                && stackBus.read(0x7FFF) == 0x12,
                "PUSH AF stores low/high bytes");
        check(stack.step() == 11 && stack.registers().sp == 0x7FFC,
                "PUSH BC decrements SP");
        stack.step();
        stack.step();
        stack.step();
        check(stack.step() == 10 && stack.registers().bc() == 0x3456
                && stack.registers().sp == 0x7FFE, "POP BC restores BC/SP");
        check(stack.step() == 10 && stack.registers().a == 0x12
                && stack.registers().sp == 0x8000, "POP AF restores A");
    }

    private static void exchangeInstructions() {
        TestBus bus = new TestBus();
        Z80 cpu = new Z80(bus);
        cpu.registers().setAf(0x1234);
        cpu.registers().setAf2(0x5678);
        bus.load(0, 0x08);
        check(cpu.step() == 4 && cpu.registers().af() == 0x5678
                && cpu.registers().af2() == 0x1234, "EX AF,AF'");
        cpu.registers().setBc(0x1111);
        cpu.registers().setDe(0x2222);
        cpu.registers().setHl(0x3333);
        cpu.registers().setBc2(0x4444);
        cpu.registers().setDe2(0x5555);
        cpu.registers().setHl2(0x6666);
        cpu.registers().pc = 0;
        bus.load(0, 0xD9);
        check(cpu.step() == 4 && cpu.registers().bc() == 0x4444
                && cpu.registers().de() == 0x5555
                && cpu.registers().hl() == 0x6666
                && cpu.registers().bc2() == 0x1111,
                "EXX swaps register pairs");
        cpu.registers().setDe(0x1234);
        cpu.registers().setHl(0x5678);
        cpu.registers().pc = 0;
        bus.load(0, 0xEB);
        check(cpu.step() == 4 && cpu.registers().de() == 0x5678
                && cpu.registers().hl() == 0x1234, "EX DE,HL");
        cpu.registers().setHl(0x1234);
        cpu.registers().sp = 0x8000;
        bus.write16(0x8000, 0x5678);
        cpu.registers().pc = 0;
        bus.load(0, 0xE3);
        check(cpu.step() == 19 && cpu.registers().hl() == 0x5678
                && bus.read16(0x8000) == 0x1234, "EX (SP),HL");
        cpu.registers().setHl(0x8000);
        cpu.registers().pc = 0;
        bus.load(0, 0xF9);
        check(cpu.step() == 6 && cpu.registers().sp == 0x8000, "LD SP,HL");
    }

    private static void arithmeticAndFlags() {
        checkAdd(0x0F, 0x01, 0x10, true, false, false, false,
                "ADD half carry");
        checkAdd(0x7F, 0x01, 0x80, true, false, true, true,
                "ADD overflow");
        checkAdd(0xFF, 0x01, 0x00, true, true, false, false,
                "ADD wrap");
        checkAdd(0x7F, 0x00, 0x7F, false, false, false, false,
                "ADD zero does not alter sign");

        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0xCE, 0x01);
        cpu.registers().a = 0x10;
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 7 && cpu.registers().a == 0x12
                && (cpu.registers().f & Z80.FLAG_C) == 0,
                "ADC A,n uses carry");

        cpu = cpu(bus, 0xD6, 0x01);
        cpu.registers().a = 0x80;
        cpu.step();
        check(cpu.registers().a == 0x7F
                && has(cpu, Z80.FLAG_N | Z80.FLAG_H | Z80.FLAG_PV)
                && !has(cpu, Z80.FLAG_S | Z80.FLAG_C | Z80.FLAG_Z),
                "SUB flags");

        cpu = cpu(bus, 0xD6, 0x01);
        cpu.registers().a = 0x00;
        cpu.step();
        check(cpu.registers().a == 0xFF
                && has(cpu, Z80.FLAG_N | Z80.FLAG_S | Z80.FLAG_H | Z80.FLAG_C)
                && !has(cpu, Z80.FLAG_Z | Z80.FLAG_PV), "SUB borrow flags");

        cpu = cpu(bus, 0xDE, 0x01);
        cpu.registers().a = 0x10;
        cpu.registers().f = Z80.FLAG_C;
        cpu.step();
        check(cpu.registers().a == 0x0E && has(cpu, Z80.FLAG_N | Z80.FLAG_H)
                && !has(cpu, Z80.FLAG_C), "SBC A,n flags");

        cpu = cpu(bus, 0xE6, 0x0F);
        cpu.registers().a = 0xF0;
        cpu.step();
        check(cpu.registers().a == 0x00
                && has(cpu, Z80.FLAG_Z | Z80.FLAG_H | Z80.FLAG_PV)
                && !has(cpu, Z80.FLAG_N | Z80.FLAG_C | Z80.FLAG_S), "AND flags");

        cpu = cpu(bus, 0xF6, 0x0F);
        cpu.registers().a = 0xF0;
        cpu.step();
        check(cpu.registers().a == 0xFF
                && has(cpu, Z80.FLAG_S | Z80.FLAG_PV)
                && !has(cpu, Z80.FLAG_Z | Z80.FLAG_H | Z80.FLAG_N
                        | Z80.FLAG_C), "OR flags");

        cpu = cpu(bus, 0xEE, 0x0F);
        cpu.registers().a = 0xF0;
        cpu.step();
        check(cpu.registers().a == 0xFF
                && has(cpu, Z80.FLAG_S | Z80.FLAG_PV)
                && !has(cpu, Z80.FLAG_Z | Z80.FLAG_H | Z80.FLAG_N
                        | Z80.FLAG_C), "XOR flags");

        cpu = cpu(bus, 0xFE, 0x10);
        cpu.registers().a = 0x10;
        cpu.step();
        check(cpu.registers().a == 0x10 && has(cpu, Z80.FLAG_Z | Z80.FLAG_N)
                && !has(cpu, Z80.FLAG_C | Z80.FLAG_H), "CP equal flags");

        cpu = cpu(bus, 0xFE, 0x20);
        cpu.registers().a = 0x10;
        cpu.step();
        check(cpu.registers().a == 0x10
                && has(cpu, Z80.FLAG_S | Z80.FLAG_N | Z80.FLAG_C)
                && !has(cpu, Z80.FLAG_Z | Z80.FLAG_H), "CP borrow flags");

        cpu = cpu(bus, 0x3C);
        cpu.registers().a = 0x0F;
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 4 && cpu.registers().a == 0x10
                && has(cpu, Z80.FLAG_C | Z80.FLAG_H)
                && !has(cpu, Z80.FLAG_N | Z80.FLAG_Z | Z80.FLAG_S
                        | Z80.FLAG_PV), "INC A preserves carry");

        cpu = cpu(bus, 0x3C);
        cpu.registers().a = 0x7F;
        cpu.registers().f = Z80.FLAG_C;
        cpu.step();
        check(cpu.registers().a == 0x80
                && has(cpu, Z80.FLAG_C | Z80.FLAG_H | Z80.FLAG_S | Z80.FLAG_PV),
                "INC overflow");

        cpu = cpu(bus, 0x3D);
        cpu.registers().a = 0x10;
        cpu.registers().f = Z80.FLAG_C;
        cpu.step();
        check(cpu.registers().a == 0x0F
                && has(cpu, Z80.FLAG_C | Z80.FLAG_H | Z80.FLAG_N)
                && !has(cpu, Z80.FLAG_Z | Z80.FLAG_PV), "DEC A preserves carry");

        cpu = cpu(bus, 0x34);
        cpu.registers().setHl(0x2000);
        bus.write(0x2000, 0x0F);
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 11 && bus.read(0x2000) == 0x10
                && has(cpu, Z80.FLAG_C | Z80.FLAG_H), "INC (HL) preserves carry");

        cpu = cpu(bus, 0x35);
        cpu.registers().setHl(0x2000);
        bus.write(0x2000, 0x10);
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 11 && bus.read(0x2000) == 0x0F
                && has(cpu, Z80.FLAG_C | Z80.FLAG_H | Z80.FLAG_N),
                "DEC (HL) preserves carry");
    }

    private static void checkAdd(int a, int value, int expected, boolean h,
                                 boolean c, boolean s, boolean overflow, String message) {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0xC6, value);
        cpu.registers().a = a;
        cpu.step();
        check(cpu.registers().a == expected
                && has(cpu, Z80.FLAG_H) == h
                && has(cpu, Z80.FLAG_C) == c
                && has(cpu, Z80.FLAG_S) == s
                && has(cpu, Z80.FLAG_PV) == overflow, message);
    }

    private static void decimalAdjust() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0xC6, 0x01, 0x27);
        cpu.registers().a = 0x09;
        cpu.step();
        check(cpu.step() == 4 && cpu.registers().a == 0x10
                && has(cpu, Z80.FLAG_H) && !has(cpu, Z80.FLAG_C | Z80.FLAG_N),
                "DAA after BCD 9+1");

        cpu = cpu(bus, 0x27);
        cpu.registers().a = 0x9A;
        cpu.step();
        check(cpu.registers().a == 0x00
                && has(cpu, Z80.FLAG_Z | Z80.FLAG_C | Z80.FLAG_H)
                && !has(cpu, Z80.FLAG_N), "DAA 9A -> 00 with carry");

        cpu = cpu(bus, 0x27);
        cpu.registers().a = 0xFF;
        cpu.registers().f = Z80.FLAG_N | Z80.FLAG_H | Z80.FLAG_C;
        cpu.step();
        check(cpu.registers().a == 0x99
                && has(cpu, Z80.FLAG_N | Z80.FLAG_C | Z80.FLAG_S | Z80.FLAG_PV)
                && !has(cpu, Z80.FLAG_H | Z80.FLAG_Z), "DAA after BCD borrow");

        cpu = cpu(bus, 0xC6, 0x27, 0x27);
        cpu.registers().a = 0x15;
        cpu.step();
        cpu.step();
        check(cpu.registers().a == 0x42 && !has(cpu, Z80.FLAG_C)
                && has(cpu, Z80.FLAG_H), "DAA BCD 15+27=42");
    }

    private static void sixteenBitArithmetic() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0x09);
        cpu.registers().setHl(0x1234);
        cpu.registers().setBc(0x1111);
        cpu.registers().f = Z80.FLAG_S | Z80.FLAG_Z | Z80.FLAG_PV;
        check(cpu.step() == 11 && cpu.registers().hl() == 0x2345
                && (cpu.registers().f & (Z80.FLAG_S | Z80.FLAG_Z | Z80.FLAG_PV))
                == (Z80.FLAG_S | Z80.FLAG_Z | Z80.FLAG_PV)
                && !has(cpu, Z80.FLAG_H | Z80.FLAG_C | Z80.FLAG_N),
                "ADD HL,BC preserves S/Z/PV");

        cpu = cpu(bus, 0x09);
        cpu.registers().setHl(0xFFFF);
        cpu.registers().setBc(0x0001);
        cpu.step();
        check(cpu.registers().hl() == 0x0000
                && has(cpu, Z80.FLAG_H | Z80.FLAG_C), "ADD HL,BC carry");

        cpu = cpu(bus, 0xED, 0x4A);
        cpu.registers().setHl(0x7FFF);
        cpu.registers().setBc(0x0001);
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 15 && cpu.registers().hl() == 0x8001
                && has(cpu, Z80.FLAG_S | Z80.FLAG_PV | Z80.FLAG_H)
                && !has(cpu, Z80.FLAG_C | Z80.FLAG_N), "ADC HL,BC flags");

        cpu = cpu(bus, 0xED, 0x42);
        cpu.registers().setHl(0x0000);
        cpu.registers().setBc(0x0001);
        cpu.registers().f = Z80.FLAG_C;
        cpu.step();
        check(cpu.registers().hl() == 0xFFFE
                && has(cpu, Z80.FLAG_S | Z80.FLAG_N | Z80.FLAG_H | Z80.FLAG_C),
                "SBC HL,BC flags");

        cpu = cpu(bus, 0x03);
        cpu.registers().setBc(0xFFFF);
        cpu.registers().f = 0xFF;
        check(cpu.step() == 6 && cpu.registers().bc() == 0
                && cpu.registers().f == 0xFF, "INC BC does not affect flags");

        cpu = cpu(bus, 0x1B);
        cpu.registers().setDe(0x0000);
        cpu.registers().f = 0xFF;
        check(cpu.step() == 6 && cpu.registers().de() == 0xFFFF
                && cpu.registers().f == 0xFF, "DEC DE does not affect flags");
    }

    private static void accumulatorRotates() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0x07);
        cpu.registers().a = 0x80;
        cpu.registers().f = Z80.FLAG_S | Z80.FLAG_Z | Z80.FLAG_PV;
        check(cpu.step() == 4 && cpu.registers().a == 0x01
                && has(cpu, Z80.FLAG_C | Z80.FLAG_S | Z80.FLAG_Z | Z80.FLAG_PV)
                && !has(cpu, Z80.FLAG_H | Z80.FLAG_N), "RLCA");

        cpu = cpu(bus, 0x0F);
        cpu.registers().a = 0x01;
        cpu.registers().f = 0;
        check(cpu.step() == 4 && cpu.registers().a == 0x80
                && has(cpu, Z80.FLAG_C), "RRCA");

        cpu = cpu(bus, 0x17);
        cpu.registers().a = 0x80;
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 4 && cpu.registers().a == 0x01
                && has(cpu, Z80.FLAG_C), "RLA through carry");

        cpu = cpu(bus, 0x1F);
        cpu.registers().a = 0x01;
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 4 && cpu.registers().a == 0x80
                && has(cpu, Z80.FLAG_C), "RRA through carry");
    }

    private static void cbOperations() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0xCB, 0x00);
        cpu.registers().b = 0x80;
        check(cpu.step() == 8 && cpu.registers().b == 0x01
                && has(cpu, Z80.FLAG_C) && !has(cpu, Z80.FLAG_Z), "CB RLC B");

        cpu = cpu(bus, 0xCB, 0x09);
        cpu.registers().c = 0x01;
        check(cpu.step() == 8 && cpu.registers().c == 0x80
                && has(cpu, Z80.FLAG_C), "CB RRC C");

        cpu = cpu(bus, 0xCB, 0x10);
        cpu.registers().b = 0x80;
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 8 && cpu.registers().b == 0x01
                && has(cpu, Z80.FLAG_C), "CB RL B");

        cpu = cpu(bus, 0xCB, 0x10);
        cpu.registers().b = 0x80;
        cpu.registers().f = 0;
        cpu.step();
        check(cpu.registers().b == 0x00 && has(cpu, Z80.FLAG_C | Z80.FLAG_Z),
                "CB RL B zero");

        cpu = cpu(bus, 0xCB, 0x18);
        cpu.registers().b = 0x01;
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 8 && cpu.registers().b == 0x80
                && has(cpu, Z80.FLAG_C), "CB RR B");

        cpu = cpu(bus, 0xCB, 0x20);
        cpu.registers().b = 0x80;
        cpu.step();
        check(cpu.registers().b == 0x00 && has(cpu, Z80.FLAG_C | Z80.FLAG_Z),
                "CB SLA B");

        cpu = cpu(bus, 0xCB, 0x28);
        cpu.registers().b = 0x81;
        cpu.step();
        check(cpu.registers().b == 0xC0
                && has(cpu, Z80.FLAG_C | Z80.FLAG_S), "CB SRA B");

        cpu = cpu(bus, 0xCB, 0x30);
        cpu.registers().b = 0x80;
        cpu.step();
        check(cpu.registers().b == 0x01 && has(cpu, Z80.FLAG_C), "CB SLL B");

        cpu = cpu(bus, 0xCB, 0x38);
        cpu.registers().b = 0x01;
        cpu.step();
        check(cpu.registers().b == 0x00 && has(cpu, Z80.FLAG_C | Z80.FLAG_Z),
                "CB SRL B");

        cpu = cpu(bus, 0xCB, 0x40);
        cpu.registers().b = 0x01;
        cpu.step();
        check(!has(cpu, Z80.FLAG_Z | Z80.FLAG_PV) && has(cpu, Z80.FLAG_H)
                && !has(cpu, Z80.FLAG_N), "CB BIT 0,B");

        cpu = cpu(bus, 0xCB, 0x78);
        cpu.registers().b = 0x80;
        cpu.step();
        check(!has(cpu, Z80.FLAG_Z) && has(cpu, Z80.FLAG_S | Z80.FLAG_H),
                "CB BIT 7,B");

        cpu = cpu(bus, 0xCB, 0x70);
        cpu.registers().b = 0x00;
        cpu.step();
        check(has(cpu, Z80.FLAG_Z | Z80.FLAG_PV | Z80.FLAG_H)
                && !has(cpu, Z80.FLAG_S | Z80.FLAG_N), "CB BIT 6,B zero");

        cpu = cpu(bus, 0xCB, 0x80);
        cpu.registers().b = 0xFF;
        check(cpu.step() == 8 && cpu.registers().b == 0xFE, "CB RES 0,B");

        cpu = cpu(bus, 0xCB, 0xC0);
        cpu.registers().b = 0x00;
        check(cpu.step() == 8 && cpu.registers().b == 0x01, "CB SET 0,B");

        cpu = cpu(bus, 0xCB, 0x06);
        cpu.registers().setHl(0x2000);
        bus.write(0x2000, 0x80);
        check(cpu.step() == 15 && bus.read(0x2000) == 0x01
                && has(cpu, Z80.FLAG_C), "CB RLC (HL)");

        cpu = cpu(bus, 0xCB, 0x7E);
        cpu.registers().setHl(0x2000);
        bus.write(0x2000, 0x80);
        check(cpu.step() == 12 && bus.read(0x2000) == 0x80
                && has(cpu, Z80.FLAG_S) && !has(cpu, Z80.FLAG_Z),
                "CB BIT 7,(HL)");

        cpu = cpu(bus, 0xCB, 0x86);
        cpu.registers().setHl(0x2000);
        bus.write(0x2000, 0xFF);
        check(cpu.step() == 15 && bus.read(0x2000) == 0xFE, "CB RES 0,(HL)");

        cpu = cpu(bus, 0xCB, 0xC6);
        cpu.registers().setHl(0x2000);
        bus.write(0x2000, 0x00);
        check(cpu.step() == 15 && bus.read(0x2000) == 0x01, "CB SET 0,(HL)");
    }

    private static void controlFlow() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0xC3, 0x34, 0x12);
        check(cpu.step() == 10 && cpu.registers().pc == 0x1234,
                "JP nn");

        cpu = cpu(bus, 0xC2, 0x34, 0x12);
        cpu.registers().f = 0;
        check(cpu.step() == 10 && cpu.registers().pc == 0x1234,
                "JP NZ taken");

        cpu = cpu(bus, 0xC2, 0x34, 0x12);
        cpu.registers().f = Z80.FLAG_Z;
        check(cpu.step() == 10 && cpu.registers().pc == 3,
                "JP NZ not taken");

        cpu = cpu(bus, 0x18, 0x05);
        check(cpu.step() == 12 && cpu.registers().pc == 7, "JR displacement");

        cpu = cpu(bus, 0x20, 0x02);
        cpu.registers().f = 0;
        check(cpu.step() == 12 && cpu.registers().pc == 4, "JR NZ taken");

        cpu = cpu(bus, 0x20, 0x02);
        cpu.registers().f = Z80.FLAG_Z;
        check(cpu.step() == 7 && cpu.registers().pc == 2, "JR NZ not taken");

        cpu = cpu(bus, 0x06, 0x02, 0x10, 0xFE, 0x00);
        cpu.step();
        check(cpu.step() == 13 && cpu.registers().b == 1
                && cpu.registers().pc == 2, "DJNZ taken");
        check(cpu.step() == 8 && cpu.registers().b == 0
                && cpu.registers().pc == 4, "DJNZ not taken");

        cpu = cpu(bus, 0xCD, 0x00, 0x01);
        cpu.registers().sp = 0x8000;
        bus.load(0x0100, 0xC9);
        check(cpu.step() == 17 && cpu.registers().pc == 0x0100
                && cpu.registers().sp == 0x7FFE
                && bus.read16(0x7FFE) == 3, "CALL nn");
        check(cpu.step() == 10 && cpu.registers().pc == 3
                && cpu.registers().sp == 0x8000, "RET");

        cpu = cpu(bus, 0xC4, 0x00, 0x01);
        bus.load(0x0100, 0xC9);
        cpu.registers().sp = 0x8000;
        cpu.registers().f = 0;
        check(cpu.step() == 17 && cpu.registers().pc == 0x0100,
                "CALL NZ taken");
        cpu = cpu(bus, 0xC4, 0x00, 0x01);
        cpu.registers().sp = 0x8000;
        cpu.registers().f = Z80.FLAG_Z;
        check(cpu.step() == 10 && cpu.registers().pc == 3,
                "CALL NZ not taken");

        cpu = cpu(bus, 0xFF);
        cpu.registers().sp = 0x8000;
        check(cpu.step() == 11 && cpu.registers().pc == 0x0038
                && bus.read16(0x7FFE) == 1, "RST 38");

        cpu = cpu(bus, 0xC0);
        cpu.registers().sp = 0x8000;
        bus.write16(0x8000, 0x1234);
        cpu.registers().f = 0;
        check(cpu.step() == 11 && cpu.registers().pc == 0x1234
                && cpu.registers().sp == 0x8002, "RET NZ taken");

        cpu = cpu(bus, 0xE9);
        cpu.registers().setHl(0x1234);
        check(cpu.step() == 4 && cpu.registers().pc == 0x1234, "JP (HL)");
    }

    private static void indexedOperations() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0xDD, 0x21, 0x34, 0x12);
        check(cpu.step() == 14 && cpu.registers().ix == 0x1234,
                "LD IX,nn and prefix timing");

        cpu.registers().a = 0x00;
        bus.write(0x1239, 0xAB);
        cpu.registers().pc = 0;
        bus.load(0, 0xDD, 0x7E, 0x05);
        check(cpu.step() == 19 && cpu.registers().a == 0xAB,
                "LD A,(IX+d)");

        cpu.registers().a = 0xCD;
        cpu.registers().pc = 0;
        bus.load(0, 0xDD, 0x77, 0x05);
        check(cpu.step() == 19 && bus.read(0x1239) == 0xCD,
                "LD (IX+d),A");

        cpu.registers().pc = 0;
        bus.load(0, 0xDD, 0x36, 0x05, 0x7F);
        check(cpu.step() == 19 && bus.read(0x1239) == 0x7F,
                "LD (IX+d),n");

        cpu.registers().a = 0x01;
        bus.write(0x1239, 0x7F);
        cpu.registers().pc = 0;
        bus.load(0, 0xDD, 0x86, 0x05);
        check(cpu.step() == 19 && cpu.registers().a == 0x80
                && has(cpu, Z80.FLAG_S), "ADD A,(IX+d)");

        cpu.registers().ix = 0x0000;
        cpu.registers().pc = 0;
        bus.load(0, 0xDD, 0x26, 0x12, 0xDD, 0x2E, 0x34);
        cpu.step();
        cpu.step();
        check(cpu.registers().ix == 0x1234, "LD IXH/IXL");

        cpu.registers().pc = 0;
        bus.load(0, 0xDD, 0x24, 0xDD, 0x25);
        cpu.step();
        check(cpu.registers().ix == 0x1334, "INC IXH");
        cpu.step();
        check(cpu.registers().ix == 0x1234, "DEC IXH restores high byte");

        cpu.registers().iy = 0x1240;
        cpu.registers().pc = 0;
        bus.write(0x123E, 0x5A);
        bus.load(0, 0xFD, 0x7E, 0xFE);
        check(cpu.step() == 19 && cpu.registers().a == 0x5A,
                "LD A,(IY-d) negative displacement");

        cpu.registers().ix = 0x1234;
        cpu.registers().h = 0x00;
        bus.write(0x1239, 0x77);
        cpu.registers().pc = 0;
        bus.load(0, 0xDD, 0x66, 0x05);
        check(cpu.step() == 19 && cpu.registers().h == 0x77
                && ((cpu.registers().ix >> 8) & 0xFF) == 0x12,
                "LD H,(IX+d) writes ordinary H");

        cpu.registers().h = 0x5A;
        cpu.registers().pc = 0;
        bus.load(0, 0xDD, 0x74, 0x05);
        check(cpu.step() == 19 && bus.read(0x1239) == 0x5A,
                "LD (IX+d),H reads ordinary H");
    }

    private static void ddcbOperations() {
        TestBus bus = new TestBus();
        Z80 cpu = new Z80(bus);
        cpu.registers().ix = 0x1234;
        bus.write(0x1239, 0x80);
        bus.load(0, 0xDD, 0xCB, 0x05, 0x06);
        check(cpu.step() == 23 && bus.read(0x1239) == 0x01
                && has(cpu, Z80.FLAG_C), "DDCB RLC (IX+d)");
        check(cpu.registers().r == 2, "DDCB increments R by two M1 cycles");

        cpu = new Z80(bus);
        cpu.registers().ix = 0x1234;
        bus.write(0x1239, 0x80);
        bus.load(0, 0xDD, 0xCB, 0x05, 0x00);
        check(cpu.step() == 23 && bus.read(0x1239) == 0x01
                && cpu.registers().b == 0x01, "DDCB RLC writes memory and register");

        cpu = new Z80(bus);
        cpu.registers().ix = 0x1234;
        bus.write(0x1239, 0x01);
        bus.load(0, 0xDD, 0xCB, 0x05, 0x46);
        check(cpu.step() == 20 && bus.read(0x1239) == 0x01
                && !has(cpu, Z80.FLAG_Z) && has(cpu, Z80.FLAG_H),
                "DDCB BIT (IX+d)");

        cpu = new Z80(bus);
        cpu.registers().ix = 0x1234;
        bus.write(0x1239, 0xFF);
        bus.load(0, 0xDD, 0xCB, 0x05, 0x86);
        check(cpu.step() == 23 && bus.read(0x1239) == 0xFE,
                "DDCB RES (IX+d)");

        cpu = new Z80(bus);
        cpu.registers().ix = 0x1234;
        bus.write(0x1239, 0x00);
        bus.load(0, 0xDD, 0xCB, 0x05, 0xC6);
        check(cpu.step() == 23 && bus.read(0x1239) == 0x01,
                "DDCB SET (IX+d)");
    }

    private static void edOperations() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0xED, 0x43, 0x00, 0x20);
        cpu.registers().setBc(0x1234);
        check(cpu.step() == 20 && bus.read(0x2000) == 0x34
                && bus.read(0x2001) == 0x12, "ED LD (nn),BC");

        cpu = cpu(bus, 0xED, 0x4B, 0x00, 0x20);
        bus.write(0x2000, 0x34);
        bus.write(0x2001, 0x12);
        check(cpu.step() == 20 && cpu.registers().bc() == 0x1234,
                "ED LD BC,(nn)");

        cpu = cpu(bus, 0xED, 0x44);
        cpu.registers().a = 0x01;
        check(cpu.step() == 8 && cpu.registers().a == 0xFF
                && has(cpu, Z80.FLAG_N | Z80.FLAG_H | Z80.FLAG_S | Z80.FLAG_C),
                "ED NEG");

        cpu = cpu(bus, 0xED, 0x67);
        cpu.registers().a = 0x12;
        cpu.registers().setHl(0x2000);
        bus.write(0x2000, 0x34);
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 18 && cpu.registers().a == 0x14
                && bus.read(0x2000) == 0x23 && has(cpu, Z80.FLAG_C),
                "ED RRD");

        cpu = cpu(bus, 0xED, 0x6F);
        cpu.registers().a = 0x12;
        cpu.registers().setHl(0x2000);
        bus.write(0x2000, 0x34);
        check(cpu.step() == 18 && cpu.registers().a == 0x13
                && bus.read(0x2000) == 0x42, "ED RLD");

        cpu = cpu(bus, 0xED, 0x40);
        cpu.registers().setBc(0x1234);
        cpu.registers().f = Z80.FLAG_C;
        bus.inValue = 0x5A;
        check(cpu.step() == 12 && cpu.registers().b == 0x5A
                && bus.lastInPort == 0x1234 && has(cpu, Z80.FLAG_C)
                && !has(cpu, Z80.FLAG_H | Z80.FLAG_N), "ED IN B,(C)");

        cpu = cpu(bus, 0xED, 0x41);
        cpu.registers().setBc(0x5A34);
        check(cpu.step() == 12 && bus.lastOutPort == 0x5A34
                && bus.lastOutValue == 0x5A, "ED OUT (C),B");

        cpu = cpu(bus, 0xED, 0x47);
        cpu.registers().a = 0x12;
        check(cpu.step() == 9 && cpu.registers().i == 0x12, "ED LD I,A");

        cpu = cpu(bus, 0xED, 0x4F);
        cpu.registers().a = 0x80;
        check(cpu.step() == 9 && cpu.registers().r == 0x80, "ED LD R,A");

        cpu = cpu(bus, 0xED, 0x57);
        cpu.registers().i = 0x5A;
        cpu.registers().iff2 = true;
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 9 && cpu.registers().a == 0x5A
                && has(cpu, Z80.FLAG_PV | Z80.FLAG_C), "ED LD A,I");

        cpu = cpu(bus, 0xED, 0x5E);
        check(cpu.step() == 8 && cpu.im() == 2, "ED IM 2");
        cpu = cpu(bus, 0xED, 0x56);
        check(cpu.step() == 8 && cpu.im() == 1, "ED IM 1");
        cpu = cpu(bus, 0xED, 0x46);
        check(cpu.step() == 8 && cpu.im() == 0, "ED IM 0");

        cpu = cpu(bus, 0xED, 0x45);
        cpu.registers().sp = 0x8000;
        bus.write16(0x8000, 0x1234);
        cpu.registers().iff2 = true;
        cpu.registers().iff1 = false;
        check(cpu.step() == 14 && cpu.registers().pc == 0x1234
                && cpu.iff1(), "ED RETN restores IFF1");
    }

    private static void blockOperations() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0xED, 0xA0);
        cpu.registers().setHl(0x2000);
        cpu.registers().setDe(0x3000);
        cpu.registers().setBc(2);
        bus.write(0x2000, 0x5A);
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 16 && bus.read(0x3000) == 0x5A
                && cpu.registers().hl() == 0x2001
                && cpu.registers().de() == 0x3001
                && cpu.registers().bc() == 1
                && has(cpu, Z80.FLAG_PV | Z80.FLAG_C)
                && !has(cpu, Z80.FLAG_H | Z80.FLAG_N), "ED LDI");

        cpu = cpu(bus, 0xED, 0xB0);
        cpu.registers().setHl(0x2000);
        cpu.registers().setDe(0x3000);
        cpu.registers().setBc(2);
        bus.write(0x2000, 0x11);
        bus.write(0x2001, 0x22);
        check(cpu.step() == 21 && cpu.registers().pc == 0
                && cpu.registers().bc() == 1
                && bus.read(0x3000) == 0x11, "ED LDIR repeats");
        check(cpu.step() == 16 && cpu.registers().pc == 2
                && cpu.registers().bc() == 0
                && bus.read(0x3001) == 0x22
                && !has(cpu, Z80.FLAG_PV), "ED LDIR completes");

        cpu = cpu(bus, 0xED, 0xA1);
        cpu.registers().a = 0x10;
        cpu.registers().setHl(0x2000);
        cpu.registers().setBc(2);
        bus.write(0x2000, 0x10);
        cpu.registers().f = Z80.FLAG_C;
        check(cpu.step() == 16 && cpu.registers().hl() == 0x2001
                && cpu.registers().bc() == 1
                && has(cpu, Z80.FLAG_Z | Z80.FLAG_N | Z80.FLAG_PV | Z80.FLAG_C)
                && !has(cpu, Z80.FLAG_H), "ED CPI");

        cpu = cpu(bus, 0xED, 0xB1);
        cpu.registers().a = 0x10;
        cpu.registers().setHl(0x2000);
        cpu.registers().setBc(3);
        bus.write(0x2000, 0x00);
        bus.write(0x2001, 0x10);
        check(cpu.step() == 21 && cpu.registers().pc == 0
                && cpu.registers().bc() == 2, "ED CPIR repeats");
        check(cpu.step() == 16 && cpu.registers().pc == 2
                && cpu.registers().bc() == 1 && has(cpu, Z80.FLAG_Z),
                "ED CPIR stops on match");

        cpu = cpu(bus, 0xED, 0xA2);
        cpu.registers().b = 2;
        cpu.registers().c = 0x10;
        cpu.registers().setHl(0x2000);
        bus.inValue = 0x5A;
        check(cpu.step() == 16 && bus.read(0x2000) == 0x5A
                && cpu.registers().b == 1 && cpu.registers().hl() == 0x2001
                && has(cpu, Z80.FLAG_N), "ED INI");

        cpu = cpu(bus, 0xED, 0xA3);
        cpu.registers().b = 2;
        cpu.registers().c = 0x20;
        cpu.registers().setHl(0x2000);
        bus.write(0x2000, 0x5A);
        check(cpu.step() == 16 && bus.lastOutPort == 0x0120
                && bus.lastOutValue == 0x5A && cpu.registers().b == 1
                && cpu.registers().hl() == 0x2001, "ED OUTI");
    }

    private static void interrupts() {
        TestBus bus = new TestBus();
        Z80 cpu = new Z80(bus);
        cpu.registers().pc = 0x1234;
        cpu.registers().sp = 0x8000;
        cpu.registers().iff1 = true;
        cpu.registers().iff2 = false;
        cpu.registers().im = 1;
        cpu.interrupt(ru.viise.jemuxg.api.hardware.InterruptType.IRQ);
        check(cpu.step() == 13 && cpu.registers().pc == 0x0038
                && cpu.registers().sp == 0x7FFE
                && bus.read16(0x7FFE) == 0x1234
                && !cpu.iff1() && cpu.registers().iff2,
                "IM 1 IRQ enters 0038");

        cpu = new Z80(bus);
        cpu.registers().pc = 0x1234;
        cpu.registers().sp = 0x8000;
        cpu.registers().i = 0x20;
        cpu.registers().iff1 = true;
        cpu.registers().im = 2;
        cpu.setIrqData(0x10);
        bus.write(0x2010, 0x67);
        bus.write(0x2011, 0x45);
        cpu.interrupt(ru.viise.jemuxg.api.hardware.InterruptType.IRQ);
        check(cpu.step() == 19 && cpu.registers().pc == 0x4567
                && bus.read16(0x7FFE) == 0x1234,
                "IM 2 IRQ reads vector");

        cpu = new Z80(bus);
        cpu.registers().pc = 0x1234;
        cpu.registers().sp = 0x8000;
        cpu.registers().iff1 = true;
        cpu.registers().iff2 = false;
        cpu.registers().im = 1;
        cpu.interrupt(ru.viise.jemuxg.api.hardware.InterruptType.NMI);
        check(cpu.step() == 11 && cpu.registers().pc == 0x0066
                && bus.read16(0x7FFE) == 0x1234
                && !cpu.iff1() && cpu.registers().iff2,
                "NMI enters 0066");

        cpu = new Z80(bus);
        cpu.registers().pc = 0x1000;
        cpu.registers().sp = 0x8000;
        cpu.registers().iff1 = true;
        cpu.registers().im = 1;
        cpu.interrupt(ru.viise.jemuxg.api.hardware.InterruptType.IRQ);
        cpu.clearInterrupt(ru.viise.jemuxg.api.hardware.InterruptType.IRQ);
        bus.load(0x1000, 0x00);
        check(cpu.step() == 4 && cpu.registers().pc == 0x1001,
                "clearInterrupt(IRQ) cancels request");
    }

    private static void haltAndEiDelay() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0xFB, 0x00, 0x00);
        cpu.registers().iff1 = false;
        cpu.registers().iff2 = false;
        cpu.registers().im = 1;
        check(cpu.step() == 4 && cpu.iff1(), "EI enables interrupts");
        cpu.interrupt(ru.viise.jemuxg.api.hardware.InterruptType.IRQ);
        check(cpu.step() == 4 && cpu.registers().pc == 2 && cpu.iff1(),
                "EI delays IRQ by one instruction");
        check(cpu.step() == 13 && cpu.registers().pc == 0x0038,
                "IRQ accepted after EI delay");

        cpu = cpu(bus, 0x76, 0x00);
        cpu.registers().iff1 = true;
        cpu.registers().im = 1;
        check(cpu.step() == 4 && cpu.halted(), "HALT stops the CPU");
        check(cpu.step() == 4 && cpu.halted()
                && cpu.registers().pc == 1, "HALT keeps executing NOP-like cycles");
        cpu.interrupt(ru.viise.jemuxg.api.hardware.InterruptType.IRQ);
        check(cpu.step() == 13 && !cpu.halted()
                && cpu.registers().pc == 0x0038, "IRQ wakes HALT");

        cpu = cpu(bus, 0x76, 0x00);
        cpu.registers().iff1 = true;
        cpu.step();
        cpu.interrupt(ru.viise.jemuxg.api.hardware.InterruptType.NMI);
        check(cpu.step() == 11 && !cpu.halted()
                && cpu.registers().pc == 0x0066, "NMI wakes HALT");
    }

    private static void cyclesAndRefresh() {
        TestBus bus = new TestBus();
        Z80 cpu = cpu(bus, 0x00, 0x00);
        check(cpu.step() == 4 && cpu.registers().r == 1, "NOP cycle count and R");
        check(cpu.step() == 4 && cpu.registers().r == 2, "R increments per M1");

        cpu = cpu(bus, 0x01, 0x00, 0x00);
        check(cpu.step() == 10 && cpu.registers().r == 1, "LD BC,nn timing/R");

        cpu = cpu(bus, 0xDD, 0x21, 0x00, 0x00);
        check(cpu.step() == 14 && cpu.registers().r == 2,
                "DD prefix timing and R");

        cpu = cpu(bus, 0xED, 0x44);
        check(cpu.step() == 8 && cpu.registers().r == 2, "ED prefix timing and R");

        cpu = cpu(bus, 0xDD, 0xC3, 0x00, 0x10);
        check(cpu.step() == 14 && cpu.registers().pc == 0x1000,
                "ignored DD prefix still costs four cycles");

        cpu = cpu(bus, 0xDD, 0xC6, 0x01);
        cpu.registers().a = 1;
        check(cpu.step() == 11 && cpu.registers().a == 2,
                "ignored DD prefix before ALU immediate");

        cpu = cpu(bus, 0xED, 0x4F, 0x00);
        cpu.registers().a = 0x80;
        cpu.step();
        check(cpu.registers().r == 0x80, "LD R,A stores all eight bits");
        cpu.step();
        check(cpu.registers().r == 0x81, "M1 preserves R bit 7");

        cpu = cpu(bus, 0xDD, 0x36, 0x05, 0x7F);
        cpu.registers().ix = 0x2000;
        check(cpu.step() == 19, "LD (IX+d),n cycles");

        cpu = cpu(bus, 0xCD, 0x00, 0x10);
        cpu.registers().sp = 0x8000;
        check(cpu.step() == 17, "CALL cycles");

        cpu = cpu(bus, 0xED, 0xB0);
        cpu.registers().setHl(0x2000);
        cpu.registers().setDe(0x3000);
        cpu.registers().setBc(1);
        check(cpu.step() == 16, "LDIR final cycle count");

        cpu = cpu(bus, 0x00, 0x00, 0x00);
        cpu.tick(10);
        check(!cpu.instructionComplete() && cpu.remainingCycles() == 2,
                "tick stops mid-instruction");
        cpu.tick(2);
        check(cpu.instructionComplete() && cpu.registers().pc == 3,
                "tick completes pending instruction");
    }

    private static void saveRestore() {
        TestBus bus = new TestBus();
        Z80 cpu = new Z80(bus);
        cpu.registers().setAf(0x1234);
        cpu.registers().setBc(0x5678);
        cpu.registers().setDe(0x9ABC);
        cpu.registers().setHl(0xDEF0);
        cpu.registers().setAf2(0x1111);
        cpu.registers().setBc2(0x2222);
        cpu.registers().setDe2(0x3333);
        cpu.registers().setHl2(0x4444);
        cpu.registers().ix = 0x5555;
        cpu.registers().iy = 0x6666;
        cpu.registers().sp = 0x7777;
        cpu.registers().pc = 0x8888;
        cpu.registers().i = 0x99;
        cpu.registers().r = 0xAA;
        cpu.registers().memptr = 0xBBCC;
        cpu.registers().iff1 = true;
        cpu.registers().iff2 = false;
        cpu.registers().im = 2;

        BinaryStateWriter writer = new BinaryStateWriter();
        cpu.save(writer);
        byte[] state = writer.toByteArray();

        cpu.registers().setAf(0);
        cpu.registers().setBc(0);
        cpu.registers().setDe(0);
        cpu.registers().setHl(0);
        cpu.registers().ix = 0;
        cpu.registers().iy = 0;
        cpu.registers().sp = 0;
        cpu.registers().pc = 0;
        cpu.registers().i = 0;
        cpu.registers().r = 0;
        cpu.registers().memptr = 0;
        cpu.registers().iff1 = false;
        cpu.registers().iff2 = true;
        cpu.registers().im = 0;

        cpu.restore(new BinaryStateReader(state));
        check(cpu.registers().af() == 0x1234
                && cpu.registers().bc() == 0x5678
                && cpu.registers().de() == 0x9ABC
                && cpu.registers().hl() == 0xDEF0, "save/restore main registers");
        check(cpu.registers().af2() == 0x1111
                && cpu.registers().bc2() == 0x2222
                && cpu.registers().de2() == 0x3333
                && cpu.registers().hl2() == 0x4444, "save/restore shadow registers");
        check(cpu.registers().ix == 0x5555 && cpu.registers().iy == 0x6666
                && cpu.registers().sp == 0x7777 && cpu.registers().pc == 0x8888,
                "save/restore index/SP/PC");
        check(cpu.registers().i == 0x99 && cpu.registers().r == 0xAA
                && cpu.registers().memptr == 0xBBCC, "save/restore I/R/MEMPTR");
        check(cpu.iff1() && !cpu.registers().iff2 && cpu.im() == 2,
                "save/restore interrupt state");

        TestBus stateBus = new TestBus();
        Z80 stateCpu = new Z80(stateBus);
        stateCpu.registers().pc = 0x1000;
        stateCpu.registers().sp = 0x8000;
        stateCpu.registers().iff1 = true;
        stateCpu.registers().im = 1;
        stateCpu.interrupt(ru.viise.jemuxg.api.hardware.InterruptType.IRQ);
        writer = new BinaryStateWriter();
        stateCpu.save(writer);
        state = writer.toByteArray();
        stateCpu.reset();
        stateCpu.restore(new BinaryStateReader(state));
        check(stateCpu.step() == 13 && stateCpu.registers().pc == 0x0038,
                "save/restore pending IRQ line");

        Z80 haltCpu = cpu(stateBus, 0x76, 0x00);
        haltCpu.registers().iff1 = true;
        haltCpu.step();
        writer = new BinaryStateWriter();
        haltCpu.save(writer);
        state = writer.toByteArray();
        haltCpu.reset();
        haltCpu.restore(new BinaryStateReader(state));
        check(haltCpu.halted(), "save/restore HALT state");
        haltCpu.interrupt(ru.viise.jemuxg.api.hardware.InterruptType.NMI);
        check(haltCpu.step() == 11 && !haltCpu.halted()
                && haltCpu.registers().pc == 0x0066, "restored HALT wakes on NMI");
    }

    private static Z80 cpu(TestBus bus, int... code) {
        bus.clear();
        bus.load(0, code);
        return new Z80(bus);
    }

    private static boolean has(Z80 cpu, int mask) {
        return (cpu.registers().f & mask) != 0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }

    private static final class TestBus implements Z80Bus {

        private final byte[] memory = new byte[0x10000];
        private int inValue = 0xFF;
        private int lastInPort = -1;
        private int lastOutPort = -1;
        private int lastOutValue = -1;

        @Override
        public int read(int address) {
            return memory[address & 0xFFFF] & 0xFF;
        }

        @Override
        public void write(int address, int value) {
            memory[address & 0xFFFF] = (byte) value;
        }

        @Override
        public int in(int port) {
            lastInPort = port & 0xFFFF;
            return inValue;
        }

        @Override
        public void out(int port, int value) {
            lastOutPort = port & 0xFFFF;
            lastOutValue = value & 0xFF;
        }

        void clear() {
            Arrays.fill(memory, (byte) 0);
            inValue = 0xFF;
            lastInPort = -1;
            lastOutPort = -1;
            lastOutValue = -1;
        }

        void load(int address, int... bytes) {
            for (int i = 0; i < bytes.length; i++) {
                memory[(address + i) & 0xFFFF] = (byte) bytes[i];
            }
        }

        int read16(int address) {
            return read(address) | (read(address + 1) << 8);
        }

        void write16(int address, int value) {
            write(address, value & 0xFF);
            write(address + 1, (value >> 8) & 0xFF);
        }
    }
}
