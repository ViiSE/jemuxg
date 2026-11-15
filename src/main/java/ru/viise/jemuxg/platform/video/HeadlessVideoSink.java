package ru.viise.jemuxg.platform.video;

import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;

/**
 * Заглушка видеовыхода для тестов и headless-запуска.
 */
public final class HeadlessVideoSink implements VideoSink {

    private long frames;

    @Override
    public void frameReady(VideoFrame frame) {
        frames++;
    }

    public long frames() {
        return frames;
    }
}
