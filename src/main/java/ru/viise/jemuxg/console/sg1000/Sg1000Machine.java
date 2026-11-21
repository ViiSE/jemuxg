package ru.viise.jemuxg.console.sg1000;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.InterruptType;
import ru.viise.jemuxg.api.hardware.SwitchPanel;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.StorageSlot;
import ru.viise.jemuxg.console.sg1000.cartridge.Sg1000Cartridge;
import ru.viise.jemuxg.console.sg1000.cartridge.Sg1000CartridgeSlot;
import ru.viise.jemuxg.console.sg1000.debug.Sg1000DebugSnapshot;
import ru.viise.jemuxg.console.sg1000.debug.Z80RegisterSnapshot;
import ru.viise.jemuxg.hardware.audio.sn76489.Sn76489;
import ru.viise.jemuxg.hardware.cpu.z80.Z80;
import ru.viise.jemuxg.hardware.cpu.z80.Z80Registers;
import ru.viise.jemuxg.hardware.video.tms9918.Tms9918;

/**
 * Сборка Sega SG-1000.
 *
 * <p>Z80 работает через {@link Sg1000Bus}: {@code $0000–$BFFF} — ROM
 * картриджа, {@code $C000–$FFFF} — 1 КБ RAM с зеркалами. Кадр
 * разбит на 262 строки по 228 тактов CPU; на строке 192 TMS9918A
 * поднимает VBlank-IRQ. Видео выдаёт TMS9918A, звук — SN76489AN.</p>
 *
 * <p>Машина хранит позицию внутри кадра, поэтому {@link #runCycles(int)}
 * может остановиться посреди кадра, а {@link #save(StateWriter)} и
 * {@link #restore(StateReader)} восстанавливают эмуляцию с этого места.</p>
 */
public final class Sg1000Machine implements Machine, Stateful {

    private static final int CONTROLLER_PORTS = 2;
    private static final int STATE_MAGIC = 0x53473130;
    private static final int STATE_VERSION = 1;

    private final Sg1000CartridgeSlot cartridgeSlot = new Sg1000CartridgeSlot();
    private final List<StorageSlot> slots = List.of(cartridgeSlot);
    private final Controller[] controllers = new Controller[CONTROLLER_PORTS];
    private final Tms9918 vdp = new Tms9918();
    private final Sn76489 psg = new Sn76489();
    private final Sg1000Bus bus = new Sg1000Bus(cartridgeSlot, vdp, psg);
    private final Z80 cpu = new Z80(bus);

    private VideoSink videoSink;
    private long frameCount;
    private int frameLine;
    private int lineCycles;

    public Sg1000Machine() {
        bus.setIrqClear(() -> cpu.clearInterrupt(InterruptType.IRQ));
        reset();
    }

    @Override
    public void reset() {
        bus.reset();
        cpu.reset();
        vdp.reset();
        psg.reset();
        frameCount = 0;
        frameLine = 0;
        lineCycles = 0;
    }

    /**
     * Выполняет один полный кадр. Если машина уже стоит посреди кадра,
     * доигрывает его до конца.
     */
    @Override
    public void runFrame() {
        if (frameLine >= Sg1000Constants.SCREEN_LINES) {
            frameLine = 0;
            lineCycles = 0;
        }
        runCycles(remainingFrameCycles());
    }

    /**
     * Выполняет заданное число тактов CPU, не переходя в следующий кадр.
     * Используется для тестов и пошагового сохранения состояния.
     */
    public void runCycles(int cpuCycles) {
        if (cpuCycles < 0) {
            throw new IllegalArgumentException(
                    "cpuCycles must be non-negative: " + cpuCycles);
        }
        if (frameLine >= Sg1000Constants.SCREEN_LINES) {
            return;
        }

        int remaining = cpuCycles;
        while (remaining > 0 && frameLine < Sg1000Constants.SCREEN_LINES) {
            if (frameLine == 0 && lineCycles == 0) {
                vdp.beginFrame();
            }
            if (lineCycles == 0) {
                vdp.beginLine(frameLine);
                if (vdp.irq()) {
                    cpu.interrupt(InterruptType.IRQ);
                }
                if (frameLine < Sg1000Constants.VISIBLE_HEIGHT) {
                    vdp.renderLine(frameLine);
                }
            }

            int slice = Math.min(
                    remaining, Sg1000Constants.CPU_CYCLES_PER_LINE - lineCycles);
            int dotsBefore = lineCycles
                    * Sg1000Constants.VDP_DOTS_PER_LINE
                    / Sg1000Constants.CPU_CYCLES_PER_LINE;
            cpu.tick(slice);
            psg.tick(slice);
            lineCycles += slice;
            int dotsAfter = lineCycles
                    * Sg1000Constants.VDP_DOTS_PER_LINE
                    / Sg1000Constants.CPU_CYCLES_PER_LINE;
            vdp.advanceDots(dotsAfter - dotsBefore);
            remaining -= slice;

            if (lineCycles == Sg1000Constants.CPU_CYCLES_PER_LINE) {
                lineCycles = 0;
                frameLine++;
                if (frameLine == Sg1000Constants.SCREEN_LINES) {
                    finishFrame();
                }
            }
        }
    }

