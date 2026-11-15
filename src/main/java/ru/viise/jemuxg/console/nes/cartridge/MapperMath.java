package ru.viise.jemuxg.console.nes.cartridge;

/**
 * Вспомогательная арифметика банков. Не является частью публичного API.
 */
final class MapperMath {

    private MapperMath() {
    }

    static long offset(int bank, int bankSize, int address, int bankCount) {
        if (bankCount <= 0) {
            return -1;
        }
        int normalized = Math.floorMod(bank, bankCount);
        return (long) normalized * bankSize + Math.floorMod(address, bankSize);
    }
}
