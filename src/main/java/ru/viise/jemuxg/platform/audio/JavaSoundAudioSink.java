package ru.viise.jemuxg.platform.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import ru.viise.jemuxg.api.hardware.AudioSink;

/**
 * Аудиовыход через {@code javax.sound.sampled}: моно, 16 бит, PCM signed.
 * <p>
 * {@link #samplesReady} пишет сэмплы в буфер звуковой карты. Благодаря
 * блокирующей записи это также задаёт реальный темп эмуляции.
 */
public final class JavaSoundAudioSink implements AudioSink, AutoCloseable {

    private final SourceDataLine line;
    private volatile boolean closed;
    private byte[] bytes = new byte[4096];

    public JavaSoundAudioSink(int sampleRate) throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                1,
                2,
                sampleRate,
                false);
        line = AudioSystem.getSourceDataLine(format);
        line.open(format, 8192);
        line.start();
    }

    @Override
    public void samplesReady(short[] samples, int count) {
        if (closed || count <= 0) {
            return;
        }

        int byteCount = count * 2;
        if (bytes.length < byteCount) {
            bytes = new byte[byteCount];
        }
        for (int i = 0; i < count; i++) {
            short sample = samples[i];
            bytes[i * 2] = (byte) (sample & 0xFF);
            bytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        try {
            int offset = 0;
            while (offset < byteCount) {
                int written = line.write(bytes, offset, byteCount - offset);
                if (written <= 0) {
                    break;
                }
                offset += written;
            }
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // Звуковая линия закрывается вместе с окном.
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        line.stop();
        line.flush();
        line.close();
    }
}
