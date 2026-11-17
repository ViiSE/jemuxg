package ru.viise.jemuxg.console.atari2600;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.cartridge.Atari3EMapper;
import ru.viise.jemuxg.console.atari2600.cartridge.Atari3FMapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariCartridge;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariE0Mapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariE7Mapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariF4Mapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariF6Mapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariF8Mapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariFeMapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariMapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariSuperChipMapper;
import ru.viise.jemuxg.console.atari2600.debug.AtariDebugSnapshot;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Тесты полного save/restore машины Atari 2600 и состояния мапперов.
 */
public final class AtariStateTests {

    private static int checks;

    private AtariStateTests() {
    }

    public static void main(String[] args) {
        machineMidFrameRoundTrip();
        cartridgeMapperRoundTrip();
        mapperRoundTrips();
        System.out.println("Atari state tests OK, checks=" + checks);
    }

    private static void machineMidFrameRoundTrip() {
        byte[] rom = rom(
                0xA9, 0x46,        // LDA #$46
                0x85, 0x09,        // STA COLUBK
                0xA9, 0xAA,        // LDA #$AA
                0x85, 0x80,        // STA $80
                0xA9, 0x05,        // LDA #5
                0x8D, 0x97, 0x02,  // STA TIM1024T
                0xA9, 0x08,        // LDA #8
                0x85, 0x15,        // STA AUDC0
                0xA9, 0x0A,        // LDA #10
                0x85, 0x17,        // STA AUDF0
                0xA9, 0x0F,        // LDA #15
                0x85, 0x19,        // STA AUDV0
                0x4C, 0x00, 0xF0); // JMP $F000
        Atari2600Machine machine = machine(rom);
        List<VideoFrame> frames = connectVideo(machine);
        CaptureAudio audio = new CaptureAudio();
        machine.connectAudio(audio);

        machine.runCycles(5_000);
        AtariDebugSnapshot before = machine.debugSnapshot();

        BinaryStateWriter writer = new BinaryStateWriter();
        machine.save(writer);
        byte[] state = writer.toByteArray();
        int remaining = Atari2600Constants.CPU_CYCLES_PER_FRAME - 5_000;

        machine.runCycles(remaining);
        check(frames.size() == 1, "first partial frame completes");
        int[] expectedFrame = frames.get(0).pixels();
        short[] expectedAudio = audio.chunk(0);

        machine.restore(new BinaryStateReader(state));
        AtariDebugSnapshot after = machine.debugSnapshot();
        check(after.registers().equals(before.registers()), "CPU registers restored");
        check(after.scanline() == before.scanline()
                && after.colorClock() == before.colorClock()
                && after.wsync() == before.wsync(), "TIA timing restored");
        check(Arrays.equals(after.tiaRegisters(), before.tiaRegisters()),
                "TIA registers restored");
        check(Arrays.equals(after.riotRam(), before.riotRam()), "RIOT RAM restored");
        check(Arrays.equals(after.framebufferArgb(), before.framebufferArgb()),
                "partial framebuffer restored");
        check(Arrays.equals(after.framebufferIndices(), before.framebufferIndices()),
                "partial palette-index framebuffer restored");

        machine.runCycles(remaining);
        check(frames.size() == 2, "frame after restore is delivered");
        check(Arrays.equals(frames.get(1).pixels(), expectedFrame),
                "frame after restore matches original");
        check(Arrays.equals(audio.chunk(1), expectedAudio),
                "audio after restore matches original");
    }

    private static void cartridgeMapperRoundTrip() {
        byte[] rom = bankedRom(8192,
                0xA9, 0x00,        // LDA #$00
                0x8D, 0xF9, 0x1F,  // STA $1FF9: select F8 bank 1
                0x4C, 0x05, 0xF0); // JMP $F005
        AtariCartridge cartridge = new AtariCartridge(
                new ByteArrayMedia("bank.a26", MediaKind.CARTRIDGE, rom));
        cartridge.write(0x1FF9, 0);
        check(cartridge.mapper().bank() == 1, "F8 bank selected");

        BinaryStateWriter writer = new BinaryStateWriter();
        cartridge.save(writer);
        cartridge.write(0x1FF8, 0);
        check(cartridge.mapper().bank() == 0, "F8 bank changed before restore");
        cartridge.restore(new BinaryStateReader(writer.toByteArray()));
        check(cartridge.mapper().bank() == 1, "cartridge mapper bank restored");
        check(cartridge.mapper().map(0x1000) == 0x1000,
                "restored cartridge mapping is bank 1");
        check(cartridge.read(0x1FFC) == (rom[0x1FFC] & 0xFF),
                "restored cartridge reset vector is readable");
    }

