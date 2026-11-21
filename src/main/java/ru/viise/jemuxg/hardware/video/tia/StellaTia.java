package ru.viise.jemuxg.hardware.video.tia;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Bus;

/**
 * Ядро TIA, портированное из проекта Stella.
 *
 * <p>Тик = один цветовой такт (228 на строку, 262 строки NTSC).
 * Регистровые записи проходят через очередь задержек и применяются
 * в начале соответствующего такта.</p>
 *
 * <p><b>Источник:</b> реализация основана на исходном коде эмулятора
 * <a href="https://github.com/stella-emu/stella">Stella</a>
 * (файлы {@code TIA.cxx}, {@code Player.*}, {@code Missile.*},
 * {@code Ball.*}, {@code Playfield.*}). Особая благодарность авторам
 * и участникам проекта Stella.</p>
 */
public final class StellaTia {

    public static final int H_CLOCKS = 228;
    public static final int H_PIXEL = 160;
    public static final int H_BLANK = 68;
    public static final int SCANLINES = 262;
    public static final int VISIBLE_HEIGHT = 192;
    public static final int VISIBLE_SCANLINE_START = 40;

    public static final int VSYNC = 0x00;
    public static final int VBLANK = 0x01;
    public static final int WSYNC = 0x02;
    public static final int NUSIZ0 = 0x04;
    public static final int NUSIZ1 = 0x05;
    public static final int COLUP0 = 0x06;
    public static final int COLUP1 = 0x07;
    public static final int COLUPF = 0x08;
    public static final int COLUBK = 0x09;
    public static final int CTRLPF = 0x0A;
    public static final int REFP0 = 0x0B;
    public static final int REFP1 = 0x0C;
    public static final int PF0 = 0x0D;
    public static final int PF1 = 0x0E;
    public static final int PF2 = 0x0F;
    public static final int GRP0 = 0x1B;
    public static final int GRP1 = 0x1C;
    public static final int RESP0 = 0x10;
    public static final int RESP1 = 0x11;
    public static final int RESM0 = 0x12;
    public static final int RESM1 = 0x13;
    public static final int RESBL = 0x14;
    public static final int ENAM0 = 0x1D;
    public static final int ENAM1 = 0x1E;
    public static final int ENABL = 0x1F;
    public static final int HMP0 = 0x20;
    public static final int HMP1 = 0x21;
    public static final int HMM0 = 0x22;
    public static final int HMM1 = 0x23;
    public static final int HMBL = 0x24;
    public static final int VDELP0 = 0x25;
    public static final int VDELP1 = 0x26;
    public static final int VDELBL = 0x27;
    public static final int RESMP0 = 0x28;
    public static final int RESMP1 = 0x29;
    public static final int HMOVE = 0x2A;
    public static final int HMCLR = 0x2B;
    public static final int CXCLR = 0x2C;
    public static final int CXM0P = 0x30;
    public static final int CXM1P = 0x31;
    public static final int CXP0FB = 0x32;
    public static final int CXP1FB = 0x33;
    public static final int CXM0FB = 0x34;
    public static final int CXM1FB = 0x35;
    public static final int CXBLPF = 0x36;
    public static final int CXPPMM = 0x37;
    public static final int INPT0 = 0x08;
    public static final int INPT1 = 0x09;
    public static final int INPT2 = 0x0A;
    public static final int INPT3 = 0x0B;
    public static final int INPT4 = 0x0C;
    public static final int INPT5 = 0x0D;
    public static final int AUDC0 = 0x15;
    public static final int AUDC1 = 0x16;
    public static final int AUDF0 = 0x17;
    public static final int AUDF1 = 0x18;
    public static final int AUDV0 = 0x19;
    public static final int AUDV1 = 0x1A;

    private static final int SHUFFLE_P0 = 0xF0;
    private static final int SHUFFLE_P1 = 0xF1;
    private static final int SHUFFLE_BL = 0xF2;

