package ru.viise.jemuxg.console.nes;

import java.util.Arrays;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Mirroring;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.hardware.video.ricoh2c02.NesPalette;
import ru.viise.jemuxg.hardware.video.ricoh2c02.Ricoh2c02;

/**
 * Контрактные тесты PPU без сторонних библиотек.
 * <p>
 * Запуск: {@code java -cp target/classes:target/test-classes
 * ru.viise.jemuxg.console.nes.PpuTests}.
 */
public final class PpuTests {

    private static int checks;

    private PpuTests() {
    }

    public static void main(String[] args) {
        backgroundAndSpriteRendering();
        nametableSelection();
        verticalMirroring();
        nmiAndFrameDelivery();
        spriteZeroHit();
        opaqueBlackBackground();
        paletteTables();
        saveRestore();
        System.out.println("PPU tests OK, checks=" + checks);
    }

    private static void backgroundAndSpriteRendering() {
        byte[] chr = new byte[0x2000];
        Arrays.fill(chr, 0x00, 0x08, (byte) 0xFF);   // tile 0: color 1
        Arrays.fill(chr, 0x10, 0x18, (byte) 0xFF);   // tile 1: color 1, sprite palette

        Ricoh2c02 ppu = newPpu(chr);
        Bus registers = ppu.registers();
        fillNametable(registers, 0x2000, 0);
        writePalette(registers, 0x0F, 0x16, 0x00, 0x2A);

        writeOam(registers, 0, 1, 1, 0);
        registers.write(0, 0x00);
        registers.write(5, 0x00);
        registers.write(5, 0x00);
        registers.write(1, 0x1E);

        VideoFrame frame = renderFrame(ppu);
        check(frame.width() == 256 && frame.height() == 240, "frame is 256x240");
        check(frame.pixels()[10 * 256 + 10] == NesPalette.ARGB[0x16],
                "background pixel uses palette color 0x16");
        check(frame.pixels()[1 * 256] == NesPalette.ARGB[0x2A],
                "sprite pixel uses sprite palette color 0x2A");
    }

    private static void nametableSelection() {
        byte[] chr = new byte[0x2000];
        Arrays.fill(chr, 0x00, 0x08, (byte) 0xFF);   // tile 0: color 1
        Arrays.fill(chr, 0x18, 0x20, (byte) 0xFF);   // tile 1: color 2

        Ricoh2c02 ppu = newPpu(chr);
        Bus registers = ppu.registers();
        fillNametable(registers, 0x2000, 0);
        fillNametable(registers, 0x2400, 1);
        writePalette(registers, 0x0F, 0x16, 0x2A, 0x00);

        registers.write(5, 0x00);
        registers.write(5, 0x00);
        registers.write(1, 0x0A);
        registers.write(0, 0x00);
        VideoFrame nt0 = renderFrame(ppu);
        check(nt0.pixels()[0] == NesPalette.ARGB[0x16], "nametable 0 uses tile 0");

        registers.write(0, 0x01);
        VideoFrame nt1 = renderFrame(ppu);
        check(nt1.pixels()[0] == NesPalette.ARGB[0x2A], "nametable 1 uses tile 1");
    }

    private static void verticalMirroring() {
        Ricoh2c02 ppu = newPpu(new byte[0x2000]);
        ppu.setMirroring(Mirroring.VERTICAL);
        Bus registers = ppu.registers();

        writeVram(registers, 0x2000, 0x11);
        writeVram(registers, 0x2400, 0x22);
        writeVram(registers, 0x2C00, 0x33);

        check(readVram(registers, 0x2000) == 0x11, "vertical mirror: $2000 direct");
        check(readVram(registers, 0x2800) == 0x11, "vertical mirror: $2800 = $2000");
        check(readVram(registers, 0x2400) == 0x33, "vertical mirror: $2400 = $2C00");
        check(readVram(registers, 0x2C00) == 0x33, "vertical mirror: $2C00 direct");
    }

    private static void nmiAndFrameDelivery() {
        Ricoh2c02 ppu = newPpu(new byte[0x2000]);
        ppu.registers().write(0, 0x80);
        ppu.tick(Ricoh2c02.CYCLES_PER_FRAME);

        check(ppu.takeNmi(), "NMI requested after vblank");
        check(!ppu.takeNmi(), "NMI cleared after take");
        check(ppu.frameCount() == 1, "frame counter incremented");
    }

    private static void spriteZeroHit() {
        byte[] chr = new byte[0x2000];
        Arrays.fill(chr, 0x00, 0x08, (byte) 0xFF);
        Arrays.fill(chr, 0x10, 0x18, (byte) 0xFF);

        Ricoh2c02 ppu = newPpu(chr);
        Bus registers = ppu.registers();
        fillNametable(registers, 0x2000, 0);
        writePalette(registers, 0x0F, 0x16, 0x00, 0x2A);
        writeOam(registers, 0, 1, 1, 0);
        registers.write(0, 0x00);
        registers.write(5, 0x00);
        registers.write(5, 0x00);
        registers.write(1, 0x1E);

        ppu.tick(2L * Ricoh2c02.CYCLES_PER_SCANLINE + 10);
        check((ppu.status() & 0x40) != 0, "sprite 0 hit flag set");
        check((ppu.status() & 0x20) == 0, "sprite overflow not set");
    }

