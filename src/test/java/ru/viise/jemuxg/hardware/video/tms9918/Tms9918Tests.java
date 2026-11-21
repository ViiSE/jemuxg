package ru.viise.jemuxg.hardware.video.tms9918;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.console.sg1000.Sg1000Bus;
import ru.viise.jemuxg.console.sg1000.Sg1000Constants;
import ru.viise.jemuxg.console.sg1000.Sg1000Machine;
import ru.viise.jemuxg.console.sg1000.cartridge.Sg1000CartridgeSlot;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;

/**
 * Проверки TMS9918A: порты, режимы, спрайты, IRQ и save/restore.
 */
public final class Tms9918Tests {

    private static int checks;

    private Tms9918Tests() {
    }

    public static void main(String[] args) {
        palette();
        portsAndVram();
        statusAndIrq();
        graphicsI();
        graphicsII();
        multicolor();
        text();
        sprites();
        busRouting();
        machineIntegration();
        saveRestore();
        System.out.println("TMS9918 tests OK, checks=" + checks);
    }

    private static void palette() {
        check(Tms9918Palette.size() == 16, "TMS9918 palette has 16 colors");
        check(Tms9918Palette.argb(0) == 0xFF000000,
                "transparent palette entry renders black");
        check(Tms9918Palette.argb(15) == 0xFFFFFFFF,
                "palette entry 15 is white");
    }

    private static void portsAndVram() {
        Tms9918 vdp = new Tms9918();
        check(vdp.register(0) == 0 && vdp.register(7) == 0,
                "registers start cleared");
        check(vdp.status() == 0 && vdp.frame() == 0,
                "status and frame start cleared");

        vdp.writeControl(0x00);
        vdp.writeControl(0x40);
        vdp.writeData(0x12);
        vdp.writeData(0x34);
        check(vdp.vramValue(0) == 0x12 && vdp.vramValue(1) == 0x34,
                "data writes go through the VRAM address latch");
        check(vdp.currentAddress() == 2,
                "VRAM address increments after writes");

        vdp.writeControl(0x00);
        vdp.writeControl(0x00);
        check(vdp.readData() == 0x12 && vdp.readData() == 0x34,
                "read-ahead buffer returns sequential VRAM data");

        vdp.writeControl(0xE2);
        vdp.writeControl(0x87);
        check(vdp.register(7) == 0xE2, "control port writes registers");
        check(vdp.currentAddress() == 3,
                "register write does not change the VRAM address");

        vdp.setVram(0x3FFF, 0x5A);
        vdp.writeControl(0xFF);
        vdp.writeControl(0x3F);
        check(vdp.readData() == 0x5A, "VRAM wraps at 16 KB");
    }

    private static void statusAndIrq() {
        Tms9918 vdp = new Tms9918();
        vdp.setRegister(1, 0x60);
        vdp.beginFrame();
        vdp.beginLine(191);
        check(!vdp.irq() && (vdp.status() & Tms9918.STATUS_INT) == 0,
                "VBlank starts on line 192");
        vdp.beginLine(192);
        check(vdp.irq(), "VBlank requests an IRQ when IE is set");
        check((vdp.status() & Tms9918.STATUS_INT) != 0,
                "VBlank sets status bit 7");
        check(vdp.readStatus() == Tms9918.STATUS_INT,
                "status read returns the VBlank flag");
        check(!vdp.irq() && vdp.status() == 0,
                "status read clears flags and IRQ");

        vdp.setRegister(1, 0x40);
        vdp.beginLine(192);
        check(!vdp.irq() && (vdp.status() & Tms9918.STATUS_INT) != 0,
                "VBlank flag is set without IE, but no IRQ is requested");
        vdp.readStatus();
    }

    private static void graphicsI() {
        Tms9918 vdp = graphicsIBase();
        vdp.setVram(0x1000, 1);
        vdp.setVram(0x808, 0x81);
        vdp.setVram(0x80, 0x4F);
        vdp.renderLine(0);
        byte[] pixels = vdp.framebufferIndices();
        check(pixels[0] == 4 && pixels[1] == 15 && pixels[6] == 15
                && pixels[7] == 4, "Graphics I renders pattern bits and colors");

        vdp.setVram(0x80, 0x04);
        vdp.renderLine(0);
        check(pixels[0] == 1 && pixels[1] == 4,
                "color 0 uses the backdrop color in Graphics I");
    }

    private static Tms9918 graphicsIBase() {
        Tms9918 vdp = new Tms9918();
        vdp.setRegister(1, 0x40);
        vdp.setRegister(2, 0x04);
        vdp.setRegister(3, 0x02);
        vdp.setRegister(4, 0x01);
        vdp.setRegister(7, 0x01);
        return vdp;
    }

