package ru.viise.jemuxg.app;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleConfig;
import ru.viise.jemuxg.api.debug.ConsoleDebugger;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.machine.Machine;
import ru.viise.jemuxg.core.debug.FileDebugSession;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.core.input.SimpleController;
import ru.viise.jemuxg.core.registry.ConsoleRegistry;
import ru.viise.jemuxg.platform.audio.JavaSoundAudioSink;
import ru.viise.jemuxg.platform.audio.SilentAudioSink;
import ru.viise.jemuxg.platform.input.SwingKeyboardController;
import ru.viise.jemuxg.platform.video.SwingVideoSink;
import ru.viise.jemuxg.storage.media.FileMedia;

/**
 * Swing-фронтенд: окно с кадром, звук через Java Sound и клавиатурный ввод.
 */
public final class Frontend {

    private static final long FRAME_NANOS = 1_000_000_000L / 60;

    private final Machine machine;
    private final ConsoleDebugger debugger;
    private final SwingVideoSink video;
    private final SwingKeyboardController keyboard;
    private final FileMedia media;
    private final AutoCloseable audioCloseable;
    private final JFrame frame;
    private final Thread emulationThread;
    private volatile boolean running = true;
    private volatile boolean debugCaptureRequested;
    private long frameIndex;

    public static void launch(Path romPath, String consoleId) {
        launch(romPath, consoleId, null);
    }

    public static void launch(Path romPath, String consoleId, String mapperName) {
        SwingUtilities.invokeLater(() -> {
            try {
                new Frontend(romPath, consoleId, mapperName).show();
            } catch (Exception e) {
                showError(e);
            }
        });
    }

    private Frontend(Path romPath, String consoleId, String mapperName) throws IOException {
        if (!Files.isRegularFile(romPath) || !Files.isReadable(romPath)) {
            throw new IOException("ROM не найден или недоступен: " + romPath);
        }

        media = FileMedia.open(romPath, MediaKind.CARTRIDGE);
        ConsoleConfig config = mapperName == null || mapperName.isBlank()
                ? ConsoleConfig.empty()
                : new ConsoleConfig(Map.of("mapper", mapperName));
        Console console = ConsoleRegistry.load()
                .require(consoleId)
                .create(config);
        machine = console.machine();
        debugger = console.debugger().orElse(null);
        video = new SwingVideoSink();
        SimpleController controller = new SimpleController();
        keyboard = new SwingKeyboardController(controller);

        AudioSink audioSink;
        AutoCloseable closeable = null;
        try {
            JavaSoundAudioSink sound = new JavaSoundAudioSink(machine.audioSampleRate());
            audioSink = sound;
            closeable = sound;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.err.println("Звук недоступен, работаю без звука: " + e.getMessage());
            audioSink = new SilentAudioSink();
        }
        audioCloseable = closeable;

        machine.connectVideo(video);
        machine.connectAudio(audioSink);
        machine.connectController(0, controller);
        machine.insert(0, media);

        frame = new JFrame("jemuxg — " + media.name());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(video);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                close();
            }

            @Override
            public void windowLostFocus(WindowEvent event) {
                keyboard.releaseAll();
            }

            @Override
            public void windowGainedFocus(WindowEvent event) {
                video.requestFocusInWindow();
            }
        });

        video.addKeyListener(keyboard);
        video.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_F12) {
                    debugCaptureRequested = true;
                    event.consume();
                }
            }
        });
        video.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                keyboard.releaseAll();
            }
        });

        emulationThread = new Thread(this::runLoop, "jemuxg-emulation");
        emulationThread.setDaemon(true);
    }

    private void show() {
        frame.setVisible(true);
        video.requestFocusInWindow();
        emulationThread.start();
    }

    private void runLoop() {
        long nextFrame = System.nanoTime();
        try {
            while (running) {
                machine.runFrame();
                frameIndex++;
                if (debugCaptureRequested) {
                    captureDebug();
                }

                nextFrame += FRAME_NANOS;
                long delay = nextFrame - System.nanoTime();
                if (delay > 0) {
                    LockSupport.parkNanos(delay);
                } else if (delay < -5 * FRAME_NANOS) {
                    nextFrame = System.nanoTime();
                }
            }
        } catch (Throwable error) {
            if (running) {
                error.printStackTrace();
            }
        }
    }

    private void captureDebug() {
        debugCaptureRequested = false;
        if (debugger == null) {
            return;
        }
        try {
            FileDebugSession session = new FileDebugSession(Path.of("debug"), media.name(), frameIndex);
            debugger.capture(session);
            System.out.println("debug snapshot: " + session.directory());
        } catch (IOException | RuntimeException error) {
            System.err.println("Не удалось сохранить debug snapshot: " + error.getMessage());
            error.printStackTrace();
        }
    }

    private void close() {
        if (!running) {
            return;
        }
        running = false;
        emulationThread.interrupt();

        if (audioCloseable != null) {
            try {
                audioCloseable.close();
            } catch (Exception ignored) {
                // Окно всё равно закрывается.
            }
        }

        try {
            emulationThread.join(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            media.close();
        } catch (IOException ignored) {
            // Файл уже мог быть закрыт.
        }

        frame.dispose();
        System.exit(0);
    }

    private static void showError(Exception error) {
        error.printStackTrace();
        JOptionPane.showMessageDialog(
                null,
                error.getMessage(),
                "jemuxg",
                JOptionPane.ERROR_MESSAGE);
    }
}
