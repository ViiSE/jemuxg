package ru.viise.jemuxg.platform.audio;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import ru.viise.jemuxg.api.hardware.AudioSink;

/**
 * Аудиовыход через {@code javax.sound.sampled}: моно, 16 бит, PCM signed.
 *
 * <p>Сэмплы складываются в небольшую очередь и пишутся в звуковую линию
 * отдельным потоком. Раньше блокирующая {@code line.write} выполнялась в
 * потоке эмуляции и периодически задерживала кадры, когда очередь звуковой
 * карты заполнялась. Теперь эмуляция не зависит от скорости аудиоустройства;
 * при переполнении очереди самые старые сэмплы отбрасываются.</p>
 */
public final class JavaSoundAudioSink implements AudioSink, AutoCloseable {

    private static final int MAX_QUEUED_FRAMES = 8;

    private final SourceDataLine line;
    private final BlockingQueue<short[]> queue = new ArrayBlockingQueue<>(MAX_QUEUED_FRAMES);
    private final Thread writer;
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
        writer = new Thread(this::writeLoop, "jemuxg-audio");
        writer.setDaemon(true);
        writer.start();
    }

    @Override
    public void samplesReady(short[] samples, int count) {
        if (closed || count <= 0) {
            return;
        }

        short[] copy = Arrays.copyOf(samples, count);
        if (!queue.offer(copy)) {
            // Не даём эмуляции ждать звуковую карту. При отставании аудио
            // теряем самый старый кадр — это лучше, чем просадка FPS.
            queue.poll();
            queue.offer(copy);
        }
    }

    private void writeLoop() {
        while (!closed || !queue.isEmpty()) {
            short[] samples;
            try {
                samples = queue.poll();
            } catch (RuntimeException ignored) {
                break;
            }
            if (samples == null) {
                if (closed) {
                    break;
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    if (closed) {
                        break;
                    }
                }
                continue;
            }
            write(samples);
        }
    }

    private void write(short[] samples) {
        int byteCount = samples.length * 2;
        if (bytes.length < byteCount) {
            bytes = new byte[byteCount];
        }
        for (int i = 0; i < samples.length; i++) {
            short sample = samples[i];
            bytes[i * 2] = (byte) (sample & 0xFF);
            bytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        try {
            int offset = 0;
            while (!closed && offset < byteCount) {
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
        writer.interrupt();
        try {
            writer.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        line.stop();
        line.flush();
        line.close();
    }
}