    private final StellaTiaPlayer player0 = new StellaTiaPlayer();
    private final StellaTiaPlayer player1 = new StellaTiaPlayer();
    private final StellaTiaMissile missile0 = new StellaTiaMissile();
    private final StellaTiaMissile missile1 = new StellaTiaMissile();
    private final StellaTiaBall ball = new StellaTiaBall();
    private final StellaTiaPlayfield playfield = new StellaTiaPlayfield();
    private final List<Write> queue = new ArrayList<>();
    private final Bus registers = new Bus() {
        @Override
        public int read(int address) {
            return readRegister(address & 0x3F);
        }

        @Override
        public void write(int address, int value) {
            StellaTia.this.write(address, value);
        }
    };

    private long colorClock;
    private int hctr;
    private int scanline;
    private long frame;
    private boolean hblank = true;
    private boolean vsync;
    private boolean vblank;
    private boolean wsync;
    private int cxM0P;
    private int cxM1P;
    private int cxP0FB;
    private int cxP1FB;
    private int cxM0FB;
    private int cxM1FB;
    private int cxBLPF;
    private int cxPPMM;
    private long sequence;
    private int backgroundColor;
    private final byte[] framebuffer = new byte[H_PIXEL * VISIBLE_HEIGHT];
    private final int[] framebufferArgb = new int[H_PIXEL * VISIBLE_HEIGHT];
    private boolean clearPending = true;
    private int movementClock;
    private boolean movementInProgress;
    private boolean extendedHblank;
    private int visibleStartScanline = VISIBLE_SCANLINE_START;

    public StellaTia() {
        reset();
    }

    public void reset() {
        player0.reset();
        player1.reset();
        missile0.reset();
        missile1.reset();
        ball.reset();
        playfield.reset();
        queue.clear();
        colorClock = 0;
        hctr = 0;
        scanline = 0;
        frame = 0;
        hblank = true;
        vsync = false;
        vblank = false;
        wsync = false;
        clearCollisions();
        sequence = 0;
        backgroundColor = 0;
        movementClock = 0;
        movementInProgress = false;
        extendedHblank = false;
        visibleStartScanline = VISIBLE_SCANLINE_START;
        clearFrame();
        clearPending = true;
    }

    public Bus registers() {
        return registers;
    }

    public void catchUpTo(long targetClock) {
        if (targetClock < colorClock) {
            throw new IllegalArgumentException("cannot go backwards");
        }
        if (clearPending && colorClock < targetClock) {
            clearFrame();
            clearPending = false;
        }
        while (colorClock < targetClock) {
            if (clearPending && scanline == 0 && hctr == 0) {
                clearFrame();
                clearPending = false;
            }
            fireDueWrites();
            tickMovement();
            if (!hblank) {
                playfield.tick(hctr - H_BLANK);
                player0.tick();
                player1.tick();
                missile0.tick(hctr, player0);
                missile1.tick(hctr, player1);
                ball.tick();
                renderPixel(hctr - H_BLANK);
            } else {
                tickHblank();
            }
            colorClock++;
            if (++hctr >= H_CLOCKS) {
                nextLine();
            }
        }
    }

    private void tickMovement() {
        if (!movementInProgress) {
            return;
        }
        if ((hctr & 0x03) != 0) {
            return;
        }
        int movementCounter = movementClock > 15 ? 0 : movementClock;
        boolean hblankNow = hblank;
        missile0.movementTick(movementCounter, hctr, hblankNow);
        missile1.movementTick(movementCounter, hctr, hblankNow);
        player0.movementTick(movementCounter, hctr, hblankNow);
        player1.movementTick(movementCounter, hctr, hblankNow);
        ball.movementTick(movementCounter, hctr, hblankNow);
        movementInProgress = missile0.isMoving()
                || missile1.isMoving()
                || player0.isMoving()
                || player1.isMoving()
                || ball.isMoving();
        movementClock++;
    }

    private void tickHblank() {
        player0.tickInHblank();
        player1.tickInHblank();
        missile0.tickInHblank();
        missile1.tickInHblank();
        ball.tickInHblank();

        if (hctr == 0) {
            extendedHblank = false;
        } else if (hctr == H_BLANK - 1) {
            if (!extendedHblank) {
                hblank = false;
            }
        } else if (hctr == H_BLANK + 7) {
            if (extendedHblank) {
                hblank = false;
            }
        }

        if (extendedHblank && hctr >= H_BLANK) {
            playfield.tick(hctr - H_BLANK);
        }
    }