    private static void opaqueBlackBackground() {
        byte[] chr = new byte[0x2000];
        Arrays.fill(chr, 0x00, 0x08, (byte) 0xFF);   // tile 0: color 1
        Arrays.fill(chr, 0x10, 0x18, (byte) 0xFF);   // tile 1: color 1, sprite palette

        Ricoh2c02 ppu = newPpu(chr);
        Bus registers = ppu.registers();
        fillNametable(registers, 0x2000, 0);
        writePalette(registers, 0x0F, 0x00, 0x2A, 0x2A);
        registers.write(5, 0x00);
        registers.write(5, 0x00);
        registers.write(1, 0x0A);

        VideoFrame frame = renderFrame(ppu);
        int pixel = 10 * 256 + 10;
        check(frame.pixels()[pixel] == NesPalette.ARGB[0x00],
                "palette entry 0x00 is opaque black");
        check(ppu.framebufferIndices()[pixel] == 0,
                "opaque black stores palette index 0");

        writeOam(registers, 0, 1, 1, 0);
        registers.write(0, 0x00);
        registers.write(5, 0x00);
        registers.write(5, 0x00);
        registers.write(1, 0x1E);
        ppu.tick(2L * Ricoh2c02.CYCLES_PER_SCANLINE + 10);
        check((ppu.status() & 0x40) != 0,
                "sprite 0 hit on opaque black background");
    }

    private static void paletteTables() {
        check(NesPalette.ARGB[0x22] == 0xFF6888FC, "active palette is classic jemuxg");
        check(NesPalette.ARGB[0x27] == 0xFFFCA044, "active classic palette 0x27");
        check(NesPalette.MESEN_2C02_ARGB[0x22] == 0xFF9290FF, "Mesen palette 0x22");
        check(NesPalette.MESEN_2C02_ARGB[0x27] == 0xFFEA9E22, "Mesen palette 0x27");
        check(NesPalette.MESEN_2C02_ARGB[0x1C] == 0xFF007C8D, "Mesen palette 0x1C");
        check(NesPalette.MESEN_2C02_ARGB[0x3C] == 0xFFB5EBF2, "Mesen palette 0x3C");
    }

    private static void saveRestore() {
        Ricoh2c02 ppu = new Ricoh2c02();
        ppu.setMirroring(Mirroring.VERTICAL);
        Bus registers = ppu.registers();
        registers.write(0, 0x10);
        registers.write(3, 0x42);
        registers.write(5, 0x12);
        registers.write(5, 0x34);
        writeVram(registers, 0x2000, 0xAB);
        ppu.tick(1000);

        BinaryStateWriter writer = new BinaryStateWriter();
        ppu.save(writer);

        Ricoh2c02 restored = new Ricoh2c02();
        StateReader reader = new BinaryStateReader(writer.toByteArray());
        restored.restore(reader);

        check(restored.oamAddress() == 0x42, "restore OAM address");
        check(restored.status() == ppu.status(), "restore status");
        check(restored.frameCount() == ppu.frameCount(), "restore frame counter");
        check(readVram(restored.registers(), 0x2000) == 0xAB, "restore VRAM");
        restored.tick(1000);
        check(restored.frameCount() == ppu.frameCount(), "restored PPU continues ticking");
    }

    private static Ricoh2c02 newPpu(byte[] chr) {
        Ricoh2c02 ppu = new Ricoh2c02();
        ppu.setMirroring(Mirroring.VERTICAL);
        ppu.connectChr(new Bus() {
            @Override
            public int read(int address) {
                return chr[address & 0x1FFF] & 0xFF;
            }

            @Override
            public void write(int address, int value) {
                chr[address & 0x1FFF] = (byte) value;
            }
        });
        return ppu;
    }

    private static VideoFrame renderFrame(Ricoh2c02 ppu) {
        VideoFrame[] frame = new VideoFrame[1];
        ppu.connect(new VideoSink() {
            @Override
            public void frameReady(VideoFrame videoFrame) {
                frame[0] = videoFrame;
            }
        });
        ppu.tick(Ricoh2c02.CYCLES_PER_FRAME);
        check(frame[0] != null, "frame delivered");
        return frame[0];
    }

    private static void writeOam(Bus registers, int y, int tile, int attribute, int x) {
        registers.write(3, 0x00);
        registers.write(4, y);
        registers.write(4, tile);
        registers.write(4, attribute);
        registers.write(4, x);
        for (int i = 1; i < 64; i++) {
            registers.write(4, 0xFF);
            registers.write(4, 0x00);
            registers.write(4, 0x00);
            registers.write(4, 0x00);
        }
    }

    private static void fillNametable(Bus registers, int base, int tile) {
        for (int i = 0; i < 0x400; i++) {
            writeVram(registers, base + i, i < 0x3C0 ? tile : 0);
        }
    }

    private static void writePalette(Bus registers, int backdrop, int bg1, int bg2, int sprite1) {
        writeVram(registers, 0x3F00, backdrop);
        writeVram(registers, 0x3F01, bg1);
        writeVram(registers, 0x3F02, bg2);
        writeVram(registers, 0x3F15, sprite1);
    }

    private static void writeVram(Bus registers, int address, int value) {
        registers.write(6, (address >> 8) & 0x3F);
        registers.write(6, address & 0xFF);
        registers.write(7, value);
    }

    private static int readVram(Bus registers, int address) {
        registers.write(6, (address >> 8) & 0x3F);
        registers.write(6, address & 0xFF);
        registers.read(7);
        return registers.read(7) & 0xFF;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
