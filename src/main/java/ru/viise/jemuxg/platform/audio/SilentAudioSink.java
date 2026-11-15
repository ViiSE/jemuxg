package ru.viise.jemuxg.platform.audio;

import ru.viise.jemuxg.api.hardware.AudioSink;

/**
 * Заглушка аудиовыхода: только считает сэмплы.
 */
public final class SilentAudioSink implements AudioSink {

    private long samples;

    @Override
    public void samplesReady(short[] samples, int count) {
        this.samples += count;
    }

    public long samples() {
        return samples;
    }
}
