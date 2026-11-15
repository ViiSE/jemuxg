package ru.viise.jemuxg.console.nes;

import java.util.List;
import ru.viise.jemuxg.api.debug.CpuDebugger;
import ru.viise.jemuxg.api.debug.CpuRegisters;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.InterruptType;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.StorageSlot;
import ru.viise.jemuxg.console.nes.cartridge.NesCartridge;
import ru.viise.jemuxg.console.nes.cartridge.NesMapper;
import ru.viise.jemuxg.console.nes.debug.NesDebugSnapshot;
import ru.viise.jemuxg.core.bus.Mirror;
import ru.viise.jemuxg.core.bus.SimpleBus;
import ru.viise.jemuxg.core.memory.Ram;
import ru.viise.jemuxg.hardware.audio.ricoh2a03.Ricoh2a03Apu;
import ru.viise.jemuxg.hardware.cpu.mos6502.Mos6502;
import ru.viise.jemuxg.hardware.video.ricoh2c02.Ricoh2c02;

/**
 * Сборка NES. Единственное место, где известны конкретные чипы.
 */
public final class NesMachine implements Machine {

    private static final int PPU_CYCLES_PER_CPU_CYCLE = 3;
    private static final int CONTROLLER_PORTS = 2;

    private final Ram ram = new Ram(0x800);
    private final Ricoh2c02 ppu = new Ricoh2c02();
    private final Ricoh2a03Apu apu = new Ricoh2a03Apu();
    private final NesCartridgeSlot cartridgeSlot = new NesCartridgeSlot();
    private final SimpleBus bus = new SimpleBus();
    private final Bus apuRegisters = apu.registers();
    private final NesControllerBus controllerBus = new NesControllerBus(apuRegisters);
    private final Mos6502 cpu;
    private final List<StorageSlot> slots = List.of(cartridgeSlot);

    public NesMachine() {
        bus.map(0x0000, 0x1FFF, ram);
        bus.map(0x2000, 0x3FFF, new Mirror(ppu.registers(), 8));
        bus.map(0x4000, 0x4013, apuRegisters);
        bus.map(0x4014, 0x4014, new Bus() {
            @Override
            public int read(int address) {
                return 0xFF;
            }

            @Override
            public void write(int address, int value) {
                ppu.oamDma(bus, value);
            }
        });
        bus.map(0x4015, 0x4015, apuRegisters);
        bus.map(0x4016, 0x4017, controllerBus);
        bus.map(0x4020, 0xFFFF, cartridgeSlot);
        cpu = new Mos6502(bus, Mos6502.Variant.RICOH_2A03);
        apu.connectCpuBus(bus);
        ppu.connectA12(this::clockMapperIrq);
        bindCartridge();
        reset();
    }

    @Override
    public void reset() {
        ram.clear();
        cpu.reset();
        ppu.reset();
        apu.reset();
        controllerBus.reset();
        bindCartridge();
    }

    @Override
    public void runFrame() {
        long ppuCycles = 0;
        while (ppuCycles < Ricoh2c02.CYCLES_PER_FRAME) {
            int step = (int) Math.min(
                    PPU_CYCLES_PER_CPU_CYCLE,
                    Ricoh2c02.CYCLES_PER_FRAME - ppuCycles);

            if (cartridgeSlot.isPresent()) {
                cpu.tick(1);
            }
            apu.tick(1);
            ppu.setMirroring(cartridgeSlot.mirroring());
            ppu.tick(step);
            ppuCycles += step;

            if (cartridgeSlot.isPresent()) {
                if (ppu.takeNmi()) {
                    cpu.interrupt(InterruptType.NMI);
                }
                updateIrqLine();
            }
        }
        apu.flush();
    }

    private void bindCartridge() {
        ppu.connectChr(cartridgeSlot.chrBus());
        ppu.setMirroring(cartridgeSlot.mirroring());
    }

    private void clockMapperIrq() {
        NesMapper mapper = cartridgeSlot.mapper();
        if (mapper != null) {
            mapper.clockIrq();
        }
    }

    private void updateIrqLine() {
        boolean pending = apu.irqPending();
        NesMapper mapper = cartridgeSlot.mapper();
        if (mapper != null) {
            pending |= mapper.irqPending();
        }
        if (pending) {
            cpu.interrupt(InterruptType.IRQ);
        } else {
            cpu.clearInterrupt(InterruptType.IRQ);
        }
    }

    @Override
    public void connectVideo(VideoSink sink) {
        ppu.connect(sink);
    }

    @Override
    public void connectAudio(AudioSink sink) {
        apu.connect(sink);
    }

    @Override
    public int audioSampleRate() {
        return apu.sampleRate();
    }

    /**
     * Доступ к пошаговому исполнению CPU для отладочных тестов.
     */
    public CpuDebugger debugCpu() {
        return cpu;
    }

    public NesDebugSnapshot debugSnapshot() {
        NesCartridge cartridge = cartridgeSlot.cartridge();
        CpuRegisters registers = cpu instanceof CpuDebugger debug
                ? debug.registers()
                : new CpuRegisters(0, 0, 0, 0, 0, 0);
        return new NesDebugSnapshot(
                ppu.frameCount(),
                Ricoh2c02.WIDTH,
                Ricoh2c02.HEIGHT,
                ppu.framebufferArgb(),
                ppu.framebufferIndices(),
                ram.snapshot(),
                cartridgeSlot.prgRamSnapshot(),
                ppu.vramSnapshot(),
                ppu.paletteSnapshot(),
                ppu.oamSnapshot(),
                registers,
                cartridge == null ? "none" : cartridge.mapper().name(),
                cartridge == null ? -1 : cartridge.mapper().id());
    }

    @Override
    public void connectController(int port, Controller controller) {
        if (port < 0 || port >= CONTROLLER_PORTS) {
            throw new IllegalArgumentException(
                    "NES has " + CONTROLLER_PORTS + " controller ports, got " + port);
        }
        controllerBus.connect(port, controller);
    }

    @Override
    public void insert(int slot, Media media) {
        slot(slot).insert(media);
        bindCartridge();
        // После вставки картриджа CPU должен заново прочитать reset-вектор.
        reset();
    }

    @Override
    public Media eject(int slot) {
        Media media = slot(slot).eject();
        bindCartridge();
        return media;
    }

    private StorageSlot slot(int index) {
        if (index < 0 || index >= slots.size()) {
            throw new IllegalArgumentException("Invalid slot index " + index + ", available: 0.." + (slots.size() - 1));
        }
        return slots.get(index);
    }

    public List<StorageSlot> slots() {
        return slots;
    }
}