    /**
     * Такты CPU, оставшиеся до конца текущего кадра.
     */
    public int remainingFrameCycles() {
        if (frameLine >= Sg1000Constants.SCREEN_LINES) {
            return 0;
        }
        return Sg1000Constants.CPU_CYCLES_PER_FRAME
                - frameLine * Sg1000Constants.CPU_CYCLES_PER_LINE
                - lineCycles;
    }

    private void finishFrame() {
        vdp.endFrame();
        psg.flush();
        frameCount++;
        if (videoSink != null) {
            videoSink.frameReady(new VideoFrame(
                    Tms9918.WIDTH,
                    Tms9918.HEIGHT,
                    vdp.framebufferArgb()));
        }
    }

    @Override
    public void connectVideo(VideoSink sink) {
        videoSink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public void connectAudio(AudioSink sink) {
        psg.connect(Objects.requireNonNull(sink, "sink"));
    }

    @Override
    public int audioSampleRate() {
        return Sg1000Constants.AUDIO_SAMPLE_RATE;
    }

    @Override
    public int videoWidth() {
        return Sg1000Constants.VISIBLE_WIDTH;
    }

    @Override
    public int videoHeight() {
        return Sg1000Constants.VISIBLE_HEIGHT;
    }

    @Override
    public void connectController(int port, Controller controller) {
        if (port < 0 || port >= CONTROLLER_PORTS) {
            throw new IllegalArgumentException(
                    "Invalid controller port " + port + ", available: 0.."
                            + (CONTROLLER_PORTS - 1));
        }
        controllers[port] = Objects.requireNonNull(controller, "controller");
        bus.connectController(port, controller);
    }

    @Override
    public void connectPanel(SwitchPanel panel) {
        bus.connectPanel(panel);
    }

    @Override
    public void insert(int slot, Media media) {
        slot(slot).insert(media);
        reset();
    }

    @Override
    public Media eject(int slot) {
        return slot(slot).eject();
    }

    /**
     * Сохраняет полное состояние: CPU, RAM, VDP, PSG, позицию внутри
     * кадра, счётчики кадров и идентичность картриджа. Контроллеры,
     * панель и видео/аудио-приёмники остаются внешними.
     */
    @Override
    public void save(StateWriter writer) {
        writer.writeInt(STATE_MAGIC);
        writer.writeByte(STATE_VERSION);
        cpu.save(writer);
        bus.ram().save(writer);
        vdp.save(writer);
        psg.save(writer);
        writer.writeInt(frameLine);
        writer.writeInt(lineCycles);
        writer.writeLong(frameCount);
        saveCartridgeIdentity(writer);
    }

    @Override
    public void restore(StateReader reader) {
        int magic = reader.readInt();
        if (magic != STATE_MAGIC) {
            throw new IllegalArgumentException("invalid SG-1000 save state");
        }
        int version = reader.readByte();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported SG-1000 save state version: " + version);
        }
        cpu.restore(reader);
        bus.ram().restore(reader);
        vdp.restore(reader);
        psg.restore(reader);

        int savedFrameLine = reader.readInt();
        int savedLineCycles = reader.readInt();
        long savedFrameCount = reader.readLong();
        if (savedFrameLine < 0 || savedFrameLine > Sg1000Constants.SCREEN_LINES) {
            throw new IllegalArgumentException(
                    "invalid SG-1000 frame line in save state: " + savedFrameLine);
        }
        if (savedLineCycles < 0
                || savedLineCycles >= Sg1000Constants.CPU_CYCLES_PER_LINE) {
            throw new IllegalArgumentException(
                    "invalid SG-1000 line cycles in save state: " + savedLineCycles);
        }
        if (savedFrameCount < 0) {
            throw new IllegalArgumentException(
                    "invalid SG-1000 frame counter in save state: " + savedFrameCount);
        }
        frameLine = savedFrameLine;
        lineCycles = savedLineCycles;
        frameCount = savedFrameCount;
        restoreCartridgeIdentity(reader);
    }

