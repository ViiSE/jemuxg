package ru.viise.jemuxg.console.sg1000.debug;

/**
 * Неизменяемый снимок регистров Z80 для отладочного дампа.
 *
 * <p>Поля повторяют {@link ru.viise.jemuxg.hardware.cpu.z80.Z80Registers},
 * но запись создаётся копией, поэтому эмуляция может продолжаться, пока
 * снимок пишется в файлы.</p>
 */
public record Z80RegisterSnapshot(
        int a,
        int f,
        int b,
        int c,
        int d,
        int e,
        int h,
        int l,
        int a2,
        int f2,
        int b2,
        int c2,
        int d2,
        int e2,
        int h2,
        int l2,
        int ix,
        int iy,
        int sp,
        int pc,
        int i,
        int r,
        int im,
        int memptr,
        boolean iff1,
        boolean iff2,
        boolean halted,
        int remainingCycles) {

    public int af() {
        return (a << 8) | f;
    }

    public int bc() {
        return (b << 8) | c;
    }

    public int de() {
        return (d << 8) | e;
    }

    public int hl() {
        return (h << 8) | l;
    }

    public int af2() {
        return (a2 << 8) | f2;
    }

    public int bc2() {
        return (b2 << 8) | c2;
    }

    public int de2() {
        return (d2 << 8) | e2;
    }

    public int hl2() {
        return (h2 << 8) | l2;
    }
}
