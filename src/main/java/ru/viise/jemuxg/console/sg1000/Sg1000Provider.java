package ru.viise.jemuxg.console.sg1000;

import java.util.Set;
import ru.viise.jemuxg.api.console.Console;
import ru.viise.jemuxg.api.console.ConsoleConfig;
import ru.viise.jemuxg.api.console.ConsoleInfo;
import ru.viise.jemuxg.api.console.ConsoleProvider;
import ru.viise.jemuxg.api.storage.MediaKind;

/**
 * Провайдер Sega SG-1000. Регистрируется через META-INF/services.
 */
public final class Sg1000Provider implements ConsoleProvider {

    private static final ConsoleInfo INFO = new Info(
            "sg1000",
            "Sega SG-1000",
            Set.of(MediaKind.CARTRIDGE));

    @Override
    public ConsoleInfo info() {
        return INFO;
    }

    @Override
    public Console create(ConsoleConfig config) {
        return new Sg1000Console(INFO);
    }

    private record Info(String id, String name, Set<MediaKind> supportedMedia) implements ConsoleInfo {
    }
}
