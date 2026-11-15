package ru.viise.jemuxg.api.hardware;

/**
 * Получатель готовых кадров. Реализуется фронтендом.
 */
public interface VideoSink {

    void frameReady(VideoFrame frame);
}
