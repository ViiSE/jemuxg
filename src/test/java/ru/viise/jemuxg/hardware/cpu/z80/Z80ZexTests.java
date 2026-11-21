package ru.viise.jemuxg.hardware.cpu.z80;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Опциональный запуск внешних Z80 conformance ROM: zexdoc/zexall.
 *
 * <p>ROM не входят в репозиторий. Пути передаются аргументами или через
 * {@code -Djemuxg.z80.zex=path1,path2}. Если путей нет, тест пропускается,
 * поэтому обычная сборка не требует внешних файлов.</p>
 *
 * <p>Мини-харнесс эмулирует только BDOS CP/M: вывод строки (C=9),
 * вывод символа (C=2), чтение консоли (C=1) и завершение (C=0).</p>
 */
public final class Z80ZexTests {

    private static final int BDOS = 0x0005;
    private static final int TPA = 0x0100;
    private static final long DEFAULT_MAX_STEPS = 2_000_000_000L;

    private Z80ZexTests() {
    }

    public static void main(String[] args) throws Exception {
        List<Path> roms = roms(args);
        if (roms.isEmpty()) {
            System.out.println("Z80 zex tests skipped: "
                    + "pass zexdoc.com/zexall.com paths or -Djemuxg.z80.zex=...");
            return;
        }
        long maxSteps = Long.getLong("jemuxg.z80.zex.maxSteps", DEFAULT_MAX_STEPS);
        for (Path rom : roms) {
            run(rom, maxSteps);
        }
        System.out.println("Z80 zex tests OK, roms=" + roms.size());
    }

    private static List<Path> roms(String[] args) {
        List<Path> roms = new ArrayList<>();
        if (args != null) {
            for (String arg : args) {
                if (arg != null && !arg.isBlank()) {
                    roms.add(Path.of(arg));
                }
            }
        }
        String property = System.getProperty("jemuxg.z80.zex");
        if (property != null && !property.isBlank()) {
            for (String part : property.split(",")) {
                if (!part.isBlank()) {
                    roms.add(Path.of(part.trim()));
                }
            }
        }
        return roms;
    }

    private static void run(Path rom, long maxSteps) throws Exception {
        byte[] program = Files.readAllBytes(rom);
        if (program.length > 0x10000 - TPA) {
            throw new IllegalArgumentException("Z80 test ROM is too large: " + rom);
        }

        byte[] memory = new byte[0x10000];
        System.arraycopy(program, 0, memory, TPA, program.length);
        memory[0] = (byte) 0xC9;
        memory[BDOS] = (byte) 0xC9;

        Z80Bus bus = new Z80Bus() {
            @Override
            public int read(int address) {
                return memory[address & 0xFFFF] & 0xFF;
            }

            @Override
            public void write(int address, int value) {
                memory[address & 0xFFFF] = (byte) value;
            }
        };
        Z80 cpu = new Z80(bus);
        cpu.registers().sp = TPA;
        cpu.registers().pc = TPA;

        StringBuilder output = new StringBuilder();
        long steps = 0;
        boolean complete = false;
        while (steps < maxSteps) {
            int pc = cpu.registers().pc;
            if (pc == 0) {
                break;
            }
            if (pc == BDOS) {
                int function = cpu.registers().c;
                if (function == 0) {
                    complete = true;
                    break;
                }
                if (function == 9) {
                    int address = cpu.registers().de();
                    while (true) {
                        int ch = memory[address & 0xFFFF] & 0xFF;
                        if (ch == '$') {
                            break;
                        }
                        output.append((char) ch);
                        address = (address + 1) & 0xFFFF;
                    }
                } else if (function == 2) {
                    output.append((char) (cpu.registers().e & 0xFF));
                } else if (function == 1) {
                    cpu.registers().a = 0x1A;
                } else if (function == 11) {
                    cpu.registers().a = 0;
                }
                int sp = cpu.registers().sp;
                int returnAddress = (memory[sp & 0xFFFF] & 0xFF)
                        | ((memory[(sp + 1) & 0xFFFF] & 0xFF) << 8);
                cpu.registers().sp = (sp + 2) & 0xFFFF;
                cpu.registers().pc = returnAddress;
                complete = output.indexOf("Tests complete") >= 0;
                if (complete) {
                    break;
                }
                continue;
            }
            cpu.step();
            steps++;
        }

        System.out.print(output);
        if (!output.isEmpty() && output.charAt(output.length() - 1) != '\n') {
            System.out.println();
        }
        if (!complete) {
            throw new AssertionError("FAILED: " + rom.getFileName()
                    + " did not complete in " + steps + " steps");
        }
        if (output.indexOf("ERROR") >= 0) {
            throw new AssertionError("FAILED: " + rom.getFileName() + " reported ERROR");
        }
    }
}
