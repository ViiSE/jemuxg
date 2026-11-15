package ru.viise.jemuxg.console.atari2600;

import java.util.Set;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleConfig;
import ru.viise.jemuxg.api.console.ConsoleInfo;
import ru.viise.jemuxg.api.console.ConsoleProvider;
import ru.viise.jemuxg.api.storage.MediaKind;

/**
 * Провайдер Atari 2600. Регистрируется через META-INF/services.
 */
public final class Atari2600Provider implements ConsoleProvider {

    private static final ConsoleInfo INFO = new Info(
            "atari2600",
            "Atari 2600",
            Set.of(MediaKind.CARTRIDGE));

    @Override
    public ConsoleInfo info() {
        return INFO;
    }

    @Override
    public Console create(ConsoleConfig config) {
        return new Atari2600Console(INFO);
    }

    private record Info(String id, String name, Set<MediaKind> supportedMedia) implements ConsoleInfo {
    }
}
