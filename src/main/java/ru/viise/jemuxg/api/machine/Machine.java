package ru.viise.jemuxg.api.machine;

import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.storage.Media;

/**
 * Работающая схема консоли. Фронтенд видит только этот контракт.
 */
public interface Machine {

    void reset();

    /**
     * Выполняет ровно один видеокадр. Детерминированно, без привязки к wall-clock.
     */
    void runFrame();

    void connectVideo(VideoSink sink);

    void connectAudio(AudioSink sink);

    /**
     * Частота дискретизации звукового выхода. Фронтенд использует её для
     * открытия аудиоустройства.
     */
    default int audioSampleRate() {
        return 44_100;
    }

    void connectController(int port, Controller controller);

    void insert(int slot, Media media);

    Media eject(int slot);
}
