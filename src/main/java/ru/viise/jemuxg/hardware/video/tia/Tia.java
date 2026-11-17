package ru.viise.jemuxg.hardware.video.tia;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;
import ru.viise.jemuxg.hardware.audio.tia.TiaAudio;

/**
 * Каркас видеочасти TIA для NTSC Atari 2600.
 *
 * <p>Сейчас реализованы тайминги развёртки, служебные регистры
 * {@code VSYNC}, {@code VBLANK} и {@code WSYNC}, палитра TIA, playfield,
 * игроки, ракеты, мяч и горизонтальное движение {@code HMOVE}.</p>
 *
 * <p>Метод {@link #tick(long)} принимает количество <b>цветовых тактов</b>
 * TIA. Один такт CPU равен трём цветовым тактам.</p>
 */
public final class Tia implements TiaVideo {

    /** VSYNC: вертикальная синхронизация (бит 1). */
    public static final int VSYNC = 0x00;

    /** VBLANK: вертикальное гашение (бит 1). */
    public static final int VBLANK = 0x01;

    /** WSYNC: остановка CPU до конца текущей строки. */
    public static final int WSYNC = 0x02;

    /** COLUP0: цвет игрока 0 (также левая половина playfield в score mode). */
    public static final int COLUP0 = 0x06;

    /** COLUP1: цвет игрока 1 (также правая половина playfield в score mode). */
    public static final int COLUP1 = 0x07;

    /** COLUPF: цвет playfield и мяча. */
    public static final int COLUPF = 0x08;

    /** COLUBK: цвет фона. */
    public static final int COLUBK = 0x09;

    /** CTRLPF: отражение, score mode, приоритет и размер мяча. */
    public static final int CTRLPF = 0x0A;

    /** PF0: младшие 4 бита playfield (биты 4-7 регистра). */
    public static final int PF0 = 0x0D;

    /** PF1: средние 8 бит playfield. */
    public static final int PF1 = 0x0E;

    /** PF2: старшие 8 бит playfield. */
    public static final int PF2 = 0x0F;

    /** RESP0: сброс горизонтальной позиции игрока 0. */
    public static final int RESP0 = 0x10;

    /** RESP1: сброс горизонтальной позиции игрока 1. */
    public static final int RESP1 = 0x11;

    /** RESM0: сброс горизонтальной позиции ракеты 0. */
    public static final int RESM0 = 0x12;

    /** RESM1: сброс горизонтальной позиции ракеты 1. */
    public static final int RESM1 = 0x13;

    /** RESBL: сброс горизонтальной позиции мяча. */
    public static final int RESBL = 0x14;

    /** GRP0: 8-битный шаблон игрока 0. */
    public static final int GRP0 = 0x1B;

    /** GRP1: 8-битный шаблон игрока 1. */
    public static final int GRP1 = 0x1C;

    /** ENAM0: включение ракеты 0 (бит 1). */
    public static final int ENAM0 = 0x1D;

    /** ENAM1: включение ракеты 1 (бит 1). */
    public static final int ENAM1 = 0x1E;

    /** ENABL: включение мяча (бит 1). */
    public static final int ENABL = 0x1F;

    /** NUSIZ0: количество копий и размер игрока/ракеты 0. */
    public static final int NUSIZ0 = 0x04;

    /** NUSIZ1: количество копий и размер игрока/ракеты 1. */
    public static final int NUSIZ1 = 0x05;

    /** REFP0: отражение игрока 0 (бит 3). */
    public static final int REFP0 = 0x0B;

    /** REFP1: отражение игрока 1 (бит 3). */
    public static final int REFP1 = 0x0C;

    /** VDELP0: вертикальная задержка игрока 0 (бит 0). */
    public static final int VDELP0 = 0x25;

    /** VDELP1: вертикальная задержка игрока 1 (бит 0). */
    public static final int VDELP1 = 0x26;

    /** VDELBL: вертикальная задержка мяча (бит 0). */
    public static final int VDELBL = 0x27;

    /** RESMP0: привязка ракеты 0 к игроку 0 (бит 1). */
    public static final int RESMP0 = 0x28;

    /** RESMP1: привязка ракеты 1 к игроку 1 (бит 1). */
    public static final int RESMP1 = 0x29;

    /** HMP0: горизонтальное движение игрока 0 (старший nibble). */
    public static final int HMP0 = 0x20;

