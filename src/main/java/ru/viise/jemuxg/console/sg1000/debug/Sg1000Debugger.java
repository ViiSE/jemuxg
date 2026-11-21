package ru.viise.jemuxg.console.sg1000.debug;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import ru.viise.jemuxg.api.debug.ConsoleDebugger;
import ru.viise.jemuxg.api.debug.DebugSession;
import ru.viise.jemuxg.console.sg1000.Sg1000Machine;

/**
 * Отладочный снимок Sega SG-1000: Z80, RAM, VRAM, VDP, PSG и экран.
 *
 * <p>Артефакты пишутся в каталог, предоставленный {@link DebugSession},
 * обычно {@code debug/<rom>/frame-N/}.</p>
 */
public final class Sg1000Debugger implements ConsoleDebugger {

    private final Sg1000Machine machine;

    public Sg1000Debugger(Sg1000Machine machine) {
        this.machine = Objects.requireNonNull(machine, "machine");
    }

    @Override
    public String consoleId() {
        return "sg1000";
    }

    @Override
    public void capture(DebugSession session) throws IOException {
        Sg1000DebugSnapshot snapshot = machine.debugSnapshot();

        byte[] argb = argbBytes(snapshot.framebufferArgb());
        byte[] indices = snapshot.framebufferIndices().clone();
        byte[] ram = snapshot.ram().clone();
        byte[] vram = snapshot.vram().clone();

        session.writePng("screenshot.png", snapshot.width(), snapshot.height(),
                snapshot.framebufferArgb());
        session.writeBytes("framebuffer.argb", argb);
        session.writeBytes("framebuffer.idx", indices);
        session.writeBytes("ram.bin", ram);
        session.writeBytes("vram.bin", vram);
        session.writeText("registers.txt", registersText(snapshot));
        session.writeText("hashes.txt", hashes(argb, indices, ram, vram));
        session.writeText("meta.json", meta(session, snapshot));
    }

    private static byte[] argbBytes(int[] pixels) {
        ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int pixel : pixels) {
            buffer.putInt(pixel);
        }
        return buffer.array();
    }

    private static String registersText(Sg1000DebugSnapshot snapshot) {
        Z80RegisterSnapshot registers = snapshot.registers();
        int[] vdp = snapshot.vdpRegisters();
        int[] psg = snapshot.psgRegisters();
        StringBuilder text = new StringBuilder(768);
        text.append(String.format(
                "PC=%04X SP=%04X AF=%04X BC=%04X DE=%04X HL=%04X IX=%04X IY=%04X%n",
                registers.pc(),
                registers.sp(),
                registers.af(),
                registers.bc(),
                registers.de(),
                registers.hl(),
                registers.ix(),
                registers.iy()));
        text.append(String.format(
                "AF'=%04X BC'=%04X DE'=%04X HL'=%04X"
                        + " I=%02X R=%02X IFF1=%d IFF2=%d IM=%d MEMPTR=%04X HALT=%s cycles=%d%n",
                registers.af2(),
                registers.bc2(),
                registers.de2(),
                registers.hl2(),
                registers.i(),
                registers.r(),
                registers.iff1() ? 1 : 0,
                registers.iff2() ? 1 : 0,
                registers.im(),
                registers.memptr(),
                registers.halted() ? "yes" : "no",
                registers.remainingCycles()));
        text.append(String.format(
                "frame=%d scanline=%d dot=%d%n",
                snapshot.frame(),
                snapshot.scanline(),
                snapshot.dot()));
        text.append(String.format(
                "VDP line=%d frame=%d status=%02X mode=%s irq=%d%n",
                snapshot.vdpLine(),
                snapshot.vdpFrame(),
                snapshot.vdpStatus(),
                snapshot.vdpMode(),
                (snapshot.vdpStatus() & 0x80) != 0 ? 1 : 0));
        text.append("VDP");
        for (int i = 0; i < vdp.length; i++) {
            text.append(String.format(" R%d=%02X", i, vdp[i]));
        }
        text.append(System.lineSeparator());
        text.append("PSG");
        for (int i = 0; i < psg.length; i++) {
            text.append(String.format(" R%d=%02X", i, psg[i]));
        }
        text.append(System.lineSeparator());
        text.append(String.format(
                "cartridge=%s size=%d%n",
                snapshot.cartridgeName(),
                snapshot.cartridgeSize()));
        return text.toString();
    }

    private static String hashes(
            byte[] argb,
            byte[] indices,
            byte[] ram,
            byte[] vram) {
        return """
                framebuffer.argb %s
                framebuffer.idx  %s
                ram.bin          %s
                vram.bin         %s
                """.formatted(
                sha256(argb),
                sha256(indices),
                sha256(ram),
                sha256(vram));
    }

    private static String meta(DebugSession session, Sg1000DebugSnapshot snapshot) {
        return """
                {
                  "console": "sg1000",
                  "rom": "%s",
                  "frame": %d,
                  "width": %d,
                  "height": %d,
                  "scanline": %d,
                  "dot": %d,
                  "vdpMode": "%s",
                  "vdpStatus": %d,
                  "cartridge": "%s",
                  "cartridgeSize": %d,
                  "framebuffer.argb": "ARGB, 4 bytes per pixel, little-endian",
                  "framebuffer.idx": "TMS9918 palette index 0..15, 1 byte per pixel",
                  "ram.bin": "1 KB system RAM at 0xC000-0xC3FF",
                  "vram.bin": "16 KB TMS9918A VRAM"
                }
                """.formatted(
                jsonEscape(session.romName()),
                session.frameNumber(),
                snapshot.width(),
                snapshot.height(),
                snapshot.scanline(),
                snapshot.dot(),
                snapshot.vdpMode(),
                snapshot.vdpStatus(),
                jsonEscape(snapshot.cartridgeName()),
                snapshot.cartridgeSize());
    }

    private static String jsonEscape(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) {
                        result.append(String.format("\\u%04X", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.toString();
    }

    private static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xFF));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
