package ru.viise.jemuxg.api.console;

import java.util.Map;

/**
 * Настройки конкретного запуска (регион, BIOS, пути и т.п.).
 */
public record ConsoleConfig(Map<String, String> options) {

    public ConsoleConfig {
        options = Map.copyOf(options);
    }

    public static ConsoleConfig empty() {
        return new ConsoleConfig(Map.of());
    }

    public String option(String key, String fallback) {
        return options.getOrDefault(key, fallback);
    }
}
