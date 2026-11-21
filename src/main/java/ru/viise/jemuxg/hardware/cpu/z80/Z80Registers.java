package ru.viise.jemuxg.hardware.cpu.z80;

/**
 * Регистровый файл Z80.
 *
 * <p>Поля намеренно открыты: ядро и отладочные тесты работают с ними
 * напрямую, как с аппаратными регистрами. Все 8-битные значения хранятся
 * в диапазоне {@code 0..255}, 16-битные — {@code 0..65535}.</p>
 */
public final class Z80Registers {

    public int a;
    public int f;
    public int b;
    public int c;
    public int d;
    public int e;
    public int h;
    public int l;

    public int a2;
    public int f2;
    public int b2;
    public int c2;
    public int d2;
    public int e2;
    public int h2;
    public int l2;

    public int ix;
    public int iy;
    public int sp;
    public int pc;
    public int i;
    public int r;

    public boolean iff1;
    public boolean iff2;
    public int im;

    /**
     * Внутренний адресный регистр Z80 (MEMPTR/WZ).
     */
    public int memptr;

    public void reset() {
        a = 0;
        f = 0;
        b = 0;
        c = 0;
        d = 0;
        e = 0;
        h = 0;
        l = 0;

        a2 = 0;
        f2 = 0;
        b2 = 0;
        c2 = 0;
        d2 = 0;
        e2 = 0;
        h2 = 0;
        l2 = 0;

        ix = 0;
        iy = 0;
        sp = 0xFFFF;
        pc = 0;
        i = 0;
        r = 0;

        iff1 = false;
        iff2 = false;
        im = 0;
        memptr = 0;
    }

    public int af() {
        return (a << 8) | f;
    }

    public void setAf(int value) {
        a = (value >> 8) & 0xFF;
        f = value & 0xFF;
    }

    public int bc() {
        return (b << 8) | c;
    }

    public void setBc(int value) {
        b = (value >> 8) & 0xFF;
        c = value & 0xFF;
    }

    public int de() {
        return (d << 8) | e;
    }

    public void setDe(int value) {
        d = (value >> 8) & 0xFF;
        e = value & 0xFF;
    }

    public int hl() {
        return (h << 8) | l;
    }

    public void setHl(int value) {
        h = (value >> 8) & 0xFF;
        l = value & 0xFF;
    }

    public int af2() {
        return (a2 << 8) | f2;
    }

    public void setAf2(int value) {
        a2 = (value >> 8) & 0xFF;
        f2 = value & 0xFF;
    }

    public int bc2() {
        return (b2 << 8) | c2;
    }

    public void setBc2(int value) {
        b2 = (value >> 8) & 0xFF;
        c2 = value & 0xFF;
    }

    public int de2() {
        return (d2 << 8) | e2;
    }

    public void setDe2(int value) {
        d2 = (value >> 8) & 0xFF;
        e2 = value & 0xFF;
    }

    public int hl2() {
        return (h2 << 8) | l2;
    }

    public void setHl2(int value) {
        h2 = (value >> 8) & 0xFF;
        l2 = value & 0xFF;
    }
}