    private static void graphicsII() {
        Tms9918 vdp = new Tms9918();
        vdp.setRegister(0, 0x02);
        vdp.setRegister(1, 0x40);
        vdp.setRegister(2, 0x04);
        vdp.setRegister(3, 0x00);
        vdp.setRegister(4, 0x04);
        vdp.setRegister(7, 0x01);
        check(vdp.mode() == Tms9918.Mode.GRAPHICS_II,
                "M3 selects Graphics II");

        vdp.setVram(0x1000, 1);
        vdp.setVram(0x2008, 0x80);
        vdp.setVram(0x0008, 0x3C);
        vdp.renderLine(0);
        byte[] pixels = vdp.framebufferIndices();
        check(pixels[0] == 3 && pixels[1] == 12,
                "Graphics II renders pattern and per-row color");

        vdp.setRegister(4, 0x05);
        vdp.setVram(0x1100, 1);
        vdp.setVram(0x2808, 0x80);
        vdp.renderLine(64);
        check(vdp.framebufferIndices()[64 * Tms9918.WIDTH] == 3,
                "Graphics II selects the pattern bank from the name table");
    }

    private static void multicolor() {
        Tms9918 vdp = new Tms9918();
        vdp.setRegister(1, 0x48);
        vdp.setRegister(2, 0x04);
        vdp.setRegister(4, 0x01);
        vdp.setRegister(7, 0x01);
        check(vdp.mode() == Tms9918.Mode.MULTICOLOR,
                "M1 selects Multicolor");

        vdp.setVram(0x1000, 2);
        vdp.setVram(0x810, 0x4F);
        vdp.setVram(0x811, 0x2B);
        vdp.renderLine(0);
        byte[] pixels = vdp.framebufferIndices();
        check(pixels[0] == 4 && pixels[3] == 4
                && pixels[4] == 15 && pixels[7] == 15,
                "Multicolor renders two 4-pixel blocks per byte");

        vdp.renderLine(4);
        check(pixels[4 * Tms9918.WIDTH] == 2
                && pixels[4 * Tms9918.WIDTH + 4] == 11,
                "Multicolor selects the next pattern row every four lines");
    }

    private static void text() {
        Tms9918 vdp = new Tms9918();
        vdp.setRegister(1, 0x50);
        vdp.setRegister(2, 0x04);
        vdp.setRegister(4, 0x01);
        vdp.setRegister(7, 0x3A);
        check(vdp.mode() == Tms9918.Mode.TEXT, "M2 selects Text");

        vdp.setVram(0x1000, 1);
        vdp.setVram(0x808, 0x80);
        vdp.renderLine(0);
        byte[] pixels = vdp.framebufferIndices();
        check(pixels[0] == 10 && pixels[7] == 10,
                "Text uses the R7 background color at the border");
        check(pixels[8] == 3 && pixels[9] == 10 && pixels[13] == 10,
                "Text renders a 6-pixel character with R7 foreground");
        check(pixels[14] == 10,
                "Text leaves the last two pixels of a character blank");
    }

    private static void sprites() {
        Tms9918 vdp = spriteBase();
        vdp.setVram(0x4000, 0x80);
        vdp.setVram(0x4001, 0x40);
        vdp.setVram(0x800, 0);
        vdp.setVram(0x801, 10);
        vdp.setVram(0x802, 0);
        vdp.setVram(0x803, 0x0F);
        vdp.renderLine(1);
        byte[] pixels = vdp.framebufferIndices();
        check(pixels[Tms9918.WIDTH + 10] == 15
                && pixels[Tms9918.WIDTH + 11] == 1,
                "sprite renders at Y+1 with transparent background");

        vdp.setVram(0x804, 0);
        vdp.setVram(0x805, 10);
        vdp.setVram(0x806, 0);
        vdp.setVram(0x807, 0x0A);
        vdp.renderLine(1);
        check((vdp.status() & Tms9918.STATUS_COL) != 0,
                "overlapping sprites set the collision flag");
        check(pixels[Tms9918.WIDTH + 10] == 15,
                "the first sprite keeps priority on collision");

        vdp = spriteBase();
        for (int i = 0; i < 5; i++) {
            vdp.setVram(0x800 + i * 4, 0);
            vdp.setVram(0x801 + i * 4, 10 + i * 10);
            vdp.setVram(0x802 + i * 4, 0);
            vdp.setVram(0x803 + i * 4, 0x0F);
        }
        vdp.setVram(0x4000, 0x80);
        vdp.renderLine(1);
        check((vdp.status() & Tms9918.STATUS_5S) != 0
                && (vdp.status() & 0x1F) == 4,
                "the fifth sprite sets the 5S flag and index");

        vdp = spriteBase();
        vdp.setVram(0x4000, 0x80);
        vdp.setVram(0x800, 0);
        vdp.setVram(0x801, 40);
        vdp.setVram(0x802, 0);
        vdp.setVram(0x803, 0x8F);
        vdp.renderLine(1);
        check(vdp.framebufferIndices()[Tms9918.WIDTH + 8] == 15,
                "early clock shifts a sprite 32 pixels left");

        vdp = spriteBase();
        vdp.setRegister(1, 0x42);
        vdp.setVram(0x4000, 0x80);
        vdp.setVram(0x4010, 0x80);
        vdp.setVram(0x800, 0);
        vdp.setVram(0x801, 10);
        vdp.setVram(0x802, 0);
        vdp.setVram(0x803, 0x0F);
        vdp.renderLine(1);
        check(vdp.framebufferIndices()[Tms9918.WIDTH + 10] == 15
                && vdp.framebufferIndices()[Tms9918.WIDTH + 18] == 15,
                "16x16 sprites use both pattern halves");

        vdp = spriteBase();
        vdp.setRegister(1, 0x43);
        vdp.setVram(0x4000, 0x80);
        vdp.setVram(0x800, 0);
        vdp.setVram(0x801, 10);
        vdp.setVram(0x802, 0);
        vdp.setVram(0x803, 0x0F);
        vdp.renderLine(1);
        check(vdp.framebufferIndices()[Tms9918.WIDTH + 10] == 15
                && vdp.framebufferIndices()[Tms9918.WIDTH + 11] == 15,
                "sprite magnification doubles each pixel");
    }

