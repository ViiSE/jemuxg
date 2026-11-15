package ru.viise.jemuxg.console.nes;

import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Mirroring;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.nes.cartridge.NesCartridge;
import ru.viise.jemuxg.console.nes.cartridge.NesMapper;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Контрактные тесты мапперов NROM, MMC1 и MMC3.
 */
public final class MapperTests {

    private static int checks;

    private MapperTests() {
    }

    public static void main(String[] args) {
        nrom();
        nrom16K();
        mmc1();
        cnrom();
        mmc3();
        System.out.println("Mapper tests OK, checks=" + checks);
    }

    private static void nrom() {
        byte[] prg = new byte[32 * 1024];
        for (int i = 0; i < prg.length; i++) {
            prg[i] = (byte) (i / 0x4000);
        }
        byte[] chr = new byte[8 * 1024];
        for (int i = 0; i < chr.length; i++) {
            chr[i] = (byte) (0x10 + i / 0x1000);
        }

        NesCartridge cart = cartridge(0, 2, 1, true, prg, chr);
        check(read(cart, 0x8000) == 0x00, "NROM $8000 maps first 16K bank");
        check(read(cart, 0xC000) == 0x01, "NROM $C000 maps second 16K bank");
        check(read(cart, 0xFFFF) == 0x01, "NROM last PRG byte");
        check(cart.chrBus().read(0x0000) == 0x10, "NROM CHR first bank");
        check(cart.chrBus().read(0x1000) == 0x11, "NROM CHR second bank");
        check(cart.mapper().id() == 0, "NROM mapper id");
    }

    private static void nrom16K() {
        byte[] prg = new byte[16 * 1024];
        for (int i = 0; i < prg.length; i++) {
            prg[i] = (byte) 0x42;
        }
        byte[] chr = new byte[8 * 1024];
        NesCartridge cart = cartridge(0, 1, 1, false, prg, chr);
        check(read(cart, 0x8000) == 0x42, "NROM 16K at $8000");
        check(read(cart, 0xC000) == 0x42, "NROM 16K mirrored at $C000");
        check(cart.mapper().id() == 0, "NROM 16K mapper id");
    }

    private static void mmc1() {
        byte[] prg = new byte[8 * 16 * 1024];
        for (int i = 0; i < prg.length; i++) {
            prg[i] = (byte) (i / 0x4000);
        }
        byte[] chr = new byte[2 * 8 * 1024];
        for (int i = 0; i < chr.length; i++) {
            chr[i] = (byte) (0x20 + i / 0x1000);
        }

        NesCartridge cart = cartridge(1, 8, 2, true, prg, chr);
        check(cart.mapper().id() == 1, "MMC1 mapper id");

        // Control: vertical mirroring, PRG mode 3, CHR mode 1.
        mmc1Write(cart, 0x8000, 0x1E);
        check(cart.mirroring() == Mirroring.VERTICAL, "MMC1 control sets vertical mirroring");

        // PRG mode 3: $8000 switchable, $C000 fixed to last bank.
        mmc1Write(cart, 0xE000, 3);
        check(read(cart, 0x8000) == 3, "MMC1 PRG bank 3 at $8000");
        check(read(cart, 0xC000) == 7, "MMC1 fixed last PRG bank at $C000");

        // CHR mode 1: two independent 4 KiB banks.
        mmc1Write(cart, 0xA000, 1);
        mmc1Write(cart, 0xC000, 3);
        check(cart.chrBus().read(0x0000) == 0x21, "MMC1 CHR bank 1 at $0000");
        check(cart.chrBus().read(0x1000) == 0x23, "MMC1 CHR bank 3 at $1000");

        // PRG RAM is enabled by default, then disabled by PRG bank bit 4.
        cart.write(0x6000, 0x55);
        check(read(cart, 0x6000) == 0x55, "MMC1 PRG RAM writable");
        mmc1Write(cart, 0xE000, 0x13);
        check(read(cart, 0x6000) == 0xFF, "MMC1 PRG RAM disabled by bank bit 4");
        mmc1Write(cart, 0xE000, 0x03);
        check(read(cart, 0x6000) == 0x55, "MMC1 PRG RAM re-enabled without data loss");

        // Control horizontal mirroring and serial reset behavior.
        mmc1Write(cart, 0x8000, 0x1F);
        check(cart.mirroring() == Mirroring.HORIZONTAL, "MMC1 control sets horizontal mirroring");
        cart.write(0x8000, 0x80);
        mmc1Write(cart, 0x8000, 0x0F);
        check(cart.mirroring() == Mirroring.HORIZONTAL, "MMC1 serial reset keeps control usable");
    }

