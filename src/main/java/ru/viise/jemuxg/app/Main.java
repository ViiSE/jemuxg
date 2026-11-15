package ru.viise.jemuxg.app;

import java.awt.GraphicsEnvironment;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleConfig;
import ru.viise.jemuxg.core.registry.ConsoleRegistry;
import ru.viise.jemuxg.platform.audio.SilentAudioSink;
import ru.viise.jemuxg.platform.video.HeadlessVideoSink;

/**
 * Точка входа.
 * <p>
 * Без аргументов открывает выбор ROM и запускает Swing-фронтенд.
 * С {@code --smoke} (или в headless-режиме) прогоняет один кадр NES без окна.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            usage();
            return;
        }
        if (args.length == 1 && "--smoke".equals(args[0])) {
            smoke();
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            smoke();
            return;
        }

        Path rom;
        try {
            rom = parseRomPath(args);
        } catch (InvalidPathException e) {
            System.err.println("Некорректный путь: " + e.getMessage());
            usage();
            return;
        }

        if (rom == null && args.length == 0) {
            rom = chooseRom();
        }
        if (rom == null) {
            return;
        }

        Frontend.launch(rom);
    }

    private static Path parseRomPath(String[] args) {
        if (args.length == 0) {
            return null;
        }
        if (args.length == 1 && !args[0].startsWith("--")) {
            return Path.of(args[0]);
        }
        if (args.length == 2 && "--rom".equals(args[0])) {
            return Path.of(args[1]);
        }
        usage();
        return null;
    }

    private static Path chooseRom() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите NES ROM");
        chooser.setFileFilter(new FileNameExtensionFilter("NES ROM (*.nes)", "nes"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    private static void usage() {
        System.out.println("""
                Использование:
                  java -cp target/classes ru.viise.jemuxg.app.Main [rom.nes]
                  java -cp target/classes ru.viise.jemuxg.app.Main --rom rom.nes
                  java -cp target/classes ru.viise.jemuxg.app.Main --smoke
                """);
    }

    private static void smoke() {
        ConsoleRegistry registry = ConsoleRegistry.load();

        registry.all().stream()
                .sorted(Comparator.comparing(provider -> provider.info().id()))
                .forEach(provider -> System.out.printf(
                        "%-8s %-40s %s%n",
                        provider.info().id(),
                        provider.info().name(),
                        provider.info().supportedMedia()));

        Console console = registry.require("nes").create(ConsoleConfig.empty());
        HeadlessVideoSink video = new HeadlessVideoSink();
        SilentAudioSink audio = new SilentAudioSink();

        console.machine().connectVideo(video);
        console.machine().connectAudio(audio);
        console.machine().reset();
        console.machine().runFrame();

        System.out.printf("nes smoke: frames=%d, samples=%d%n", video.frames(), audio.samples());
    }
}
