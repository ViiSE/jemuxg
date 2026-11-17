package ru.viise.jemuxg.console.atari2600.debug;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import ru.viise.jemuxg.api.debug.ConsoleDebugger;
import ru.viise.jemuxg.api.debug.CpuRegisters;
import ru.viise.jemuxg.api.debug.DebugSession;
import ru.viise.jemuxg.console.atari2600.Atari2600Machine;
import ru.viise.jemuxg.hardware.video.tia.Tia;

/**
 * Отладочный снимок Atari 2600: экран, память RIOT, регистры TIA/RIOT
 * и состояние маппера.
 */
public final class AtariDebugger implements ConsoleDebugger {

    private final Atari2600Machine machine;

    public AtariDebugger(Atari2600Machine machine) {
        this.machine = Objects.requireNonNull(machine, "machine");
    }

    @Override
    public String consoleId() {
        return "atari2600";
    }

    @Override
    public void capture(DebugSession session) throws IOException {
        AtariDebugSnapshot snapshot = machine.debugSnapshot();

        byte[] argb = argbBytes(snapshot.framebufferArgb());
        byte[] indices = snapshot.framebufferIndices().clone();
        byte[] riotRam = snapshot.riotRam().clone();
        byte[] tiaRegisters = snapshot.tiaRegisters().clone();
        byte[] riotRegisters = snapshot.riotRegisters().clone();
        byte[] mapperRam = snapshot.mapperRam().clone();

        session.writePng("screenshot.png", snapshot.width(), snapshot.height(),
                snapshot.framebufferArgb());
        session.writeBytes("framebuffer.argb", argb);
        session.writeBytes("framebuffer.idx", indices);
        session.writeBytes("riot-ram.bin", riotRam);
        session.writeBytes("tia-registers.bin", tiaRegisters);
        session.writeBytes("riot-registers.bin", riotRegisters);
        session.writeBytes("mapper-ram.bin", mapperRam);
        session.writeText("registers.txt", registersText(snapshot));
        session.writeText("hashes.txt",
                hashes(argb, indices, riotRam, tiaRegisters, riotRegisters, mapperRam));
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

    private static String registersText(AtariDebugSnapshot snapshot) {
        CpuRegisters registers = snapshot.registers();
        byte[] tia = snapshot.tiaRegisters();
        byte[] riot = snapshot.riotRegisters();
        return """
                PC=%04X A=%02X X=%02X Y=%02X SP=%02X P=%02X
                TIA frame=%d scanline=%d colorClock=%d wsync=%d
                TIA P0=%d P1=%d M0=%d M1=%d BL=%d
                TIA GRP0=%02X GRP1=%02X ENAM0=%02X ENAM1=%02X ENABL=%02X
                RIOT timer=%02X underflow=%02X
                RIOT SWCHA=%02X SWACNT=%02X SWCHB=%02X SWBCNT=%02X
                mapper=%s bank=%d/%d writable=%s
                mapper-state=%s
                mapper-ram-bytes=%d
                """.formatted(
                registers.pc(),
                registers.a(),
                registers.x(),
                registers.y(),
                registers.sp(),
                registers.status(),
                snapshot.frame(),
                snapshot.scanline(),
                snapshot.colorClock(),
                snapshot.wsync() ? 1 : 0,
                tia[Tia.RESP0] & 0xFF,
                tia[Tia.RESP1] & 0xFF,
                tia[Tia.RESM0] & 0xFF,
                tia[Tia.RESM1] & 0xFF,
                tia[Tia.RESBL] & 0xFF,
                tia[Tia.GRP0] & 0xFF,
                tia[Tia.GRP1] & 0xFF,
                tia[Tia.ENAM0] & 0xFF,
                tia[Tia.ENAM1] & 0xFF,
                tia[Tia.ENABL] & 0xFF,
                riot[0x14] & 0xFF,
                riot[0x15] & 0xFF,
                riot[0x00] & 0xFF,
                riot[0x01] & 0xFF,
                riot[0x02] & 0xFF,
                riot[0x03] & 0xFF,
                snapshot.mapperName(),
                snapshot.mapperBank(),
                snapshot.mapperBankCount(),
                snapshot.mapperWritable(),
                snapshot.mapperState(),
                snapshot.mapperRam().length);
    }

    private static String hashes(
            byte[] argb,
            byte[] indices,
            byte[] riotRam,
            byte[] tiaRegisters,
            byte[] riotRegisters,
            byte[] mapperRam) {
        return """
                framebuffer.argb   %s
                framebuffer.idx    %s
                riot-ram.bin       %s
                tia-registers.bin  %s
                riot-registers.bin %s
                mapper-ram.bin     %s
                """.formatted(
                sha256(argb),
                sha256(indices),
                sha256(riotRam),
                sha256(tiaRegisters),
                sha256(riotRegisters),
                sha256(mapperRam));
    }

    private static String meta(DebugSession session, AtariDebugSnapshot snapshot) {
        return """
                {
                  "console": "atari2600",
                  "rom": "%s",
                  "frame": %d,
                  "width": %d,
                  "height": %d,
                  "mapper": "%s",
                  "mapperState": "%s",
                  "mapperBank": %d,
                  "mapperBankCount": %d,
                  "mapperWritable": %s,
                  "mapperRamBytes": %d,
                  "framebuffer.argb": "ARGB, 4 bytes per pixel, little-endian",
                  "framebuffer.idx": "TIA palette index 0..127, 1 byte per pixel",
                  "tia-registers.bin": "64 bytes, latched/effective TIA registers"
                }
                """.formatted(
                jsonEscape(session.romName()),
                session.frameNumber(),
                snapshot.width(),
                snapshot.height(),
                jsonEscape(snapshot.mapperName()),
                jsonEscape(snapshot.mapperState()),
                snapshot.mapperBank(),
                snapshot.mapperBankCount(),
                snapshot.mapperWritable(),
                snapshot.mapperRam().length);
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
