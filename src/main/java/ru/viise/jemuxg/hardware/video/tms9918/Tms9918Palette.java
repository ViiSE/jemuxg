package ru.viise.jemuxg.hardware.video.tms9918;

/**
 * Палитра TMS9918A.
 *
 * <p>Индекс 0 — прозрачный цвет. Для фона он заменяется backdrop-цветом,
 * для спрайтов означает отсутствие пикселя. В ARGB прозрачный индекс
 * отображается в чёрный, так как у SG-1000 нет внешнего видеосигнала.</p>
 *
 * <p>Значения RGB соответствуют стандартной палитре TMS9918A (NTSC).</p>
 */
public final class Tms9918Palette {

    private static final int[] COLORS = {
        0xFF000000, // 0 transparent (rendered as black)
        0xFF000000, // 1 black
        0xFF21C842, // 2 medium green
        0xFF5EDC78, // 3 light green
        0xFF5455ED, // 4 dark blue
        0xFF7D76FC, // 5 light blue
        0xFFD4524D, // 6 dark red
        0xFF42EBF5, // 7 cyan
        0xFFFC5554, // 8 medium red
        0xFFFF7978, // 9 light red
        0xFFD4C154, // 10 dark yellow
        0xFFE6CE80, // 11 light yellow
        0xFF21B03B, // 12 dark green
        0xFFC95BBA, // 13 magenta
        0xFFCCCCCC, // 14 gray
        0xFFFFFFFF, // 15 white
    };

    private Tms9918Palette() {
    }

    public static int size() {
        return COLORS.length;
    }

    public static int argb(int index) {
        return COLORS[index & 0x0F];
    }
}
