package ru.viise.jemuxg.console.nes;

import ru.viise.jemuxg.console.atari2600.Atari2600BusTests;
import ru.viise.jemuxg.console.atari2600.AtariBankedCartridgeTests;
import ru.viise.jemuxg.console.atari2600.Atari6507Tests;
import ru.viise.jemuxg.console.atari2600.AtariCartridgeTests;
import ru.viise.jemuxg.console.atari2600.AtariExtraMapperTests;
import ru.viise.jemuxg.console.atari2600.AtariMapperSelectionTests;
import ru.viise.jemuxg.console.atari2600.AtariRiotPortsTests;
import ru.viise.jemuxg.console.atari2600.AtariRiotRamTests;
import ru.viise.jemuxg.console.atari2600.AtariRiotTimerTests;
import ru.viise.jemuxg.console.atari2600.AtariTiaAudioTests;
import ru.viise.jemuxg.console.atari2600.AtariTiaTests;

/**
 * Запуск всех контрактных тестов и тестовых ROM одной командой.
 */
public final class AllTests {

    private AllTests() {
    }

    public static void main(String[] args) throws Exception {
        PpuTests.main(args);
        ApuTests.main(args);
        MapperTests.main(args);
        InputTests.main(args);
        TestRomTests.main(args);
        Atari6507Tests.main(args);
        Atari2600BusTests.main(args);
        AtariCartridgeTests.main(args);
        AtariBankedCartridgeTests.main(args);
        AtariExtraMapperTests.main(args);
        AtariMapperSelectionTests.main(args);
        AtariRiotRamTests.main(args);
        AtariRiotTimerTests.main(args);
        AtariRiotPortsTests.main(args);
        AtariTiaTests.main(args);
        AtariTiaAudioTests.main(args);
        System.out.println("All jemuxg tests OK");
    }
}