    private static Tms9918 spriteBase() {
        Tms9918 vdp = graphicsIBase();
        vdp.setRegister(5, 0x10);
        vdp.setRegister(6, 0x08);
        vdp.setVram(0x800, 0xD0);
        return vdp;
    }

    private static void busRouting() {
        Sg1000CartridgeSlot slot = new Sg1000CartridgeSlot();
        Tms9918 vdp = new Tms9918();
        Sg1000Bus bus = new Sg1000Bus(slot, vdp);

        bus.out(0xBF, 0x00);
        bus.out(0xBF, 0x40);
        bus.out(0xBE, 0x12);
        check(vdp.vramValue(0) == 0x12,
                "TMS9918 data writes are routed through the SG-1000 bus");
        bus.out(0x82, 0x34);
        check(vdp.vramValue(1) == 0x34,
                "mirrored data ports write the same VRAM");

        bus.out(0xBF, 0x00);
        bus.out(0xBF, 0x00);
        check(bus.in(0xBE) == 0x12,
                "TMS9918 data reads are routed through the SG-1000 bus");

        boolean[] cleared = {false};
        bus.setIrqClear(() -> cleared[0] = true);
        vdp.setRegister(1, 0x60);
        vdp.beginLine(192);
        check(bus.in(0xBF) == Tms9918.STATUS_INT,
                "status reads are routed through the SG-1000 bus");
        check(cleared[0] && !vdp.irq(),
                "reading status clears the CPU IRQ through the bus hook");
    }

    private static void machineIntegration() {
        Sg1000Machine machine = new Sg1000Machine();
        List<VideoFrame> frames = new ArrayList<>();
        machine.connectVideo(frames::add);
        machine.vdp().setRegister(1, 0x40);
        machine.vdp().setRegister(7, 0x05);

        machine.runFrame();
        check(frames.size() == 1, "SG-1000 emits exactly one video frame");
        VideoFrame frame = frames.get(0);
        check(frame.width() == Tms9918.WIDTH && frame.height() == Tms9918.HEIGHT,
                "SG-1000 frame has TMS9918 dimensions");
        check(frame.pixels()[0] == Tms9918Palette.argb(5),
                "SG-1000 frame contains the VDP backdrop color");
        check(machine.vdp().frame() == 1 && machine.frameCount() == 1,
                "SG-1000 advances VDP and machine frame counters");

        machine.vdp().setRegister(1, 0x60);
        machine.runFrame();
        check((machine.vdp().status() & Tms9918.STATUS_INT) != 0,
                "machine leaves the VBlank status flag set");
        check(machine.vdp().irq(),
                "machine raises the VDP IRQ line at line 192");
    }

    private static void saveRestore() {
        Tms9918 vdp = spriteBase();
        vdp.setRegister(1, 0x60);
        vdp.setVram(0x4000, 0x80);
        vdp.beginFrame();
        vdp.beginLine(192);
        vdp.renderLine(1);
        byte[] beforeFrame = vdp.framebufferIndices().clone();
        int[] beforeArgb = vdp.framebufferArgb().clone();

        BinaryStateWriter writer = new BinaryStateWriter();
        vdp.save(writer);
        byte[] state = writer.toByteArray();

        vdp.setVram(0, 0xAA);
        vdp.setRegister(7, 0x0F);
        vdp.readStatus();
        vdp.restore(new BinaryStateReader(state));

        check(vdp.vramValue(0) == 0x80 && vdp.register(7) == 0x01,
                "TMS9918 restores VRAM and registers");
        check(Arrays.equals(vdp.framebufferIndices(), beforeFrame)
                && Arrays.equals(vdp.framebufferArgb(), beforeArgb),
                "TMS9918 restores indexed and ARGB framebuffers");
        check(vdp.status() == Tms9918.STATUS_INT && vdp.irq(),
                "TMS9918 restores status and IRQ");
        check(vdp.line() == 192 && vdp.frame() == 0,
                "TMS9918 restores line and frame counters");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }
}

