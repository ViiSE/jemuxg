package ru.viise.jemuxg.console.nes;

import java.util.Set;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleConfig;
import ru.viise.jemuxg.api.console.ConsoleInfo;
import ru.viise.jemuxg.api.console.ConsoleProvider;
import ru.viise.jemuxg.api.storage.MediaKind;

/**
 * Провайдер NES. Регистрируется через META-INF/services.
 */
public final class NesProvider implements ConsoleProvider {

    private static final ConsoleInfo INFO = new Info(
            "nes",
            "Nintendo Entertainment System (NES)",
            Set.of(MediaKind.CARTRIDGE));

    @Override
    public ConsoleInfo info() {
        return INFO;
    }

    @Override
    public Console create(ConsoleConfig config) {
        return new NesConsole(INFO);
    }

    private record Info(String id, String name, Set<MediaKind> supportedMedia) implements ConsoleInfo {
    }
}
