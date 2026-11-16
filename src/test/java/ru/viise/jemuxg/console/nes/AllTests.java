package ru.viise.jemuxg.console.nes;

import ru.viise.jemuxg.console.atari2600.Atari2600BusTests;
import ru.viise.jemuxg.console.atari2600.Atari6507Tests;

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
        System.out.println("All jemuxg tests OK");
    }
}