    private static void mapperRoundTrips() {
        AtariF8Mapper f8 = new AtariF8Mapper();
        f8.write(0x1FF9, 0);
        roundTrip(f8, () -> f8.write(0x1FF8, 0), () -> f8.bank() == 1,
                "F8 bank restored");

        AtariF6Mapper f6 = new AtariF6Mapper();
        f6.write(0x1FF8, 0);
        roundTrip(f6, () -> f6.write(0x1FF6, 0), () -> f6.bank() == 2,
                "F6 bank restored");

        AtariF4Mapper f4 = new AtariF4Mapper();
        f4.write(0x1FF9, 0);
        roundTrip(f4, () -> f4.write(0x1FF4, 0), () -> f4.bank() == 5,
                "F4 bank restored");

        Atari3FMapper mapper3F = new Atari3FMapper(8192);
        mapper3F.write(0, 2);
        roundTrip(mapper3F, () -> mapper3F.write(0, 0), () -> mapper3F.bank() == 2,
                "3F bank restored");

        Atari3EMapper mapper3E = new Atari3EMapper(8192);
        mapper3E.write(0x3F, 1);
        mapper3E.write(0x3E, 3);
        mapper3E.write(0x1410, 0x55);
        roundTrip(mapper3E, () -> mapper3E.write(0x1400, 0x11),
                () -> mapper3E.readByte(0x1010) == 0x55 && mapper3E.bank() == 7,
                "3E ROM/RAM state restored");

        AtariE0Mapper e0 = new AtariE0Mapper(8192);
        e0.write(0x1FE1, 0);
        e0.write(0x1FE9, 0);
        e0.write(0x1FF2, 0);
        roundTrip(e0, () -> {
            e0.write(0x1FE0, 0);
            e0.write(0x1FE8, 0);
            e0.write(0x1FF0, 0);
        }, () -> e0.bank() == 1 && e0.map(0x1400) == 0x400
                && e0.map(0x1800) == 0x800,
                "E0 segments restored");

        AtariE7Mapper e7 = new AtariE7Mapper(16384);
        e7.write(0x1FE6, 0);
        e7.write(0x1FE9, 0);
        e7.write(0x1800, 0x77);
        roundTrip(e7, () -> {
            e7.write(0x1FE0, 0);
            e7.write(0x1800, 0x11);
        }, () -> e7.bank() == 6 && e7.readByte(0x1900) == 0x77,
                "E7 lower bank and RAM restored");

        AtariFeMapper fe = new AtariFeMapper(8192);
        fe.access(0x01FE, 0);
        fe.access(0x0000, 0);
        roundTrip(fe, () -> {
            fe.access(0x01FE, 0);
            fe.access(0x0000, 0x20);
        }, () -> fe.bank() == 1, "FE bank restored");

        AtariSuperChipMapper superChip = new AtariSuperChipMapper(new AtariF8Mapper());
        superChip.write(0x1000, 0x5A);
        superChip.write(0x1FF9, 0);
        roundTrip(superChip, () -> {
            superChip.write(0x1000, 0);
            superChip.write(0x1FF8, 0);
        }, () -> superChip.readByte(0x1080) == 0x5A && superChip.bank() == 1,
                "SuperChip RAM and base bank restored");
    }

    private static void roundTrip(AtariMapper mapper, Runnable mutate,
            java.util.function.BooleanSupplier verify, String message) {
        BinaryStateWriter writer = new BinaryStateWriter();
        mapper.save(writer);
        byte[] state = writer.toByteArray();
        mutate.run();
        StateReader reader = new BinaryStateReader(state);
        mapper.restore(reader);
        check(verify.getAsBoolean(), message);
    }

    private static Atari2600Machine machine(byte[] rom) {
        Atari2600Machine machine = new Atari2600Machine();
        machine.insert(0, new ByteArrayMedia("state.a26", MediaKind.CARTRIDGE, rom));
        return machine;
    }

    private static List<VideoFrame> connectVideo(Atari2600Machine machine) {
        List<VideoFrame> frames = new ArrayList<>();
        machine.connectVideo(new VideoSink() {
            @Override
            public void frameReady(VideoFrame frame) {
                frames.add(frame);
            }
        });
        return frames;
    }

    private static byte[] rom(int... code) {
        return bankedRom(4096, code);
    }

    private static byte[] bankedRom(int size, int... code) {
        byte[] rom = new byte[size];
        for (int i = 0; i < code.length; i++) {
            rom[i] = (byte) code[i];
        }
        for (int bank = 0; bank < size / 0x1000; bank++) {
            int base = bank * 0x1000;
            rom[base + 0x0FFC] = 0x00;
            rom[base + 0x0FFD] = (byte) 0xF0;
        }
        return rom;
    }

    private static final class CaptureAudio implements AudioSink {
        private final List<short[]> chunks = new ArrayList<>();

        @Override
        public void samplesReady(short[] samples, int count) {
            chunks.add(Arrays.copyOf(samples, count));
        }

        private short[] chunk(int index) {
            return chunks.get(index);
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        System.out.println("OK: " + message);
    }
}
