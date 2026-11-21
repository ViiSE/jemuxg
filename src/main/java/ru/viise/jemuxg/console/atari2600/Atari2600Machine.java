package ru.viise.jemuxg.console.atari2600;

import java.util.List;
import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.PaddleController;
import ru.viise.jemuxg.api.hardware.SwitchPanel;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.StorageSlot;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariCartridge;
import ru.viise.jemuxg.console.atari2600.cartridge.AtariMapper;
import ru.viise.jemuxg.console.atari2600.debug.AtariDebugSnapshot;
import ru.viise.jemuxg.hardware.cpu.mos6507.Mos6507;
import ru.viise.jemuxg.hardware.riot.riot6532.Riot6532Chip;
import ru.viise.jemuxg.hardware.video.tia.StellaTiaVideo;

/**
 * Сборка Atari 2600.
 *
 * <p>CPU 6507, TIA, RIOT и картридж работают на общей шине. За один такт
 * CPU TIA получает три цветовых такта, RIOT — один такт CPU. WSYNC
 * останавливает CPU до конца текущей строки.</p>
 */
public final class Atari2600Machine implements Machine, Stateful {

    private static final int CONTROLLER_PORTS = 2;
    private static final int STATE_MAGIC = 0x4A4D5831;
    private static final int STATE_VERSION = 3;

    private final Atari2600CartridgeSlot cartridgeSlot;
    private final List<StorageSlot> slots;
    private final Controller[] controllers = new Controller[CONTROLLER_PORTS];
    private final StellaTiaVideo tia = new StellaTiaVideo();
    private final Riot6532Chip riot = new Riot6532Chip();
    private final Atari2600Bus bus;
    private final Mos6507 cpu;

    public Atari2600Machine() {
        this(null);
    }

    /**
     * @param mapperName явная схема картриджа или {@code null}/{@code auto}
     */
    public Atari2600Machine(String mapperName) {
        cartridgeSlot = new Atari2600CartridgeSlot(mapperName);
        slots = List.of(cartridgeSlot);
        bus = new Atari2600Bus(
                tia.registers(), riot.ram(), riot.registers(), cartridgeSlot, true);
        cpu = new Mos6507(bus);
        reset();
    }

    @Override
    public void reset() {
        tia.reset();
        riot.reset();
        cpu.reset();
    }

    @Override
    public void runFrame() {
        runCycles(Atari2600Constants.CPU_CYCLES_PER_FRAME);
    }

    /**
     * Выполняет заданное число тактов CPU. Полезно для пошаговой отладки
     * и сохранения состояния посреди кадра.
     */
    public void runCycles(int cpuCycles) {
        if (cpuCycles < 0) {
            throw new IllegalArgumentException("cpuCycles must be non-negative: " + cpuCycles);
        }
        for (int i = 0; i < cpuCycles; i++) {
            boolean cpuStep = !tia.cpuHalted() && cartridgeSlot.isPresent();
            if (cpuStep) {
                cpu.tick(1);
                if (cpu.instructionComplete()) {
                    bus.flushPendingWrites();
                }
            }
            riot.tick(1);
            tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_CPU_CYCLE);
            AtariCartridge cartridge = cartridgeSlot.cartridge();
            if (cartridge != null) {
                cartridge.mapper().tick(1);
            }
        }
    }

    /**
     * Сохраняет полное состояние машины: CPU, RIOT, TIA (включая звук и
     * framebuffer) и картридж/маппер. Подключения контроллеров и панели
     * остаются внешними и не сериализуются.
     */
    @Override
    public void save(StateWriter writer) {
        writer.writeInt(STATE_MAGIC);
        writer.writeByte(STATE_VERSION);
        cpu.save(writer);
        riot.save(writer);
        tia.save(writer);

        AtariCartridge cartridge = cartridgeSlot.cartridge();
        writer.writeByte(cartridge == null ? 0 : 1);
        if (cartridge != null) {
            cartridge.save(writer);
        }
    }

    @Override
    public void restore(StateReader reader) {
        if (reader.readInt() != STATE_MAGIC) {
            throw new IllegalArgumentException("invalid Atari 2600 save state");
        }
        int version = reader.readByte();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException("unsupported Atari 2600 save state version: " + version);
        }
        cpu.restore(reader);
        riot.restore(reader);
        tia.restore(reader);

        int present = reader.readByte();
        if (present != 0 && present != 1) {
            throw new IllegalArgumentException("invalid cartridge flag in save state: " + present);
        }
        AtariCartridge cartridge = cartridgeSlot.cartridge();
        if (present == 1) {
            if (cartridge == null) {
                throw new IllegalStateException("save state requires a cartridge");
            }
            cartridge.restore(reader);
        } else if (cartridge != null) {
            throw new IllegalStateException("save state was captured without a cartridge");
        }
    }

    @Override
    public void connectVideo(VideoSink sink) {
        tia.connect(sink);
    }

    @Override
    public void connectAudio(AudioSink sink) {
        tia.connectAudio(sink);
    }

    @Override
    public int audioSampleRate() {
        return Atari2600Constants.AUDIO_SAMPLE_RATE;
    }

    @Override
    public int videoWidth() {
        return Atari2600Constants.VISIBLE_WIDTH;
    }

    @Override
    public int videoHeight() {
        return Atari2600Constants.VISIBLE_HEIGHT;
    }

    @Override
    public void connectController(int port, Controller controller) {
        if (port < 0 || port >= CONTROLLER_PORTS) {
            throw new IllegalArgumentException(
                    "Atari 2600 has " + CONTROLLER_PORTS + " controller ports, got " + port);
        }
        controllers[port] = controller;
        tia.connectPlayer(port, controller);
        riot.connectPlayer(port, controller);
        if (controller instanceof PaddleController paddle) {
            tia.connectPaddle(port, paddle);
        }
    }

    /**
     * Подключает консольные переключатели (Reset, Select, Color/B&amp;W).
     */
    @Override
    public void connectPanel(SwitchPanel panel) {
        riot.connectPanel(panel);
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

    public StellaTiaVideo tia() {
        return tia;
    }

    public StellaTiaVideo stellaTiaChip() {
        return tia;
    }

    public Riot6532Chip riot() {
        return riot;
    }

    public Mos6507 cpu() {
        return cpu;
    }

    public Atari2600Bus bus() {
        return bus;
    }

    /**
     * Снимок машины для отладочного дампа. Вызывается на границе кадра.
     */
    public AtariDebugSnapshot debugSnapshot() {
        AtariCartridge cartridge = cartridgeSlot.cartridge();
        AtariMapper mapper = cartridge == null ? null : cartridge.mapper();
        return new AtariDebugSnapshot(
                tia.frameCount(),
                tia.scanline(),
                tia.colorClock(),
                tia.cpuHalted(),
                videoWidth(),
                videoHeight(),
                tia.framebufferArgb(),
                tia.framebufferIndices(),
                riot.ramSnapshot(),
                tia.registerSnapshot(),
                riot.registerSnapshot(),
                cpu.registers(),
                mapper == null ? "none" : mapper.name(),
                mapper == null ? "" : mapper.debugState(),
                mapper == null ? -1 : mapper.bank(),
                mapper == null ? 0 : mapper.bankCount(),
                mapper != null && mapper.writable(),
                mapper == null ? new byte[0] : mapper.ramSnapshot());
    }
}