    /** HMP1: горизонтальное движение игрока 1. */
    public static final int HMP1 = 0x21;

    /** HMM0: горизонтальное движение ракеты 0. */
    public static final int HMM0 = 0x22;

    /** HMM1: горизонтальное движение ракеты 1. */
    public static final int HMM1 = 0x23;

    /** HMBL: горизонтальное движение мяча. */
    public static final int HMBL = 0x24;

    /** HMOVE: применить горизонтальное движение. */
    public static final int HMOVE = 0x2A;

    /** HMCLR: очистить все регистры горизонтального движения. */
    public static final int HMCLR = 0x2B;

    /** AUDC0/AUDC1: управление аудиоканалами. */
    public static final int AUDC0 = 0x15;
    public static final int AUDC1 = 0x16;
    /** AUDF0/AUDF1: делители частоты аудиоканалов. */
    public static final int AUDF0 = 0x17;
    public static final int AUDF1 = 0x18;
    /** AUDV0/AUDV1: громкость аудиоканалов. */
    public static final int AUDV0 = 0x19;
    public static final int AUDV1 = 0x1A;

    /** CXCLR: очистить защёлки коллизий. */
    public static final int CXCLR = 0x2C;

    /** CXM0P: коллизии missile 0 с player 0/1. */
    public static final int CXM0P = 0x30;
    /** CXM1P: коллизии missile 1 с player 0/1. */
    public static final int CXM1P = 0x31;
    /** CXP0FB: коллизии player 0 с playfield/ball. */
    public static final int CXP0FB = 0x32;
    /** CXP1FB: коллизии player 1 с playfield/ball. */
    public static final int CXP1FB = 0x33;
    /** CXM0FB: коллизии missile 0 с playfield/ball. */
    public static final int CXM0FB = 0x34;
    /** CXM1FB: коллизии missile 1 с playfield/ball. */
    public static final int CXM1FB = 0x35;
    /** CXBLPF: коллизия ball с playfield. */
    public static final int CXBLPF = 0x36;
    /** CXPPMM: коллизии player 0/1 и missile 0/1. */
    public static final int CXPPMM = 0x37;

    /** INPT0..INPT3: paddle-входы. */
    public static final int INPT0 = 0x38;
    public static final int INPT1 = 0x39;
    public static final int INPT2 = 0x3A;
    public static final int INPT3 = 0x3B;
    /** INPT4/INPT5: кнопка fire игроков 0/1. */
    public static final int INPT4 = 0x3C;
    public static final int INPT5 = 0x3D;

    private static final int VSYNC_BIT = 0x02;
    private static final int VBLANK_BIT = 0x02;

    /**
     * Смещения копий по NUSIZ для игроков и ракет.
     * Индекс — младшие 3 бита NUSIZ.
     */
    private static final int[][] COPY_OFFSETS = {
        {0},
        {0, 16},
        {0, 32},
        {0, 16, 32},
        {0, 64},
        {0},
        {0, 32, 64},
        {0}
    };

    private final int[] pixels = new int[
            Atari2600Constants.VISIBLE_WIDTH * Atari2600Constants.VISIBLE_HEIGHT];

    private final byte[] pixelIndices = new byte[pixels.length];
    private final TiaAudio audio = new TiaAudio();

    private VideoSink sink;
    private int vsyncRegister;
    private int vblankRegister;
    private int coluP0;
    private int coluP1;
    private int coluPf;
    private int coluBk;
    private int ctrlPf;
    private int pf0;
    private int pf1;
    private int pf2;
    private int nusiz0;
    private int nusiz1;
    private int refp0;
    private int refp1;
    private int grp0Old;
    private int grp0New;
    private int grp1Old;
    private int grp1New;
    private int enam0;
    private int enam1;
    private int enablOld;
    private int enablNew;
    private int vdelp0;
    private int vdelp1;
    private int vdelbl;
    private int resmp0;
    private int resmp1;
    private int hmp0;
    private int hmp1;
    private int hmm0;
    private int hmm1;
    private int hmbl;

    private int cxM0P;
    private int cxM1P;
    private int cxP0FB;
    private int cxP1FB;
    private int cxM0FB;
    private int cxM1FB;
    private int cxBLPF;
    private int cxPPMM;

    private Controller controller0;
    private Controller controller1;