    private static void cnrom() {
        byte[] prg = new byte[2 * 16 * 1024];
        for (int i = 0; i < prg.length; i++) {
            prg[i] = (byte) (i / 0x4000);
        }
        byte[] chr = new byte[4 * 8 * 1024];
        for (int i = 0; i < chr.length; i++) {
            chr[i] = (byte) (0x40 + i / 0x2000);
        }

        NesCartridge cart = cartridge(3, 2, 4, true, prg, chr);
        check(cart.mapper().id() == 3, "CNROM mapper id");
        check(cart.mapper().name().equals("CNROM"), "CNROM mapper name");
        check(cart.mirroring() == Mirroring.VERTICAL, "CNROM header mirroring");
        check(read(cart, 0x8000) == 0x00, "CNROM PRG first 16K at $8000");
        check(read(cart, 0xC000) == 0x01, "CNROM PRG second 16K at $C000");
        check(cart.chrBus().read(0x0000) == 0x40, "CNROM initial CHR bank");

        cart.write(0x8000, 1);
        check(cart.chrBus().read(0x0000) == 0x41, "CNROM selects CHR bank 1");
        check(cart.chrBus().read(0x1FFF) == 0x41, "CNROM selected CHR bank spans 8K");

        cart.write(0x8000, 2);
        check(cart.chrBus().read(0x0000) == 0x42, "CNROM selects CHR bank 2");
        cart.write(0x8000, 3);
        check(cart.chrBus().read(0x0000) == 0x43, "CNROM selects CHR bank 3");
        cart.write(0x8000, 4);
        check(cart.chrBus().read(0x0000) == 0x40, "CNROM CHR bank wraps modulo bank count");
    }

