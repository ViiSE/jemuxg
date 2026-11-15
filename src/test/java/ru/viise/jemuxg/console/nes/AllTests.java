package ru.viise.jemuxg.console.nes;

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
        System.out.println("All jemuxg tests OK");
    }
}