    private int player0X;
    private int player1X;
    private int missile0X;
    private int missile1X;
    private int ballX;
    private int scanline;
    private int colorClock;
    private boolean wsync;
    private long frameCount;

    public Tia() {
        clearFramebuffer();
    }

    @Override
    public Bus registers() {
        return new Bus() {
            @Override
            public int read(int address) {
                return readRegister(address & Atari2600Constants.TIA_ADDRESS_MASK);
            }

            @Override
            public void write(int address, int value) {
                writeRegister(address & Atari2600Constants.TIA_ADDRESS_MASK, value);
            }
        };
    }

    @Override
    public void connectPlayer(int port, Controller controller) {
        switch (port) {
            case 0 -> controller0 = controller;
            case 1 -> controller1 = controller;
            default -> throw new IllegalArgumentException("Invalid Atari player port: " + port);
        }
    }

    private int readRegister(int register) {
        return switch (register) {
            case VSYNC -> vsyncRegister;
            case VBLANK -> vblankRegister;
            case CXM0P -> cxM0P;
            case CXM1P -> cxM1P;
            case CXP0FB -> cxP0FB;
            case CXP1FB -> cxP1FB;
            case CXM0FB -> cxM0FB;
            case CXM1FB -> cxM1FB;
            case CXBLPF -> cxBLPF;
            case CXPPMM -> cxPPMM;
            case INPT0, INPT1, INPT2, INPT3 -> 0x80;
            case INPT4 -> fireInput(0);
            case INPT5 -> fireInput(1);
            default -> 0;
        };
    }

    private int fireInput(int port) {
        Controller controller = port == 0 ? controller0 : controller1;
        return controller != null && controller.pressed(Button.A) ? 0x00 : 0x80;
    }

    private void writeRegister(int register, int value) {
        switch (register) {
            case VSYNC -> vsyncRegister = value & 0xFF;
            case VBLANK -> vblankRegister = value & 0xFF;
            case WSYNC -> wsync = true;
            case COLUP0 -> coluP0 = value & 0xFF;
            case COLUP1 -> coluP1 = value & 0xFF;
            case COLUPF -> coluPf = value & 0xFF;
            case COLUBK -> coluBk = value & 0xFF;
            case CTRLPF -> ctrlPf = value & 0xFF;
            case PF0 -> pf0 = value & 0xFF;
            case PF1 -> pf1 = value & 0xFF;
            case PF2 -> pf2 = value & 0xFF;
            case NUSIZ0 -> nusiz0 = value & 0xFF;
            case NUSIZ1 -> nusiz1 = value & 0xFF;
            case REFP0 -> refp0 = value & 0xFF;
            case REFP1 -> refp1 = value & 0xFF;
            case GRP0 -> {
                grp0Old = grp0New;
                grp0New = value & 0xFF;
            }
            case GRP1 -> {
                grp1Old = grp1New;
                grp1New = value & 0xFF;
            }
            case ENAM0 -> enam0 = value & 0xFF;
            case ENAM1 -> enam1 = value & 0xFF;
            case ENABL -> {
                enablOld = enablNew;
                enablNew = value & 0xFF;
            }
            case VDELP0 -> vdelp0 = value & 0xFF;
            case VDELP1 -> vdelp1 = value & 0xFF;
            case VDELBL -> vdelbl = value & 0xFF;
            case RESMP0 -> resmp0 = value & 0xFF;
            case RESMP1 -> resmp1 = value & 0xFF;
            case HMP0 -> hmp0 = value & 0xFF;
            case HMP1 -> hmp1 = value & 0xFF;
            case HMM0 -> hmm0 = value & 0xFF;
            case HMM1 -> hmm1 = value & 0xFF;
            case HMBL -> hmbl = value & 0xFF;
            case HMOVE -> applyHmove();
            case HMCLR -> clearHmove();
            case AUDC0, AUDC1, AUDF0, AUDF1, AUDV0, AUDV1 ->
                    audio.writeRegister(register, value);
            case CXCLR -> clearCollisions();
            case RESP0 -> player0X = currentVisibleX();
            case RESP1 -> player1X = currentVisibleX();
            case RESM0 -> missile0X = currentVisibleX();
            case RESM1 -> missile1X = currentVisibleX();
            case RESBL -> ballX = currentVisibleX();
            default -> {
                // Остальные регистры TIA будут добавлены на этапах 4.5-4.6.
            }
        }
    }

