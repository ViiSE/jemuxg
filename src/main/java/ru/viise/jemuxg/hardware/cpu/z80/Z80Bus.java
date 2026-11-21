package ru.viise.jemuxg.hardware.cpu.z80;

import ru.viise.jemuxg.api.hardware.Bus;

/**
 * Шина Z80: 64 КБ памяти и отдельное пространство 256 портов ввода-вывода.
 *
 * <p>Память использует обычный {@link Bus}. Порты по умолчанию читаются
 * как {@code 0xFF}, а запись игнорируется — этого достаточно для тестов
 * и для машин, у которых ещё нет периферии.</p>
 */
public interface Z80Bus extends Bus {

    default int in(int port) {
        return 0xFF;
    }

    default void out(int port, int value) {
    }
}
