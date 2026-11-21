package ru.viise.jemuxg.console.sg1000;

/**
 * Константы Sega SG-1000 (NTSC).
 *
 * <p>SG-1000 — не Sega Master System: здесь стоит TMS9918A с 16 КБ VRAM,
 * а не VDP 315-5124. Поэтому тайминги и карта портов фиксируются отдельно
 * и не переиспользуют константы SMS.</p>
 */
public final class Sg1000Constants {

    private Sg1000Constants() {
    }

    /** Тактовая частота Z80A в SG-1000. */
    public static final int CPU_CLOCK_HZ = 3_579_545;

    /** Количество строк в кадре NTSC. */
    public static final int SCREEN_LINES = 262;

    /** Количество тактов CPU на одну строку. */
    public static final int CPU_CYCLES_PER_LINE = 228;

    /** Такты CPU на кадр: 262 * 228 = 59 736. */
    public static final int CPU_CYCLES_PER_FRAME = SCREEN_LINES * CPU_CYCLES_PER_LINE;

    /** Точек VDP на строку (включая невидимую часть). */
    public static final int VDP_DOTS_PER_LINE = 342;

    /** Ширина видимой области. */
    public static final int VISIBLE_WIDTH = 256;

    /** Высота видимой области. */
    public static final int VISIBLE_HEIGHT = 192;

    /** Размер VRAM TMS9918A. */
    public static final int VDP_VRAM_SIZE = 16 * 1024;

    /** Размер системной RAM. */
    public static final int RAM_SIZE = 1024;

    public static final int RAM_START = 0xC000;
    public static final int RAM_END = 0xC3FF;

    public static final int ROM_START = 0x0000;
    public static final int ROM_END = 0xBFFF;

    /** Минимальный размер картриджа: 8 КБ. */
    public static final int MIN_ROM_SIZE = 0x2000;

    /** Максимальный размер картриджа: 48 КБ. */
    public static final int MAX_ROM_SIZE = 0xC000;

    /** Шаг размера ROM: 8 КБ. */
    public static final int ROM_BANK_SIZE = 0x2000;

    /** Размер Sega My Card: 32 КБ. */
    public static final int MY_CARD_ROM_SIZE = 4 * ROM_BANK_SIZE;

    /** Порт данных VDP. */
    public static final int VDP_DATA = 0xBE;

    /** Порт управления/статуса VDP. */
    public static final int VDP_CONTROL = 0xBF;

    /** Начало зеркал VDP-портов. */
    public static final int VDP_PORT_START = 0x80;

    /** Конец зеркал VDP-портов. */
    public static final int VDP_PORT_END = 0xBF;

    /** Порт записи SN76489AN. */
    public static final int PSG_PORT = 0x7F;

    /** Начало зеркал PSG. */
    public static final int PSG_PORT_START = 0x40;

    /** Конец зеркал PSG. */
    public static final int PSG_PORT_END = 0x7F;

    /** Порт первого джойстика (активный низкий уровень). */
    public static final int INPUT_P1 = 0xDC;

    /** Порт второго джойстика и кнопки Reset (активный низкий уровень). */
    public static final int INPUT_P2 = 0xDD;

    /** Начало зеркал портов ввода. */
    public static final int INPUT_PORT_START = 0xC0;

    /** Конец зеркал портов ввода. */
    public static final int INPUT_PORT_END = 0xFF;

    /** Вектор IRQ Z80 в режиме IM 1. */
    public static final int IRQ_VECTOR = 0x0038;

    /** Стартовый адрес после сброса: BIOS отсутствует. */
    public static final int RESET_PC = 0x0000;

    /** Частота дискретизации звука фронтенда. */
    public static final int AUDIO_SAMPLE_RATE = 44_100;

    /** Количество сэмплов на кадр при 60 Гц. */
    public static final int AUDIO_SAMPLES_PER_FRAME = AUDIO_SAMPLE_RATE / 60;
}
