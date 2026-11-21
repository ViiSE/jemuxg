package ru.viise.jemuxg.console.sg1000;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.core.input.SimpleController;
import ru.viise.jemuxg.core.state.BinaryStateReader;
import ru.viise.jemuxg.core.state.BinaryStateWriter;
import ru.viise.jemuxg.storage.media.FileMedia;

/**
 * Опциональный дымовой тест на реальных ROM SG-1000.
 *
 * <p>ROM не входят в репозиторий. Тест ищет их рядом с рабочим каталогом
 * или принимает пути аргументами / через {@code -Djemuxg.sg1000.rom}.
 * Если файлов нет, тест пропускается.</p>
 */
public final class Sg1000RomTests {

    private static final String[] DEFAULT_ROMS = {
        "Bomb Jack (Japan).sg",
        "H.E.R.O. (Japan).sg"
    };

    private static int checks;

    private Sg1000RomTests() {
    }

    public static void main(String[] args) throws Exception {
        List<Path> roms = roms(args);
        if (roms.isEmpty()) {
            System.out.println("SG-1000 ROM tests skipped: no .sg files supplied");
            return;
        }
        for (Path rom : roms) {
            runRom(rom);
        }
        System.out.println("SG-1000 ROM tests OK, checks=" + checks);
    }

    private static List<Path> roms(String[] args) {
        List<Path> roms = new ArrayList<>();
        if (args.length > 0) {
            for (String arg : args) {
                Path path = Path.of(arg);
                if (Files.isRegularFile(path)) {
                    roms.add(path);
                }
            }
            return roms;
        }
        String property = System.getProperty("jemuxg.sg1000.rom");
        if (property != null && !property.isBlank()) {
            for (String name : property.split(",")) {
                Path path = Path.of(name.trim());
                if (Files.isRegularFile(path)) {
                    roms.add(path);
                }
            }
            return roms;
        }
        for (String name : DEFAULT_ROMS) {
            Path path = Path.of(name);
            if (Files.isRegularFile(path)) {
                roms.add(path);
            }
        }
        return roms;
    }

    private static void runRom(Path rom) throws Exception {
        Sg1000Machine machine = new Sg1000Machine();
        FrameCapture video = new FrameCapture();
        CaptureAudio audio = new CaptureAudio();
        SimpleController controller = new SimpleController();
        machine.connectVideo(video);
        machine.connectAudio(audio);
        machine.connectController(0, controller);
        machine.insert(0, FileMedia.open(rom, MediaKind.CARTRIDGE));

        int frames = 300;
        for (int frame = 0; frame < frames; frame++) {
            if (frame % 20 == 0) {
                controller.press(Button.A);
            } else {
                controller.release(Button.A);
            }
            if (frame >= 120 && frame < 180) {
                controller.press(Button.RIGHT);
            } else {
                controller.release(Button.RIGHT);
            }
            machine.runFrame();
        }

        check(video.frames == frames,
                rom.getFileName() + " emits one video frame per frame");
        check(video.last != null
                        && video.last.width() == Sg1000Constants.VISIBLE_WIDTH
                        && video.last.height() == Sg1000Constants.VISIBLE_HEIGHT,
                rom.getFileName() + " produces 256x192 frames");
        check(video.last != null && uniqueColors(video.last) >= 2,
                rom.getFileName() + " renders a non-empty picture");
        check(audio.samples > 0 && audio.nonZero > 0,
                rom.getFileName() + " produces non-zero audio");

        controller.releaseAll();
        machine.runFrame();
        VideoFrame expected = video.last;
        BinaryStateWriter writer = new BinaryStateWriter();
        ((Stateful) machine).save(writer);
        byte[] state = writer.toByteArray();

        machine.runFrame();
        machine.runFrame();
        machine.restore(new BinaryStateReader(state));
        machine.runFrame();
        machine.runFrame();
        check(video.last != null && expected != null
                        && Arrays.equals(video.last.pixels(), expected.pixels()),
                rom.getFileName() + " is deterministic after save/restore");
    }

    private static int uniqueColors(VideoFrame frame) {
        Set<Integer> colors = new HashSet<>();
        for (int pixel : frame.pixels()) {
            colors.add(pixel);
        }
        return colors.size();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        checks++;
        System.out.println("OK: " + message);
    }

    private static final class FrameCapture implements VideoSink {

        private VideoFrame last;
        private int frames;

        @Override
        public void frameReady(VideoFrame frame) {
            last = frame;
            frames++;
        }
    }

    private static final class CaptureAudio implements AudioSink {

        private long samples;
        private long nonZero;

        @Override
        public void samplesReady(short[] data, int count) {
            samples += count;
            for (int i = 0; i < count; i++) {
                if (data[i] != 0) {
                    nonZero++;
                }
            }
        }
    }
}