    /**
     * @return включена ли вертикальная синхронизация
     */
    public boolean vsync() {
        return (vsyncRegister & VSYNC_BIT) != 0;
    }

    /**
     * @return включено ли вертикальное гашение
     */
    public boolean vblank() {
        return (vblankRegister & VBLANK_BIT) != 0;
    }

    /**
     * @return остановлен ли CPU записью в WSYNC до конца строки
     */
    @Override
    public boolean cpuHalted() {
        return wsync;
    }

    @Override
    public String name() {
        return "TIA";
    }

    @Override
    public void reset() {
        vsyncRegister = 0;
        vblankRegister = 0;
        coluP0 = 0;
        coluP1 = 0;
        coluPf = 0;
        coluBk = 0;
        ctrlPf = 0;
        pf0 = 0;
        pf1 = 0;
        pf2 = 0;
        nusiz0 = 0;
        nusiz1 = 0;
        refp0 = 0;
        refp1 = 0;
        grp0Old = 0;
        grp0New = 0;
        grp1Old = 0;
        grp1New = 0;
        enam0 = 0;
        enam1 = 0;
        enablOld = 0;
        enablNew = 0;
        vdelp0 = 0;
        vdelp1 = 0;
        vdelbl = 0;
        resmp0 = 0;
        resmp1 = 0;
        hmp0 = 0;
        hmp1 = 0;
        hmm0 = 0;
        hmm1 = 0;
        hmbl = 0;
        clearCollisions();
        player0X = 0;
        player1X = 0;
        missile0X = 0;
        missile1X = 0;
        ballX = 0;
        scanline = 0;
        colorClock = 0;
        wsync = false;
        frameCount = 0;
        audio.reset();
        clearFramebuffer();
    }

    private void clearFramebuffer() {
        Arrays.fill(pixels, TiaPalette.ARGB[0]);
        Arrays.fill(pixelIndices, (byte) 0);
    }

    @Override
    public void connect(VideoSink sink) {
        this.sink = sink;
    }

    /**
     * Подключает аудиовыход TIA.
     */
    public void connectAudio(AudioSink sink) {
        audio.connect(sink);
    }

    @Override
    public int width() {
        return Atari2600Constants.VISIBLE_WIDTH;
    }

    @Override
    public int height() {
        return Atari2600Constants.VISIBLE_HEIGHT;
    }

    @Override
    public int scanline() {
        return scanline;
    }

    @Override
    public int colorClock() {
        return colorClock;
    }

    public long frameCount() {
        return frameCount;
    }

    public int[] framebufferArgb() {
        return pixels.clone();
    }

    /**
     * @return копия индексного framebuffer (0..127)
     */
    public byte[] framebufferIndices() {
        return pixelIndices.clone();
    }

