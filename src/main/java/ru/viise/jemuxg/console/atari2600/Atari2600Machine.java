package ru.viise.jemuxg.console.atari2600;

import java.util.List;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.StorageSlot;
import ru.viise.jemuxg.hardware.video.tia.Tia;
import ru.viise.jemuxg.hardware.video.tia.TiaVideo;

/**
 * Сборка Atari 2600.
 *
 * <p>Сейчас подключён каркас TIA: развёртка и кадр 160×192. CPU, RIOT и
 * картридж будут связаны с шиной на следующих этапах.</p>
 */
public final class Atari2600Machine implements Machine {

    private static final int CONTROLLER_PORTS = 2;

    private final Atari2600CartridgeSlot cartridgeSlot = new Atari2600CartridgeSlot();
    private final List<StorageSlot> slots = List.of(cartridgeSlot);
    private final Controller[] controllers = new Controller[CONTROLLER_PORTS];
    private final TiaVideo tia = new Tia();

    private AudioSink audioSink;

    public Atari2600Machine() {
        tia.reset();
    }

    @Override
    public void reset() {
        tia.reset();
    }

    @Override
    public void runFrame() {
        tia.tick(Atari2600Constants.COLOR_CLOCKS_PER_FRAME);
    }

    @Override
    public void connectVideo(VideoSink sink) {
        tia.connect(sink);
    }

    @Override
    public void connectAudio(AudioSink sink) {
        audioSink = sink;
    }

    @Override
    public int audioSampleRate() {
        return Atari2600Constants.AUDIO_SAMPLE_RATE;
    }

    @Override
    public void connectController(int port, Controller controller) {
        if (port < 0 || port >= CONTROLLER_PORTS) {
            throw new IllegalArgumentException(
                    "Atari 2600 has " + CONTROLLER_PORTS + " controller ports, got " + port);
        }
        controllers[port] = controller;
        tia.connectPlayer(port, controller);
    }

    @Override
    public void insert(int slot, Media media) {
        slot(slot).insert(media);
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

    public TiaVideo tia() {
        return tia;
    }
}
