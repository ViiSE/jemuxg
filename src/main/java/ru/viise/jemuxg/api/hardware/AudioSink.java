package ru.viise.jemuxg.api.hardware;

/**
 * Получатель сэмплов. Реализуется фронтендом.
 */
public interface AudioSink {

    void samplesReady(short[] samples, int count);
}
