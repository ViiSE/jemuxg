package ru.viise.jemuxg.platform.video;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;

/**
 * Видеовыход в Swing-панель. Кадры приходят из потока эмуляции, а рисуются
 * в EDT; панель хранит только последний кадр.
 */
public final class SwingVideoSink extends JPanel implements VideoSink {

    private static final int DEFAULT_WIDTH = 256;
    private static final int DEFAULT_HEIGHT = 240;
    private static final int DEFAULT_SCALE = 3;

    private volatile BufferedImage image;

    public SwingVideoSink() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Создаёт панель под конкретное разрешение кадра. Окно получает размер
     * в {@value #DEFAULT_SCALE} раза больше кадра, а при изменении размера
     * панели картинка масштабируется с сохранением пропорций.
     */
    public SwingVideoSink(int frameWidth, int frameHeight) {
        if (frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException(
                    "Frame size must be positive: " + frameWidth + "x" + frameHeight);
        }
        setBackground(Color.BLACK);
        setOpaque(true);
        setFocusable(true);
        setPreferredSize(new Dimension(
                frameWidth * DEFAULT_SCALE,
                frameHeight * DEFAULT_SCALE));
    }

    @Override
    public void frameReady(VideoFrame frame) {
        BufferedImage next = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_RGB);
        next.setRGB(0, 0, frame.width(), frame.height(), frame.pixels(), 0, frame.width());
        image = next;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        BufferedImage current = image;
        if (current == null) {
            return;
        }

        int panelWidth = getWidth();
        int panelHeight = getHeight();
        if (panelWidth <= 0 || panelHeight <= 0) {
            return;
        }

        double scale = Math.min(
                (double) panelWidth / current.getWidth(),
                (double) panelHeight / current.getHeight());
        int width = Math.max(1, (int) Math.round(current.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(current.getHeight() * scale));
        int x = (panelWidth - width) / 2;
        int y = (panelHeight - height) / 2;

        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(current, x, y, width, height, null);
        } finally {
            g2.dispose();
        }
    }
}
