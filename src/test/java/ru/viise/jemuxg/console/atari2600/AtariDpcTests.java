package ru.viise.jemuxg.console.atari2600;

import java.util.Arrays;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariCartridge;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariDpcMapper;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariMapperSelector;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.storage.media.ByteArrayMedia;

/**
 * Тесты DPC-картриджа Pitfall II.
 */
public final class AtariDpcTests {

    private static int checks;

    private AtariDpcTests() {
    }

    public static void main(String[] args) {
        basics();
        displayAndCounters();
        flags();
        musicTiming();
        randomGenerator();
        saveRestore();
        selectorAndCartridge();
        System.out.println("Atari DPC tests OK, checks=" + checks);
    }

    private static void basics() {
        AtariDpcMapper mapper = new AtariDpcMapper(rom(10240));
        check(mapper.name().equals("DPC"), "DPC name");
        check(mapper.bankCount() == 2, "DPC has two 4K banks");
        check(mapper.bank() == 1, "DPC starts in upper bank");
        check(mapper.writable(), "DPC window is writable");
        check(mapper.map(0x1080) == 0x1080, "DPC upper bank program mapping");
        check(mapper.readByte(0x1080) == -1, "DPC program read is delegated");
        check(mapper.readByte(0x1FF8) == -1, "DPC F8 bank hotspot read is delegated");
        check(mapper.bank() == 0, "DPC hotspot $1FF8 selects lower bank");
        check(mapper.readByte(0x1FF9) == -1, "DPC F8 bank hotspot read 1");
        check(mapper.bank() == 1, "DPC hotspot $1FF9 selects upper bank");

        AtariCartridge cartridge = new AtariCartridge(rom(10240));
        check(cartridge.mapper().name().equals("DPC"), "cartridge creates DPC");
        check(cartridge.read(0x1FFC) == 0x80 && cartridge.read(0x1FFD) == 0xF8,
                "DPC reset vector from upper bank");
        check(cartridge.read(0x1080) == 0x21, "DPC program byte through cartridge");
        check(cartridge.read(0x1008) == 0xAA, "DPC display byte through cartridge");
    }

    private static void displayAndCounters() {
        AtariDpcMapper mapper = new AtariDpcMapper(rom(10240));
        mapper.write(0x1050, 0xFF);
        mapper.write(0x1058, 0x07);
        check(mapper.readByte(0x1008) == 0xCC, "DF0 reads display[0]");
        check(mapper.readByte(0x1008) == 0xDD, "DF0 counter decrements after read");
        check(mapper.readByte(0x1009) == 0xAA, "DF1 has independent counter");
        check(mapper.readByte(0x100F) == 0xAA, "DF7 reads its own counter");
    }

    private static void flags() {
        AtariDpcMapper mapper = new AtariDpcMapper(rom(10240));
        mapper.write(0x1040, 5);  // DF0 top
        mapper.write(0x1048, 2);  // DF1 bottom
        mapper.write(0x1050, 5);  // DF0 counter low
        check(mapper.readByte(0x1038) == 0xFF, "DF0 flag set at top");
        mapper.write(0x1050, 2);
        check(mapper.readByte(0x1038) == 0x00, "DF0 flag cleared at bottom");
        mapper.write(0x1050, 0);
        mapper.write(0x1058, 1);
        check(mapper.readByte(0x1038) == 0x00, "DF0 flag stays clear below bottom");
    }

    private static void musicTiming() {
        AtariDpcMapper mapper = new AtariDpcMapper(rom(10240));
        mapper.write(0x1045, 8);  // DF5 top
        mapper.write(0x104D, 1);  // DF5 bottom
        mapper.write(0x105D, 0x10); // DF5 music mode
        mapper.write(0x1055, 0);  // in music mode this loads top
        check(mapper.readByte(0x103D) == 0xFF, "DPC music flag starts at top");

        mapper.tick(1_000);
        check(mapper.readByte(0x103D) == 0x00, "DPC music fetcher ticks down to bottom");

        mapper.tick(200);
        check(mapper.readByte(0x103D) == 0xFF, "DPC music fetcher moves above bottom");

        BinaryStateWriter writer = new BinaryStateWriter();
        mapper.save(writer);
        AtariDpcMapper restored = new AtariDpcMapper(rom(10240));
        restored.restore(new BinaryStateReader(writer.toByteArray()));
        check(restored.debugState().equals(mapper.debugState()), "DPC music timing save/restore");
    }

    private static void randomGenerator() {
        AtariDpcMapper mapper = new AtariDpcMapper(rom(10240));
        check(mapper.readByte(0x1000) == 3, "DPC RNG first value");
        check(mapper.readByte(0x1000) == 7, "DPC RNG advances on every access");
        mapper.write(0x1070, 0);
        check(mapper.readByte(0x1000) == 3, "DPC RNG reset");
    }

    private static void saveRestore() {
        AtariDpcMapper mapper = new AtariDpcMapper(rom(10240));
        mapper.write(0x1040, 7);
        mapper.write(0x1048, 2);
        mapper.write(0x1050, 7);
        mapper.write(0x105D, 0x10); // music mode for DF5
        mapper.write(0x1FF8, 0);     // lower bank
        BinaryStateWriter writer = new BinaryStateWriter();
        mapper.save(writer);

        AtariDpcMapper restored = new AtariDpcMapper(rom(10240));
        restored.restore(new BinaryStateReader(writer.toByteArray()));
        check(restored.bank() == 0, "DPC save restores bank");
        check(restored.debugState().contains("counters=[7"), "DPC save restores counters");
        check(restored.readByte(0x1038) == 0xFF, "DPC save restores flags/top");
    }

    private static void selectorAndCartridge() {
        check(mapperName(rom(10240)).equals("DPC"), "auto 10K selects DPC");
        check(mapperName(rom(10495)).equals("DPC"), "auto padded Pitfall selects DPC");
        check(mapperName(rom(10240), "dpc").equals("DPC"), "explicit DPC");
        check(mapperName(rom(10495), "DPC").equals("DPC"), "explicit DPC is case insensitive");
        check(mapperName(rom(10240), "dpc").equals("DPC"), "explicit DPC trimmed");
        AtariCartridge cartridge = new AtariCartridge(rom(10240), "dpc");
        check(cartridge.mapper().name().equals("DPC"), "cartridge explicit DPC");
        check(cartridge.read(0x1008) == 0xAA, "cartridge DPC read");
        cartridge.write(0x1040, 7);
        cartridge.write(0x1050, 7);
        check(cartridge.read(0x1038) == 0xFF, "cartridge DPC register write");
    }

    private static String mapperName(Media media) {
        return AtariMapperSelector.select(media, null).name();
    }

    private static String mapperName(Media media, String scheme) {
        return AtariMapperSelector.select(media, scheme).name();
    }

    private static Media rom(int size) {
        byte[] data = new byte[size];
        Arrays.fill(data, 0, Math.min(8192, size), (byte) 0x11);
        Arrays.fill(data, 0, Math.min(0x1080, size), (byte) 0x20);
        data[0x1080] = 0x21;
        data[0x1FFC] = (byte) 0x80;
        data[0x1FFD] = (byte) 0xF8;
        data[8192] = (byte) 0xCC;
        data[8193] = (byte) 0xDD;
        data[8192 + 2047] = (byte) 0xAA;
        return new ByteArrayMedia("dpc-test.a26", MediaKind.CARTRIDGE, data);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }
}