    private static void mmc3() {
        byte[] prg = new byte[8 * 16 * 1024];
        for (int i = 0; i < prg.length; i++) {
            prg[i] = (byte) (i / 0x2000);
        }
        byte[] chr = new byte[16 * 1024];
        for (int i = 0; i < chr.length; i++) {
            chr[i] = (byte) (0x30 + i / 0x400);
        }

        NesCartridge cart = cartridge(4, 8, 2, true, prg, chr);
        NesMapper mapper = cart.mapper();
        check(mapper.id() == 4, "MMC3 mapper id");

        // Default PRG mapping: R6, R7, second-last, last.
        cart.write(0x8000, 0x06);
        cart.write(0x8001, 5);
        cart.write(0x8000, 0x07);
        cart.write(0x8001, 7);
        check(read(cart, 0x8000) == 5, "MMC3 R6 at $8000");
        check(read(cart, 0xA000) == 7, "MMC3 R7 at $A000");
        check(read(cart, 0xC000) == 14, "MMC3 second-last PRG bank at $C000");
        check(read(cart, 0xE000) == 15, "MMC3 last PRG bank at $E000");

        // Bank-select bit 6 swaps $8000 and $C000.
        cart.write(0x8000, 0x46);
        cart.write(0x8001, 5);
        check(read(cart, 0x8000) == 14, "MMC3 PRG swap puts second-last at $8000");
        check(read(cart, 0xC000) == 5, "MMC3 PRG swap puts R6 at $C000");

        // CHR mode 0: 2 KiB R0/R1 and four 1 KiB R2..R5 banks.
        cart.write(0x8000, 0x00);
        int[] values = {2, 4, 6, 7, 8, 9};
        for (int i = 0; i < values.length; i++) {
            cart.write(0x8000, i);
            cart.write(0x8001, values[i]);
        }
        check(cart.chrBus().read(0x0000) == 0x32, "MMC3 CHR mode 0 R0 first 1K");
        check(cart.chrBus().read(0x0400) == 0x33, "MMC3 CHR mode 0 R0 second 1K");
        check(cart.chrBus().read(0x0800) == 0x34, "MMC3 CHR mode 0 R1 first 1K");
        check(cart.chrBus().read(0x1000) == 0x36, "MMC3 CHR mode 0 R2 at $1000");
        check(cart.chrBus().read(0x1C00) == 0x39, "MMC3 CHR mode 0 R5 at $1C00");

        // CHR mode 1: R2..R5 at $0000..$0C00, R0/R1 at $1000/$1800.
        for (int i = 0; i < values.length; i++) {
            cart.write(0x8000, 0x80 | i);
            cart.write(0x8001, values[i]);
        }
        check(cart.chrBus().read(0x0000) == 0x36, "MMC3 CHR mode 1 R2 at $0000");
        check(cart.chrBus().read(0x0C00) == 0x39, "MMC3 CHR mode 1 R5 at $0C00");
        check(cart.chrBus().read(0x1000) == 0x32, "MMC3 CHR mode 1 R0 first 1K");
        check(cart.chrBus().read(0x1800) == 0x34, "MMC3 CHR mode 1 R1 first 1K");

        // Mirroring register.
        cart.write(0xA000, 0);
        check(cart.mirroring() == Mirroring.VERTICAL, "MMC3 mirroring vertical");
        cart.write(0xA000, 1);
        check(cart.mirroring() == Mirroring.HORIZONTAL, "MMC3 mirroring horizontal");

        // PRG RAM protect: bit 7 enable, bit 6 write-protect.
        check(read(cart, 0x6000) == 0xFF, "MMC3 PRG RAM disabled by default");
        cart.write(0xA001, 0x80);
        cart.write(0x6000, 0x12);
        check(read(cart, 0x6000) == 0x12, "MMC3 PRG RAM enabled and writable");
        cart.write(0xA001, 0xC0);
        cart.write(0x6000, 0x34);
        check(read(cart, 0x6000) == 0x12, "MMC3 PRG RAM write-protected");

        // IRQ: reload on $C001, decrement per A12 clock, fire at zero.
        cart.write(0xC000, 3);
        cart.write(0xC001, 0);
        cart.write(0xE001, 0);
        mapper.clockIrq();
        check(!mapper.irqPending(), "MMC3 IRQ reload does not fire");
        mapper.clockIrq();
        mapper.clockIrq();
        check(!mapper.irqPending(), "MMC3 IRQ not pending before counter reaches zero");
        mapper.clockIrq();
        check(mapper.irqPending(), "MMC3 IRQ pending at zero");
        mapper.clearIrq();
        check(!mapper.irqPending(), "MMC3 IRQ acknowledge clears pending");

        cart.write(0xE001, 0);
        mapper.clockIrq();
        mapper.clockIrq();
        mapper.clockIrq();
        mapper.clockIrq();
        check(mapper.irqPending(), "MMC3 IRQ re-arms after acknowledge");
    }

    private static void mmc1Write(Bus cart, int address, int value) {
        for (int bit = 0; bit < 5; bit++) {
            cart.write(address, (value >> bit) & 0x01);
        }
    }

    private static NesCartridge cartridge(
            int mapper,
            int prg16k,
            int chr8k,
            boolean vertical,
            byte[] prg,
            byte[] chr) {
        byte[] data = new byte[16 + prg.length + chr.length];
        data[0] = 'N';
        data[1] = 'E';
        data[2] = 'S';
        data[3] = 0x1A;
        data[4] = (byte) prg16k;
        data[5] = (byte) chr8k;
        data[6] = (byte) (((mapper & 0x0F) << 4) | (vertical ? 1 : 0));
        data[7] = (byte) (mapper & 0xF0);
        System.arraycopy(prg, 0, data, 16, prg.length);
        System.arraycopy(chr, 0, data, 16 + prg.length, chr.length);
        return new NesCartridge(new ByteArrayMedia("mapper.nes", MediaKind.CARTRIDGE, data));
    }

    private static int read(Bus bus, int address) {
        return bus.read(address) & 0xFF;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
