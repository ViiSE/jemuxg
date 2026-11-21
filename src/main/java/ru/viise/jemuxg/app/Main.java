package ru.viise.jemuxg.app;

import java.awt.GraphicsEnvironment;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
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
 * С {@code --smoke} (или в headless-режиме) прогоняет по одному кадру
 * каждой зарегистрированной консоли без окна.
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

        LaunchRequest request;
        try {
            request = parseRequest(args);
        } catch (InvalidPathException e) {
            System.err.println("Некорректный путь: " + e.getMessage());
            usage();
            return;
        }
        if (request == null) {
            return;
        }

        Path rom = request.rom();
        if (rom == null) {
            rom = chooseRom();
        }
        if (rom == null) {
            return;
        }

        ConsoleRegistry registry = ConsoleRegistry.load();
        String consoleId;
        try {
            consoleId = request.consoleId() != null
                    ? registry.require(request.consoleId()).info().id()
                    : consoleFor(rom);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            if (request.consoleId() == null) {
                System.err.println("Укажите консоль явно: --console <id>");
                System.err.println("Доступные консоли: " + registry.all().stream()
                        .map(provider -> provider.info().id())
                        .sorted()
                        .toList());
            }
            return;
        }

        Frontend.launch(rom, consoleId, request.mapper());
    }

    private static LaunchRequest parseRequest(String[] args) {
        String consoleId = null;
        String mapper = null;
        Path rom = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--console".equals(arg)) {
                if (++i >= args.length) {
                    usage();
                    return null;
                }
                consoleId = args[i];
                continue;
            }
            if ("--mapper".equals(arg)) {
                if (++i >= args.length || mapper != null) {
                    usage();
                    return null;
                }
                mapper = args[i];
                continue;
            }
            if ("--rom".equals(arg)) {
                if (++i >= args.length || rom != null) {
                    usage();
                    return null;
                }
                rom = Path.of(args[i]);
                continue;
            }
            if (arg.startsWith("--") || rom != null) {
                usage();
                return null;
            }
            rom = Path.of(arg);
        }

        return new LaunchRequest(rom, consoleId, mapper);
    }

    private static String consoleFor(Path rom) {
        String name = rom.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".nes")) {
            return "nes";
        }
        if (name.endsWith(".a26")) {
            return "atari2600";
        }
        if (name.endsWith(".sg") || name.endsWith(".sc")) {
            return "sg1000";
        }
        throw new IllegalArgumentException(
                "Не удалось определить консоль по расширению файла: " + rom.getFileName());
    }

    private static Path chooseRom() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите ROM");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "Atari 2600 (*.a26)", "a26"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "NES (*.nes)", "nes"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "Sega SG-1000 (*.sg, *.sc)", "sg", "sc"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Все поддерживаемые ROM (*.nes, *.a26, *.sg, *.sc)",
                "nes", "a26", "sg", "sc"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    private static void usage() {
        System.out.println("""
                Использование:
                  java -cp target/classes ru.viise.jemuxg.app.Main [rom.nes|rom.a26|rom.sg|rom.sc]
                  java -cp target/classes ru.viise.jemuxg.app.Main --rom <rom> [--console <id>] [--mapper <scheme>]
                  java -cp target/classes ru.viise.jemuxg.app.Main --console <id> <rom> [--mapper <scheme>]
                  java -cp target/classes ru.viise.jemuxg.app.Main --smoke

                Консоль определяется по расширению: .nes -> nes, .a26 -> atari2600,
                .sg/.sc -> sg1000. Для неоднозначных файлов (.bin) используйте
                --console <id>, например:
                  java -cp target/classes ru.viise.jemuxg.app.Main --console sg1000 game.bin
                Схема картриджа Atari 2600 задаётся через --mapper:
                  auto (по умолчанию), 2k, 4k, f8, f6, f4, f8sc, f6sc, f4sc,
                  3f, 3e, e0, e7, fe.
                """);
    }

    private record LaunchRequest(Path rom, String consoleId, String mapper) {
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

        registry.all().stream()
                .sorted(Comparator.comparing(provider -> provider.info().id()))
                .forEach(provider -> {
                    Console console = provider.create(ConsoleConfig.empty());
                    HeadlessVideoSink video = new HeadlessVideoSink();
                    SilentAudioSink audio = new SilentAudioSink();

                    console.machine().connectVideo(video);
                    console.machine().connectAudio(audio);
                    console.machine().reset();
                    console.machine().runFrame();

                    System.out.printf("%s smoke: frames=%d, samples=%d%n",
                            provider.info().id(), video.frames(), audio.samples());
                });
    }
}
