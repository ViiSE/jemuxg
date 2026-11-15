package ru.viise.jemuxg.console.atari2600;

import java.util.Arrays;
import java.util.List;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.api.storage.Media;
import ru.viise.jemuxg.api.storage.StorageSlot;

/**
 * Сборка Atari 2600.
 *
 * <p>Сейчас это каркас этапа 1: провайдер, консоль, слот и пустой кадр.
 * TIA, RIOT и 6507 подключаются на следующих этапах.</p>
 */
public final class Atari2600Machine implements Machine {

    private static final int CONTROLLER_PORTS = 2;

    private final Atari2600CartridgeSlot cartridgeSlot = new Atari2600CartridgeSlot();
    private final List<StorageSlot> slots = List.of(cartridgeSlot);
    private final Controller[] controllers = new Controller[CONTROLLER_PORTS];
    private final int[] pixels = new int[
            Atari2600Constants.VISIBLE_WIDTH * Atari2600Constants.VISIBLE_HEIGHT];

    private VideoSink videoSink;
    private AudioSink audioSink;

    public Atari2600Machine() {
        Arrays.fill(pixels, 0xFF000000);
    }

    @Override
    public void reset() {
        // Состояние TIA/RIOT/6507 появится вместе с их реализацией.
    }

    @Override
    public void runFrame() {
        if (videoSink != null) {
            videoSink.frameReady(new VideoFrame(
                    Atari2600Constants.VISIBLE_WIDTH,
                    Atari2600Constants.VISIBLE_HEIGHT,
                    pixels));
        }
    }

    @Override
    public void connectVideo(VideoSink sink) {
        videoSink = sink;
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
}
