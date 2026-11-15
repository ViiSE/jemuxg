package ru.viise.jemuxg.api.debug;

/**
 * Снимок регистров CPU. B и U биты статуса не нормализуются — это делает CPU.
 */
public record CpuRegisters(int a, int x, int y, int sp, int pc, int status) {
}
