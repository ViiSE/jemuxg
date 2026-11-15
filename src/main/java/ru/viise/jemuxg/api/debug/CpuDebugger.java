package ru.viise.jemuxg.api.debug;

/**
 * Отладочный доступ к CPU. Не используется эмуляционным ядром,
 * нужен для тестов, дизассемблера и отладчика.
 */
public interface CpuDebugger {

    CpuRegisters registers();

    void setRegisters(CpuRegisters registers);

    void setProgramCounter(int pc);

    /**
     * Читает байт из адресного пространства CPU без изменения состояния.
     */
    int readMemory(int address);

    /**
     * Выполняет ровно одну инструкцию (или обработку прерывания)
     * и возвращает количество затраченных циклов.
     */
    int stepInstruction();
}