    public void tick(int clocks) {
        if (clocks < 0) {
            throw new IllegalArgumentException("clocks must be >= 0");
        }
        catchUpTo(colorClock + clocks);
    }

    public void write(int address, int value) {
        int register = address & 0x3F;
        int delay = delayFor(register);
        if (delay > 0) {
            queue.add(new Write(colorClock + delay, sequence++, register, value & 0xFF));
            return;
        }
        apply(register, value & 0xFF);
    }

    private int delayFor(int register) {
        return switch (register) {
            case GRP0, GRP1, VBLANK, REFP0, REFP1, VDELP0, VDELP1,
                    ENAM0, ENAM1, ENABL -> 1;
            case PF0, PF1, PF2, HMP0, HMP1, HMM0, HMM1, HMBL, HMCLR -> 2;
            case HMOVE -> 6;
            default -> 0;
        };
    }

    private void fireDueWrites() {
        if (queue.isEmpty()) {
            return;
        }
        List<Write> due = new ArrayList<>();
        queue.removeIf(w -> {
            if (w.fireClock <= colorClock) {
                due.add(w);
                return true;
            }
            return false;
        });
        due.sort((a, b) -> Long.compare(a.sequence, b.sequence));
        for (Write w : due) {
            apply(w.address, w.value);
        }
    }

    private void apply(int register, int value) {
        switch (register) {
            case VSYNC -> vsync = (value & 0x02) != 0;
            case VBLANK -> {
                boolean oldBlank = vblank;
                vblank = (value & 0x02) != 0;
                if (oldBlank && !vblank) {
                    visibleStartScanline = scanline;
                }
            }
            case WSYNC -> wsync = true;
            case NUSIZ0 -> {
                player0.setNusiz(value);
                missile0.nusiz(value);
            }
            case NUSIZ1 -> {
                player1.setNusiz(value);
                missile1.nusiz(value);
            }
            case COLUP0 -> {
                playfield.setColorP0(value);
                missile0.setColor(value);
                player0.setColor(value);
            }
            case COLUP1 -> {
                playfield.setColorP1(value);
                missile1.setColor(value);
                player1.setColor(value);
            }
            case COLUPF -> {
                playfield.setColor(value);
                ball.setColor(value);
            }
            case COLUBK -> backgroundColor = value & 0xFE;
            case CTRLPF -> {
                playfield.ctrlpf(value);
                ball.ctrlpf(value);
            }
            case PF0 -> playfield.pf0(value);
            case PF1 -> playfield.pf1(value);
            case PF2 -> playfield.pf2(value);
            case REFP0 -> player0.setRefp(value);
            case REFP1 -> player1.setRefp(value);
            case VDELP0 -> player0.setVdel(value);
            case VDELP1 -> player1.setVdel(value);
            case GRP0 -> {
                player0.grp(value);
                queue.add(new Write(colorClock + 1, sequence++, SHUFFLE_P1, 0));
            }
            case GRP1 -> {
                player1.grp(value);
                queue.add(new Write(colorClock + 1, sequence++, SHUFFLE_P0, 0));
                queue.add(new Write(colorClock + 1, sequence++, SHUFFLE_BL, 0));
            }
            case SHUFFLE_P0 -> player0.shufflePatterns();
            case SHUFFLE_P1 -> player1.shufflePatterns();
            case SHUFFLE_BL -> ball.shuffleStatus();
            case RESP0 -> player0.resp(respTarget(), false);
            case RESP1 -> player1.resp(respTarget(), false);
            case RESM0 -> missile0.resm(respTarget(), hblank, false);
            case RESM1 -> missile1.resm(respTarget(), hblank, false);
            case RESBL -> ball.resbl(respTarget(), false);
            case RESMP0 -> missile0.resmp(value);
            case RESMP1 -> missile1.resmp(value);
            case ENAM0 -> missile0.enam(value);
            case ENAM1 -> missile1.enam(value);
            case ENABL -> ball.enabl(value);
            case VDELBL -> ball.vdelbl(value);
            case HMP0 -> player0.hmp(value);
            case HMP1 -> player1.hmp(value);
            case HMM0 -> missile0.hmm(value);
            case HMM1 -> missile1.hmm(value);
            case HMBL -> ball.hmbl(value);
            case HMOVE -> {
                movementClock = 0;
                movementInProgress = true;
                if (!extendedHblank) {
                    clearHmoveComb();
                    extendedHblank = true;
                }
                player0.startMovement();
                player1.startMovement();
                missile0.startMovement();
                missile1.startMovement();
                ball.startMovement();
            }
            case HMCLR -> {
                player0.hmp(0);
                player1.hmp(0);
                missile0.hmm(0);
                missile1.hmm(0);
                ball.hmbl(0);
            }
            case CXCLR -> clearCollisions();
            default -> {
            }
        }
    }

