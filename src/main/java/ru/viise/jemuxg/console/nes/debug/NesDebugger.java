package ru.viise.jemuxg.console.nes.debug;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import ru.viise.jemuxg.api.debug.ConsoleDebugger;
import ru.viise.jemuxg.api.debug.CpuRegisters;
import ru.viise.jemuxg.api.debug.DebugSession;
import ru.viise.jemuxg.console.nes.NesMachine;

/**
 * Отладочный снимок NES: экран, индексный кадр, память и регистры.
 */
public final class NesDebugger implements ConsoleDebugger {

    private final NesMachine machine;

    public NesDebugger(NesMachine machine) {
        this.machine = Objects.requireNonNull(machine, "machine");
    }

    @Override
    public String consoleId() {
        return "nes";
    }

    @Override
    public void capture(DebugSession session) throws IOException {
        NesDebugSnapshot snapshot = machine.debugSnapshot();

        byte[] argb = argbBytes(snapshot.framebufferArgb());
        byte[] indices = snapshot.framebufferIndices().clone();
        byte[] cpuRam = snapshot.cpuRam().clone();
        byte[] prgRam = snapshot.prgRam().clone();
        byte[] vram = snapshot.vram().clone();
        byte[] palette = snapshot.palette().clone();
        byte[] oam = snapshot.oam().clone();

        session.writePng("screenshot.png", snapshot.width(), snapshot.height(), snapshot.framebufferArgb());
        session.writeBytes("framebuffer.argb", argb);
        session.writeBytes("framebuffer.idx", indices);
        session.writeBytes("cpu-ram.bin", cpuRam);
        session.writeBytes("prg-ram.bin", prgRam);
        session.writeBytes("vram.bin", vram);
        session.writeBytes("palette.bin", palette);
        session.writeBytes("oam.bin", oam);
        session.writeText("registers.txt", registersText(snapshot.registers()));
        session.writeText("hashes.txt", hashes(argb, indices, cpuRam, prgRam, vram, palette, oam));
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

    private static String registersText(CpuRegisters registers) {
        return String.format(
                "PC=%04X A=%02X X=%02X Y=%02X SP=%02X P=%02X%n",
                registers.pc(),
                registers.a(),
                registers.x(),
                registers.y(),
                registers.sp(),
                registers.status());
    }

    private static String hashes(
            byte[] argb,
            byte[] indices,
            byte[] cpuRam,
            byte[] prgRam,
            byte[] vram,
            byte[] palette,
            byte[] oam) {
        return """
                framebuffer.argb %s
                framebuffer.idx  %s
                cpu-ram.bin      %s
                prg-ram.bin      %s
                vram.bin         %s
                palette.bin      %s
                oam.bin          %s
                """.formatted(
                sha256(argb),
                sha256(indices),
                sha256(cpuRam),
                sha256(prgRam),
                sha256(vram),
                sha256(palette),
                sha256(oam));
    }

    private static String meta(DebugSession session, NesDebugSnapshot snapshot) {
        return """
                {
                  "console": "nes",
                  "rom": "%s",
                  "frame": %d,
                  "width": %d,
                  "height": %d,
                  "mapper": "%s",
                  "mapperId": %d,
                  "framebuffer.argb": "ARGB, 4 bytes per pixel, little-endian",
                  "framebuffer.idx": "NES palette index, 1 byte per pixel"
                }
                """.formatted(
                jsonEscape(session.romName()),
                session.frameNumber(),
                snapshot.width(),
                snapshot.height(),
                jsonEscape(snapshot.mapperName()),
                snapshot.mapperId());
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