    /**
     * Снимок 64 регистров TIA без побочных эффектов.
     *
     * <p>Для регистров-защёлок возвращается записанное значение. В слотах
     * {@code $10..$14} находятся текущие X-позиции объектов, в
     * {@code $1B/$1C} — текущие GRP, в {@code $38..$3D} — входы, а в
     * {@code $30..$37} — защёлки коллизий.</p>
     */
    public byte[] registerSnapshot() {
        byte[] registers = new byte[Atari2600Constants.TIA_REGISTER_COUNT];
        registers[VSYNC] = (byte) vsyncRegister;
        registers[VBLANK] = (byte) vblankRegister;
        registers[WSYNC] = (byte) (wsync ? 1 : 0);
        registers[NUSIZ0] = (byte) nusiz0;
        registers[NUSIZ1] = (byte) nusiz1;
        registers[COLUP0] = (byte) coluP0;
        registers[COLUP1] = (byte) coluP1;
        registers[COLUPF] = (byte) coluPf;
        registers[COLUBK] = (byte) coluBk;
        registers[CTRLPF] = (byte) ctrlPf;
        registers[REFP0] = (byte) refp0;
        registers[REFP1] = (byte) refp1;
        registers[PF0] = (byte) pf0;
        registers[PF1] = (byte) pf1;
        registers[PF2] = (byte) pf2;
        registers[RESP0] = (byte) player0X;
        registers[RESP1] = (byte) player1X;
        registers[RESM0] = (byte) missile0X;
        registers[RESM1] = (byte) missile1X;
        registers[RESBL] = (byte) ballX;
        registers[AUDC0] = (byte) audio.registerValue(AUDC0);
        registers[AUDC1] = (byte) audio.registerValue(AUDC1);
        registers[AUDF0] = (byte) audio.registerValue(AUDF0);
        registers[AUDF1] = (byte) audio.registerValue(AUDF1);
        registers[AUDV0] = (byte) audio.registerValue(AUDV0);
        registers[AUDV1] = (byte) audio.registerValue(AUDV1);
        registers[GRP0] = (byte) grp0New;
        registers[GRP1] = (byte) grp1New;
        registers[ENAM0] = (byte) enam0;
        registers[ENAM1] = (byte) enam1;
        registers[ENABL] = (byte) enablNew;
        registers[HMP0] = (byte) hmp0;
        registers[HMP1] = (byte) hmp1;
        registers[HMM0] = (byte) hmm0;
        registers[HMM1] = (byte) hmm1;
        registers[HMBL] = (byte) hmbl;
        registers[VDELP0] = (byte) vdelp0;
        registers[VDELP1] = (byte) vdelp1;
        registers[VDELBL] = (byte) vdelbl;
        registers[RESMP0] = (byte) resmp0;
        registers[RESMP1] = (byte) resmp1;
        registers[CXM0P] = (byte) cxM0P;
        registers[CXM1P] = (byte) cxM1P;
        registers[CXP0FB] = (byte) cxP0FB;
        registers[CXP1FB] = (byte) cxP1FB;
        registers[CXM0FB] = (byte) cxM0FB;
        registers[CXM1FB] = (byte) cxM1FB;
        registers[CXBLPF] = (byte) cxBLPF;
        registers[CXPPMM] = (byte) cxPPMM;
        registers[INPT0] = (byte) 0x80;
        registers[INPT1] = (byte) 0x80;
        registers[INPT2] = (byte) 0x80;
        registers[INPT3] = (byte) 0x80;
        registers[INPT4] = (byte) fireInput(0);
        registers[INPT5] = (byte) fireInput(1);
        return registers;
    }

    @Override
    public void tick(long colorClocks) {
        if (colorClocks < 0) {
            throw new IllegalArgumentException("colorClocks must be >= 0");
        }
        for (long i = 0; i < colorClocks; i++) {
            if (scanline == 0 && colorClock == 0) {
                clearFramebuffer();
            }
            colorClock++;
            audio.tick(1);
            if (colorClock >= Atari2600Constants.COLOR_CLOCKS_PER_SCANLINE) {
                colorClock = 0;
                scanline++;
                wsync = false;
                if (scanline >= Atari2600Constants.SCANLINES_PER_FRAME) {
                    scanline = 0;
                    frameCount++;
                    audio.flush();
                    emitFrame();
                }
            } else {
                renderPixel();
            }
        }
    }

