package ru.viise.jemuxg.api.console;

/**
 * Фабрика консолей. Реализации находятся через {@link java.util.ServiceLoader}.
 */
public interface ConsoleProvider {

    ConsoleInfo info();

    Console create(ConsoleConfig config);
}