    private void saveCartridgeIdentity(StateWriter writer) {
        Sg1000Cartridge cartridge = cartridgeSlot.cartridge();
        writer.writeByte(cartridge == null ? 0 : 1);
        if (cartridge == null) {
            return;
        }
        writer.writeInt((int) cartridge.size());
        writer.writeBytes(cartridge.media().name().getBytes(StandardCharsets.UTF_8));
    }

    private void restoreCartridgeIdentity(StateReader reader) {
        int present = reader.readByte();
        if (present != 0 && present != 1) {
            throw new IllegalArgumentException(
                    "invalid cartridge flag in SG-1000 save state: " + present);
        }
        Sg1000Cartridge cartridge = cartridgeSlot.cartridge();
        if (present == 0) {
            if (cartridge != null) {
                throw new IllegalStateException(
                        "SG-1000 save state was captured without a cartridge");
            }
            return;
        }
        if (cartridge == null) {
            throw new IllegalStateException("SG-1000 save state requires a cartridge");
        }
        int size = reader.readInt();
        String name = new String(reader.readBytes(), StandardCharsets.UTF_8);
        if (cartridge.size() != size || !cartridge.media().name().equals(name)) {
            throw new IllegalArgumentException(
                    "SG-1000 save state cartridge mismatch: expected "
                            + name + " (" + size + " bytes), got "
                            + cartridge.media().name() + " (" + cartridge.size() + " bytes)");
        }
    }

    private StorageSlot slot(int index) {
        if (index < 0 || index >= slots.size()) {
            throw new IllegalArgumentException(
                    "Invalid slot index " + index + ", available: 0.." + (slots.size() - 1));
        }
        return slots.get(index);
    }

    public List<StorageSlot> slots() {
        return slots;
    }

    public Sg1000CartridgeSlot cartridgeSlot() {
        return cartridgeSlot;
    }

    public Sg1000Bus bus() {
        return bus;
    }

    public Z80 cpu() {
        return cpu;
    }

    public Tms9918 vdp() {
        return vdp;
    }

    public Sn76489 psg() {
        return psg;
    }

    public long frameCount() {
        return frameCount;
    }

    /**
     * Позиция внутри кадра: строка 0..261 и такты текущей строки.
     */
    public int frameLine() {
        return frameLine;
    }

    public int lineCycles() {
        return lineCycles;
    }

    /**
     * Снимок машины для отладочного дампа.
     */
    public Sg1000DebugSnapshot debugSnapshot() {
        Z80Registers registers = cpu.registers();
        int[] vdpRegisters = new int[8];
        for (int i = 0; i < vdpRegisters.length; i++) {
            vdpRegisters[i] = vdp.register(i);
        }
        int[] psgRegisters = new int[8];
        for (int i = 0; i < psgRegisters.length; i++) {
            psgRegisters[i] = psg.register(i);
        }
        Sg1000Cartridge cartridge = cartridgeSlot.cartridge();
        return new Sg1000DebugSnapshot(
                frameCount,
                vdp.line(),
                vdp.dot(),
                videoWidth(),
                videoHeight(),
                vdp.framebufferArgb().clone(),
                vdp.framebufferIndices().clone(),
                bus.ram().snapshot(),
                vdp.vramSnapshot(),
                vdpRegisters,
                vdp.status(),
                vdp.mode(),
                vdp.line(),
                vdp.frame(),
                psgRegisters,
                new Z80RegisterSnapshot(
                        registers.a,
                        registers.f,
                        registers.b,
                        registers.c,
                        registers.d,
                        registers.e,
                        registers.h,
                        registers.l,
                        registers.a2,
                        registers.f2,
                        registers.b2,
                        registers.c2,
                        registers.d2,
                        registers.e2,
                        registers.h2,
                        registers.l2,
                        registers.ix,
                        registers.iy,
                        registers.sp,
                        registers.pc,
                        registers.i,
                        registers.r,
                        registers.im,
                        registers.memptr,
                        registers.iff1,
                        registers.iff2,
                        cpu.halted(),
                        cpu.remainingCycles()),
                cartridge == null ? "none" : cartridge.media().name(),
                cartridge == null ? 0 : cartridge.size());
    }
}