    private void renderPixel() {
        int x = colorClock - Atari2600Constants.VISIBLE_COLOR_CLOCK_START;
        if (x < 0 || x >= Atari2600Constants.VISIBLE_WIDTH) {
            return;
        }
        if (scanline < Atari2600Constants.VISIBLE_SCANLINE_START
                || scanline >= Atari2600Constants.VISIBLE_SCANLINE_START
                + Atari2600Constants.VISIBLE_HEIGHT) {
            return;
        }

        int y = scanline - Atari2600Constants.VISIBLE_SCANLINE_START;
        int colorIndex;
        if (vblank()) {
            colorIndex = 0;
        } else {
            int playfield = playfieldBit(x);
            boolean player0 = playerPixel(x, player0X, displayGrp0(), nusiz0, refp0);
            boolean player1 = playerPixel(x, player1X, displayGrp1(), nusiz1, refp1);
            boolean missile0 = missilePixel(
                    x, resmp0, player0X, missile0X, nusiz0, enam0);
            boolean missile1 = missilePixel(
                    x, resmp1, player1X, missile1X, nusiz1, enam1);
            boolean ball = ballPixel(x);
            updateCollisions(playfield != 0, player0, player1, missile0, missile1, ball);
            int background = colorIndex(coluBk);
            int playfieldColor = (ctrlPf & 0x06) == 0x02
                    ? colorIndex(x < Atari2600Constants.VISIBLE_WIDTH / 2 ? coluP0 : coluP1)
                    : colorIndex(coluPf);

            if ((ctrlPf & 0x06) == 0x04) {
                // Приоритет playfield: PF/BL -> P0/M0 -> P1/M1 -> BK.
                if (playfield != 0) {
                    colorIndex = colorIndex(coluPf);
                } else if (ball) {
                    colorIndex = colorIndex(coluPf);
                } else if (player0) {
                    colorIndex = colorIndex(coluP0);
                } else if (missile0) {
                    colorIndex = colorIndex(coluP0);
                } else if (player1) {
                    colorIndex = colorIndex(coluP1);
                } else if (missile1) {
                    colorIndex = colorIndex(coluP1);
                } else {
                    colorIndex = background;
                }
            } else if ((ctrlPf & 0x06) == 0x02) {
                // Score mode: P0/M0 -> PF -> P1/M1 -> BL -> BK.
                if (player0) {
                    colorIndex = colorIndex(coluP0);
                } else if (missile0) {
                    colorIndex = colorIndex(coluP0);
                } else if (playfield != 0) {
                    colorIndex = playfieldColor;
                } else if (player1) {
                    colorIndex = colorIndex(coluP1);
                } else if (missile1) {
                    colorIndex = colorIndex(coluP1);
                } else if (ball) {
                    colorIndex = colorIndex(coluPf);
                } else {
                    colorIndex = background;
                }
            } else {
                // Обычный приоритет: P0/M0 -> P1/M1 -> PF/BL -> BK.
                if (player0) {
                    colorIndex = colorIndex(coluP0);
                } else if (missile0) {
                    colorIndex = colorIndex(coluP0);
                } else if (player1) {
                    colorIndex = colorIndex(coluP1);
                } else if (missile1) {
                    colorIndex = colorIndex(coluP1);
                } else if (playfield != 0) {
                    colorIndex = playfieldColor;
                } else if (ball) {
                    colorIndex = colorIndex(coluPf);
                } else {
                    colorIndex = background;
                }
            }
        }

        int offset = y * Atari2600Constants.VISIBLE_WIDTH + x;
        pixels[offset] = TiaPalette.ARGB[colorIndex];
        pixelIndices[offset] = (byte) colorIndex;
    }

    private static int colorIndex(int colorCode) {
        return (colorCode >> 1) & 0x7F;
    }

    private int currentVisibleX() {
        if (colorClock < Atari2600Constants.VISIBLE_COLOR_CLOCK_START) {
            return 0;
        }
        return Math.floorMod(
                colorClock - Atari2600Constants.VISIBLE_COLOR_CLOCK_START,
                Atari2600Constants.VISIBLE_WIDTH);
    }

    private int displayGrp0() {
        return (vdelp0 & 0x01) != 0 ? grp0Old : grp0New;
    }

    private int displayGrp1() {
        return (vdelp1 & 0x01) != 0 ? grp1Old : grp1New;
    }

