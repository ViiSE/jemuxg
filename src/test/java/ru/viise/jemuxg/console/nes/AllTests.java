package ru.viise.jemuxg.console.nes;

import ru.viise.jemuxg.console.atari2600.Atari2600BusTests;
import ru.viise.jemuxg.console.atari2600.AtariBankedCartridgeTests;
import ru.viise.jemuxg.console.atari2600.Atari6507Tests;
import ru.viise.jemuxg.console.atari2600.AtariCartridgeTests;
import ru.viise.jemuxg.console.atari2600.AtariDebuggerTests;
import ru.viise.jemuxg.console.atari2600.AtariDpcTests;
import ru.viise.jemuxg.console.atari2600.AtariExtraMapperTests;
import ru.viise.jemuxg.console.atari2600.AtariFrontendTests;
import ru.viise.jemuxg.console.atari2600.AtariInputTests;
import ru.viise.jemuxg.console.atari2600.AtariIntegrationTests;
import ru.viise.jemuxg.console.atari2600.AtariMapperSelectionTests;
import ru.viise.jemuxg.console.atari2600.AtariMachineTests;
import ru.viise.jemuxg.console.atari2600.AtariRiotPortsTests;
import ru.viise.jemuxg.console.atari2600.AtariRiotRamTests;
import ru.viise.jemuxg.console.atari2600.AtariReferenceTests;
import ru.viise.jemuxg.console.atari2600.AtariRiotTimerTests;
import ru.viise.jemuxg.console.atari2600.AtariStateTests;
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
        AtariDebuggerTests.main(args);
        AtariDpcTests.main(args);
        AtariExtraMapperTests.main(args);
        AtariFrontendTests.main(args);
        AtariInputTests.main(args);
        AtariIntegrationTests.main(args);
        AtariMapperSelectionTests.main(args);
        AtariMachineTests.main(args);
        AtariRiotRamTests.main(args);
        AtariRiotTimerTests.main(args);
        AtariRiotPortsTests.main(args);
        AtariReferenceTests.main(args);
        AtariStateTests.main(args);
        ru.viise.jemuxg.console.atari2600.StellaTiaPlayerTests.main(args);
        ru.viise.jemuxg.console.atari2600.StellaTiaMissileTests.main(args);
        ru.viise.jemuxg.console.atari2600.StellaTiaBallTests.main(args);
        ru.viise.jemuxg.console.atari2600.StellaTiaPlayfieldTests.main(args);
        ru.viise.jemuxg.console.atari2600.StellaTiaFrameTests.main(args);
        ru.viise.jemuxg.console.atari2600.StellaTiaHmoveTests.main(args);
        ru.viise.jemuxg.console.atari2600.StellaTiaReferenceTests.main(args);
        ru.viise.jemuxg.console.atari2600.StellaTiaMachineTests.main(args);
        ru.viise.jemuxg.console.atari2600.StellaTiaTests.main(args);
        AtariTiaTests.main(args);
        AtariTiaAudioTests.main(args);
        System.out.println("All jemuxg tests OK");
    }
}