    private int readRegister(int register) {
        return switch (register) {
            case CXM0P, 0x00 -> cxM0P;
            case CXM1P, 0x01 -> cxM1P;
            case CXP0FB, 0x02 -> cxP0FB;
            case CXP1FB, 0x03 -> cxP1FB;
            case CXM0FB, 0x04 -> cxM0FB;
            case CXM1FB, 0x05 -> cxM1FB;
            case CXBLPF, 0x06 -> cxBLPF;
            case CXPPMM, 0x07 -> cxPPMM;
            case 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D,
                 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D -> 0x80;
            default -> 0;
        };
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

    private int respTarget() {
        if (!hblank) {
            return 157;
        }
        return hctr >= 73 ? 158 : 159;
    }

    private void renderPixel(int x) {
        if (x < 0 || x >= H_PIXEL) {
            return;
        }
        int y = scanline - visibleStartScanline;
        if (y < 0 || y >= VISIBLE_HEIGHT) {
            return;
        }

        int color = 0;
        if (!vblank) {
            updateCollisions(
                    playfield.isOn(),
                    player0.on(),
                    player1.on(),
                    missile0.isOn(),
                    missile1.isOn(),
                    ball.isOn());
            switch (playfield.priority()) {
                case StellaTiaPlayfield.PRIORITY_PFP -> {
                    if (playfield.isOn()) {
                        color = playfield.color();
                    } else if (ball.isOn()) {
                        color = ball.color();
                    } else if (player0.on()) {
                        color = player0.color();
                    } else if (missile0.isOn()) {
                        color = missile0.color();
                    } else if (player1.on()) {
                        color = player1.color();
                    } else if (missile1.isOn()) {
                        color = missile1.color();
                    } else {
                        color = backgroundColor;
                    }
                }
                case StellaTiaPlayfield.PRIORITY_SCORE -> {
                    if (player0.on()) {
                        color = player0.color();
                    } else if (missile0.isOn()) {
                        color = missile0.color();
                    } else if (playfield.isOn()) {
                        color = playfield.color();
                    } else if (player1.on()) {
                        color = player1.color();
                    } else if (missile1.isOn()) {
                        color = missile1.color();
                    } else if (ball.isOn()) {
                        color = ball.color();
                    } else {
                        color = backgroundColor;
                    }
                }
                default -> {
                    if (player0.on()) {
                        color = player0.color();
                    } else if (missile0.isOn()) {
                        color = missile0.color();
                    } else if (player1.on()) {
                        color = player1.color();
                    } else if (missile1.isOn()) {
                        color = missile1.color();
                    } else if (playfield.isOn()) {
                        color = playfield.color();
                    } else if (ball.isOn()) {
                        color = ball.color();
                    } else {
                        color = backgroundColor;
                    }
                }
            }
        }

        int index = colorIndex(color);
        int offset = y * H_PIXEL + x;
        framebuffer[offset] = (byte) index;
        framebufferArgb[offset] = TiaPalette.ARGB[index];
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

    private static int colorIndex(int color) {
        return (color >> 1) & 0x7F;
    }

    private void clearFrame() {
        Arrays.fill(framebuffer, (byte) 0);
        Arrays.fill(framebufferArgb, TiaPalette.ARGB[0]);
    }

    /**
     * HMOVE shortens the visible part of the line by eight pixels. On real
     * hardware the comb wipes the first eight pixels of the current row.
     */
    private void clearHmoveComb() {
        int y = scanline - visibleStartScanline;
        if (!hblank || y < 0 || y >= VISIBLE_HEIGHT) {
            return;
        }
        int offset = y * H_PIXEL;
        for (int x = 0; x < 8; x++) {
            framebuffer[offset + x] = 0;
            framebufferArgb[offset + x] = TiaPalette.ARGB[0];
        }
    }

    private void nextLine() {
        hctr = 0;
        hblank = true;
        wsync = false;
        playfield.nextLine();
        missile0.nextLine();
        missile1.nextLine();
        ball.nextLine();
        scanline++;
        if (scanline >= SCANLINES) {
            scanline = 0;
            frame++;
            clearPending = true;
        }
    }

    public int width() {
        return H_PIXEL;
    }

    public int height() {
        return VISIBLE_HEIGHT;
    }

    public byte[] framebufferIndices() {
        return framebuffer.clone();
    }

    public int[] framebufferArgb() {
        return framebufferArgb.clone();
    }

    public long colorClock() {
        return colorClock;
    }

    public int hctr() {
        return hctr;
    }

    public int scanline() {
        return scanline;
    }

    public long frame() {
        return frame;
    }

    public boolean hblank() {
        return hblank;
    }

    public boolean vblank() {
        return vblank;
    }

    public boolean vsync() {
        return vsync;
    }

    public boolean cpuHalted() {
        return wsync;
    }

    public boolean movementInProgress() {
        return movementInProgress;
    }

    public boolean extendedHblank() {
        return extendedHblank;
    }

    public int movementClock() {
        return movementClock;
    }

    public StellaTiaPlayer player0() {
        return player0;
    }

    public StellaTiaPlayer player1() {
        return player1;
    }

    public StellaTiaMissile missile0() {
        return missile0;
    }

    public StellaTiaMissile missile1() {
        return missile1;
    }

    public StellaTiaBall ball() {
        return ball;
    }

    public StellaTiaPlayfield playfield() {
        return playfield;
    }

    /**
     * Снимок 64 регистров TIA для отладочного дампа.
     */
    public byte[] registerSnapshot() {
        byte[] registers = new byte[64];
        registers[VSYNC] = (byte) (vsync ? 0x02 : 0);
        registers[VBLANK] = (byte) (vblank ? 0x02 : 0);
        registers[WSYNC] = (byte) (wsync ? 1 : 0);
        registers[NUSIZ0] = (byte) player0.nusiz();
        registers[NUSIZ1] = (byte) player1.nusiz();
        registers[COLUP0] = (byte) player0.color();
        registers[COLUP1] = (byte) player1.color();
        registers[COLUPF] = (byte) ball.color();
        registers[COLUBK] = (byte) backgroundColor;
        registers[CTRLPF] = (byte) ((playfield.reflected() ? 0x01 : 0)
                | switch (playfield.priority()) {
                    case StellaTiaPlayfield.PRIORITY_SCORE -> 0x02;
                    case StellaTiaPlayfield.PRIORITY_PFP -> 0x04;
                    default -> 0;
                });
        registers[REFP0] = (byte) (player0.reflected() ? 0x08 : 0);
        registers[REFP1] = (byte) (player1.reflected() ? 0x08 : 0);
        registers[PF0] = (byte) (playfield.pf0() << 4);
        registers[PF1] = (byte) playfield.pf1();
        registers[PF2] = (byte) playfield.pf2();
        registers[RESP0] = (byte) player0.position();
        registers[RESP1] = (byte) player1.position();
        registers[RESM0] = (byte) missile0.position();
        registers[RESM1] = (byte) missile1.position();
        registers[RESBL] = (byte) ball.position();
        registers[GRP0] = (byte) player0.patternNew();
        registers[GRP1] = (byte) player1.patternNew();
        registers[ENAM0] = (byte) (missile0.isEnabled() ? 0x02 : 0);
        registers[ENAM1] = (byte) (missile1.isEnabled() ? 0x02 : 0);
        registers[ENABL] = (byte) (ball.isEnabled() ? 0x02 : 0);
        registers[HMP0] = (byte) (player0.hmpClocks() << 4);
        registers[HMP1] = (byte) (player1.hmpClocks() << 4);
        registers[HMM0] = (byte) (missile0.hmmClocks() << 4);
        registers[HMM1] = (byte) (missile1.hmmClocks() << 4);
        registers[HMBL] = (byte) (ball.hmmClocks() << 4);
        registers[VDELP0] = (byte) (player0.delaying() ? 1 : 0);
        registers[VDELP1] = (byte) (player1.delaying() ? 1 : 0);
        registers[CXM0P] = (byte) cxM0P;
        registers[CXM1P] = (byte) cxM1P;
        registers[CXP0FB] = (byte) cxP0FB;
        registers[CXP1FB] = (byte) cxP1FB;
        registers[CXM0FB] = (byte) cxM0FB;
        registers[CXM1FB] = (byte) cxM1FB;
        registers[CXBLPF] = (byte) cxBLPF;
        registers[CXPPMM] = (byte) cxPPMM;
        for (int i = 0x38; i <= 0x3D; i++) {
            registers[i] = (byte) 0x80;
        }
        return registers;
    }

    public void save(StateWriter writer) {
        writer.writeLong(colorClock);
        writer.writeShort(hctr);
        writer.writeShort(scanline);
        writer.writeLong(frame);
        writer.writeByte(hblank ? 1 : 0);
        writer.writeByte(vsync ? 1 : 0);
        writer.writeByte(vblank ? 1 : 0);
        writer.writeByte(wsync ? 1 : 0);
        writer.writeByte(cxM0P);
        writer.writeByte(cxM1P);
        writer.writeByte(cxP0FB);
        writer.writeByte(cxP1FB);
        writer.writeByte(cxM0FB);
        writer.writeByte(cxM1FB);
        writer.writeByte(cxBLPF);
        writer.writeByte(cxPPMM);
        writer.writeLong(sequence);
        writer.writeByte(backgroundColor);
        writer.writeByte(clearPending ? 1 : 0);
        writer.writeShort(movementClock);
        writer.writeByte(movementInProgress ? 1 : 0);
        writer.writeByte(extendedHblank ? 1 : 0);
        writer.writeShort(visibleStartScanline);
        writer.writeInt(queue.size());
        for (Write write : queue) {
            writer.writeLong(write.fireClock());
            writer.writeLong(write.sequence());
            writer.writeByte(write.address());
            writer.writeByte(write.value());
        }
        player0.save(writer);
        player1.save(writer);
        missile0.save(writer);
        missile1.save(writer);
        ball.save(writer);
        playfield.save(writer);
        writer.writeBytes(framebuffer.clone());
    }

    public void restore(StateReader reader) {
        colorClock = reader.readLong();
        hctr = reader.readShort();
        scanline = reader.readShort();
        frame = reader.readLong();
        hblank = reader.readByte() != 0;
        vsync = reader.readByte() != 0;
        vblank = reader.readByte() != 0;
        wsync = reader.readByte() != 0;
        cxM0P = reader.readByte();
        cxM1P = reader.readByte();
        cxP0FB = reader.readByte();
        cxP1FB = reader.readByte();
        cxM0FB = reader.readByte();
        cxM1FB = reader.readByte();
        cxBLPF = reader.readByte();
        cxPPMM = reader.readByte();
        sequence = reader.readLong();
        backgroundColor = reader.readByte();
        clearPending = reader.readByte() != 0;
        movementClock = reader.readShort();
        movementInProgress = reader.readByte() != 0;
        extendedHblank = reader.readByte() != 0;
        visibleStartScanline = reader.readShort();
        int queued = reader.readInt();
        queue.clear();
        for (int i = 0; i < queued; i++) {
            queue.add(new Write(
                    reader.readLong(), reader.readLong(),
                    reader.readByte(), reader.readByte()));
        }
        player0.restore(reader);
        player1.restore(reader);
        missile0.restore(reader);
        missile1.restore(reader);
        ball.restore(reader);
        playfield.restore(reader);
        byte[] indices = reader.readBytes();
        if (indices.length != framebuffer.length) {
            throw new IllegalArgumentException("invalid Stella TIA framebuffer size");
        }
        System.arraycopy(indices, 0, framebuffer, 0, framebuffer.length);
        for (int i = 0; i < framebuffer.length; i++) {
            framebufferArgb[i] = TiaPalette.ARGB[framebuffer[i] & 0x7F];
        }
    }

    private record Write(long fireClock, long sequence, int address, int value) {
    }
}
