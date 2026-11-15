package ru.viise.jemuxg.api.console;

import java.util.Set;
import ru.viise.jemuxg.api.storage.MediaKind;

public interface ConsoleInfo {

    String id();

    String name();

    Set<MediaKind> supportedMedia();
}
