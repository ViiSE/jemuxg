package ru.viise.jemuxg.console.atari2600;

/**
 * Аппаратные константы Atari 2600 для NTSC.
 *
 * <p>Пока моделируется только NTSC-версия. PAL/SECAM отличаются частотой
 * кадров, количеством строк и палитрой, поэтому будут добавлены отдельно.</p>
 *
 * <h2>Тайминги</h2>
 * <pre>
 * цветовой такт:          3 579 545 Гц
 * такт CPU:               3 579 545 / 3 = 1 193 182 Гц
 * цветовых тактов/строку: 228
 * строк/кадр:             262
 * тактов CPU/строку:      228 / 3 = 76
 * тактов CPU/кадр:        76 * 262 = 19 912
 * частота кадров:         3 579 545 / (228 * 262) ≈ 59.9227 Гц
 * </pre>
 *
 * <p>Видимая область TIA — 160×192 пикселя. Кадр эмулятора отдаётся
 * фронтенду именно в этом размере; служебные строки и горизонтальные
 * overscan-области не выводятся.</p>
 */
public final class Atari2600Constants {

    // --- Тактирование -----------------------------------------------------

    /** Частота цветового такта NTSC, Гц. */
    public static final int COLOR_CLOCK_HZ = 3_579_545;

    /** Частота тактов CPU (6507), Гц. */
    public static final int CPU_CLOCK_HZ = 1_193_182;

    /** Сколько цветовых тактов TIA приходится на один такт CPU. */
    public static final int COLOR_CLOCKS_PER_CPU_CYCLE = 3;

    /** Количество цветовых тактов в одной строке развёртки. */
    public static final int COLOR_CLOCKS_PER_SCANLINE = 228;

    /** Количество строк в NTSC-кадре, включая вертикальный blanking. */
    public static final int SCANLINES_PER_FRAME = 262;

    /** Количество цветовых тактов в одном NTSC-кадре. */
    public static final int COLOR_CLOCKS_PER_FRAME =
            COLOR_CLOCKS_PER_SCANLINE * SCANLINES_PER_FRAME;

    /** Количество тактов CPU в одной строке. */
    public static final int CPU_CYCLES_PER_SCANLINE =
            COLOR_CLOCKS_PER_SCANLINE / COLOR_CLOCKS_PER_CPU_CYCLE;

    /** Количество тактов CPU в одном NTSC-кадре. */
    public static final int CPU_CYCLES_PER_FRAME =
            CPU_CYCLES_PER_SCANLINE * SCANLINES_PER_FRAME;

    /** Реальная частота NTSC-кадров; для расчётов, не для wall-clock. */
    public static final double FRAME_RATE_HZ =
            (double) COLOR_CLOCK_HZ / COLOR_CLOCKS_PER_FRAME;

    // --- Видео ------------------------------------------------------------

    /** Ширина видимой области TIA. */
    public static final int VISIBLE_WIDTH = 160;

    /** Высота видимой области TIA. */
    public static final int VISIBLE_HEIGHT = 192;

    /** Количество цветов в палитре TIA (7-битный индекс). */
    public static final int TIA_COLOR_COUNT = 128;

    // --- Звук -------------------------------------------------------------

    /** Частота дискретизации звукового выхода эмулятора. */
    public static final int AUDIO_SAMPLE_RATE = 44_100;

    // --- Адресное пространство 6507 --------------------------------------

    /** Разрядность адресной шины 6507. */
    public static final int ADDRESS_BITS = 13;

    /** Маска 13-битного адреса. */
    public static final int ADDRESS_MASK = (1 << ADDRESS_BITS) - 1;

    /** Полный размер адресного пространства 6507. */
    public static final int ADDRESS_SPACE = 1 << ADDRESS_BITS;

    /** Вектор сброса в адресном пространстве 6507. */
    public static final int RESET_VECTOR = 0x1FFC;

    /** Вектор IRQ/BRK в адресном пространстве 6507. */
    public static final int IRQ_VECTOR = 0x1FFE;

    // --- Карта памяти -----------------------------------------------------

    /** Начало регистров TIA. */
    public static final int TIA_BASE = 0x0000;

    /** Конец регистров TIA. */
    public static final int TIA_END = 0x003F;

    /** Количество адресов в блоке TIA. */
    public static final int TIA_REGISTER_COUNT = TIA_END - TIA_BASE + 1;

    /** Маска адреса внутри блока TIA (регистры занимают 64 байта). */
    public static final int TIA_ADDRESS_MASK = TIA_REGISTER_COUNT - 1;

    /** Конец второго (зеркального) окна TIA. */
    public static final int TIA_MIRROR_END = TIA_END + TIA_REGISTER_COUNT;

    /** Начало встроенной RAM RIOT. */
    public static final int RIOT_RAM_BASE = 0x0080;

    /** Конец встроенной RAM RIOT. */
    public static final int RIOT_RAM_END = 0x00FF;

    /** Размер встроенной RAM RIOT. */
    public static final int RIOT_RAM_SIZE = RIOT_RAM_END - RIOT_RAM_BASE + 1;

    /** Маска смещения внутри RAM RIOT. */
    public static final int RIOT_RAM_OFFSET_MASK = RIOT_RAM_SIZE - 1;

    /** Начало регистров RIOT 6532. */
    public static final int RIOT_BASE = 0x0280;

    /** Конец регистров RIOT 6532. */
    public static final int RIOT_END = 0x029F;

    /** Количество регистров RIOT 6532. */
    public static final int RIOT_REGISTER_COUNT = RIOT_END - RIOT_BASE + 1;

    /** Начало адресного пространства картриджа. */
    public static final int CARTRIDGE_BASE = 0x1000;

    /** Конец адресного пространства картриджа. */
    public static final int CARTRIDGE_END = 0x1FFF;

    /** Размер адресного окна картриджа (4 КБ). */
    public static final int CARTRIDGE_WINDOW_SIZE =
            CARTRIDGE_END - CARTRIDGE_BASE + 1;

    /** Маска смещения внутри окна картриджа. */
    public static final int CARTRIDGE_OFFSET_MASK = CARTRIDGE_WINDOW_SIZE - 1;

    /** Вектор сброса внутри окна картриджа. */
    public static final int CARTRIDGE_RESET_VECTOR = 0x1FFC;

    /** Вектор IRQ/BRK внутри окна картриджа. */
    public static final int CARTRIDGE_IRQ_VECTOR = 0x1FFE;

    private Atari2600Constants() {
    }
}