    private boolean playerPixel(
            int x, int position, int grp, int nusiz, int refp) {
        int nusizIndex = nusiz & 0x07;
        int size = nusizIndex == 5 ? 2 : nusizIndex == 7 ? 4 : 1;
        int width = 8 * size;
        boolean reflected = (refp & 0x08) != 0;
        for (int offset : COPY_OFFSETS[nusizIndex]) {
            int delta = Math.floorMod(
                    x - position - offset, Atari2600Constants.VISIBLE_WIDTH);
            if (delta < width) {
                int bitIndex = delta / size;
                int bit = (grp >> (reflected ? bitIndex : 7 - bitIndex)) & 1;
                if (bit != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean missilePixel(
            int x, int resmp, int playerPosition, int missilePosition,
            int nusiz, int enam) {
        if ((enam & 0x02) == 0) {
            return false;
        }
        int position = (resmp & 0x02) != 0
                ? playerPosition + 4
                : missilePosition;
        int width = 1 << ((nusiz >> 4) & 0x03);
        int nusizIndex = nusiz & 0x07;
        for (int offset : COPY_OFFSETS[nusizIndex]) {
            int delta = Math.floorMod(
                    x - position - offset, Atari2600Constants.VISIBLE_WIDTH);
            if (delta < width) {
                return true;
            }
        }
        return false;
    }

    private boolean ballPixel(int x) {
        int enable = (vdelbl & 0x01) != 0 ? enablOld : enablNew;
        if ((enable & 0x02) == 0) {
            return false;
        }
        int width = 1 << ((ctrlPf >> 4) & 0x03);
        return Math.floorMod(x - ballX, Atari2600Constants.VISIBLE_WIDTH) < width;
    }

    private void applyHmove() {
        player0X = applyMotion(player0X, hmp0);
        player1X = applyMotion(player1X, hmp1);
        missile0X = applyMotion(missile0X, hmm0);
        missile1X = applyMotion(missile1X, hmm1);
        ballX = applyMotion(ballX, hmbl);
    }

    /**
     * Упрощённая модель HMOVE: старший nibble — знаковое смещение -8..+7,
     * которое применяется к позиции сразу. Пиксельно-точный HMOVE-comb
     * и восьмипиксельная база аппаратного HMOVE здесь не моделируются.
     */
    private static int applyMotion(int position, int register) {
        int nibble = (register >> 4) & 0x0F;
        int offset = nibble < 8 ? nibble : nibble - 16;
        return Math.floorMod(position + offset, Atari2600Constants.VISIBLE_WIDTH);
    }

    private void clearHmove() {
        hmp0 = 0;
        hmp1 = 0;
        hmm0 = 0;
        hmm1 = 0;
        hmbl = 0;
    }

    private void clearCollisions() {
        cxM0P = 0;
        cxM1P = 0;
        cxP0FB = 0;
        cxP1FB = 0;
        cxM0FB = 0;
        cxM1FB = 0;
        cxBLPF = 0;
        cxPPMM = 0;
    }

    private void updateCollisions(
            boolean playfield,
            boolean player0,
            boolean player1,
            boolean missile0,
            boolean missile1,
            boolean ball) {
        if (player0 && player1) {
            cxPPMM |= 0x80;
        }
        if (missile0 && missile1) {
            cxPPMM |= 0x40;
        }
        if (player0 && playfield) {
            cxP0FB |= 0x80;
        }
        if (player0 && ball) {
            cxP0FB |= 0x40;
        }
        if (player1 && playfield) {
            cxP1FB |= 0x80;
        }
        if (player1 && ball) {
            cxP1FB |= 0x40;
        }
        if (missile0 && playfield) {
            cxM0FB |= 0x80;
        }
        if (missile0 && ball) {
            cxM0FB |= 0x40;
        }
        if (missile1 && playfield) {
            cxM1FB |= 0x80;
        }
        if (missile1 && ball) {
            cxM1FB |= 0x40;
        }
        if (ball && playfield) {
            cxBLPF |= 0x80;
        }
        if (missile0 && player1) {
            cxM0P |= 0x80;
        }
        if (missile0 && player0) {
            cxM0P |= 0x40;
        }
        if (missile1 && player0) {
            cxM1P |= 0x80;
        }
        if (missile1 && player1) {
            cxM1P |= 0x40;
        }
    }

    /**
     * Возвращает бит 20-битного playfield для пикселя x (0..159).
     */
    private int playfieldBit(int x) {
        int position;
        if (x < Atari2600Constants.VISIBLE_WIDTH / 2) {
            position = x >> 2;
        } else if ((ctrlPf & 0x01) != 0) {
            position = 19 - ((x - Atari2600Constants.VISIBLE_WIDTH / 2) >> 2);
        } else {
            position = (x - Atari2600Constants.VISIBLE_WIDTH / 2) >> 2;
        }

        if (position < 4) {
            return (pf0 >> (4 + position)) & 1;
        }
        if (position < 12) {
            return (pf1 >> (11 - position)) & 1;
        }
        return (pf2 >> (position - 12)) & 1;
    }

    /**
     * @return текущее значение CTRLPF
     */
    public int controlPf() {
        return ctrlPf;
    }

    private void emitFrame() {
        if (sink != null) {
            sink.frameReady(new VideoFrame(
                    Atari2600Constants.VISIBLE_WIDTH,
                    Atari2600Constants.VISIBLE_HEIGHT,
                    pixels.clone()));
        }
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeByte(vsyncRegister);
        writer.writeByte(vblankRegister);
        writer.writeByte(coluP0);
        writer.writeByte(coluP1);
        writer.writeByte(coluPf);
        writer.writeByte(coluBk);
        writer.writeByte(ctrlPf);
        writer.writeByte(pf0);
        writer.writeByte(pf1);
        writer.writeByte(pf2);
        writer.writeByte(nusiz0);
        writer.writeByte(nusiz1);
        writer.writeByte(refp0);
        writer.writeByte(refp1);
        writer.writeByte(grp0Old);
        writer.writeByte(grp0New);
        writer.writeByte(grp1Old);
        writer.writeByte(grp1New);
        writer.writeByte(enam0);
        writer.writeByte(enam1);
        writer.writeByte(enablOld);
        writer.writeByte(enablNew);
        writer.writeByte(vdelp0);
        writer.writeByte(vdelp1);
        writer.writeByte(vdelbl);
        writer.writeByte(resmp0);
        writer.writeByte(resmp1);
        writer.writeByte(hmp0);
        writer.writeByte(hmp1);
        writer.writeByte(hmm0);
        writer.writeByte(hmm1);
        writer.writeByte(hmbl);
        writer.writeByte(cxM0P);
        writer.writeByte(cxM1P);
        writer.writeByte(cxP0FB);
        writer.writeByte(cxP1FB);
        writer.writeByte(cxM0FB);
        writer.writeByte(cxM1FB);
        writer.writeByte(cxBLPF);
        writer.writeByte(cxPPMM);
        writer.writeShort(player0X);
        writer.writeShort(player1X);
        writer.writeShort(missile0X);
        writer.writeShort(missile1X);
        writer.writeShort(ballX);
        writer.writeShort(scanline);
        writer.writeShort(colorClock);
        writer.writeByte(wsync ? 1 : 0);
        writer.writeLong(frameCount);
        writer.writeBytes(argbToBytes(pixels));
        writer.writeBytes(pixelIndices.clone());
        audio.save(writer);
    }

    @Override
    public void restore(StateReader reader) {
        vsyncRegister = reader.readByte();
        vblankRegister = reader.readByte();
        coluP0 = reader.readByte();
        coluP1 = reader.readByte();
        coluPf = reader.readByte();
        coluBk = reader.readByte();
        ctrlPf = reader.readByte();
        pf0 = reader.readByte();
        pf1 = reader.readByte();
        pf2 = reader.readByte();
        nusiz0 = reader.readByte();
        nusiz1 = reader.readByte();
        refp0 = reader.readByte();
        refp1 = reader.readByte();
        grp0Old = reader.readByte();
        grp0New = reader.readByte();
        grp1Old = reader.readByte();
        grp1New = reader.readByte();
        enam0 = reader.readByte();
        enam1 = reader.readByte();
        enablOld = reader.readByte();
        enablNew = reader.readByte();
        vdelp0 = reader.readByte();
        vdelp1 = reader.readByte();
        vdelbl = reader.readByte();
        resmp0 = reader.readByte();
        resmp1 = reader.readByte();
        hmp0 = reader.readByte();
        hmp1 = reader.readByte();
        hmm0 = reader.readByte();
        hmm1 = reader.readByte();
        hmbl = reader.readByte();
        cxM0P = reader.readByte();
        cxM1P = reader.readByte();
        cxP0FB = reader.readByte();
        cxP1FB = reader.readByte();
        cxM0FB = reader.readByte();
        cxM1FB = reader.readByte();
        cxBLPF = reader.readByte();
        cxPPMM = reader.readByte();
        player0X = reader.readShort();
        player1X = reader.readShort();
        missile0X = reader.readShort();
        missile1X = reader.readShort();
        ballX = reader.readShort();
        scanline = reader.readShort();
        colorClock = reader.readShort();
        wsync = reader.readByte() != 0;
        frameCount = reader.readLong();
        restoreFramebuffer(reader.readBytes(), reader.readBytes());
        audio.restore(reader);
    }

    private static byte[] argbToBytes(int[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.asIntBuffer().put(values);
        return buffer.array();
    }

    private void restoreFramebuffer(byte[] argbBytes, byte[] indexBytes) {
        if (argbBytes.length != pixels.length * Integer.BYTES) {
            throw new IllegalArgumentException(
                    "invalid TIA framebuffer length: " + argbBytes.length);
        }
        if (indexBytes.length != pixelIndices.length) {
            throw new IllegalArgumentException(
                    "invalid TIA palette-index framebuffer length: " + indexBytes.length);
        }
        ByteBuffer.wrap(argbBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asIntBuffer()
                .get(pixels);
        System.arraycopy(indexBytes, 0, pixelIndices, 0, pixelIndices.length);
    }
}
