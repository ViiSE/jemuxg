package ru.viise.jemuxg.console.nes.debug;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.viise.jemuxg.api.debug.CpuDebugger;
import ru.viise.jemuxg.api.debug.CpuRegisters;
import ru.viise.jemuxg.api.storage.MediaKind;
import ru.viise.jemuxg.console.nes.NesMachine;
import ru.viise.jemuxg.storage.media.FileMedia;

/**
 * Сравнение выполнения nestest с эталонным логом.
 * <p>
 * Nestest — диагностический ROM, который последовательно исполняет
 * инструкции CPU и пишет их состояние в PRG RAM. Эталонный лог содержит
 * состояние перед каждой инструкцией, байты опкода и счётчик циклов.
 * <p>
 * Сравниваются PC, A, X, Y, P, SP, байты инструкции и накопленные циклы.
 * PPU-циклы из лога игнорируются.
 */
public final class NestestRunner {

    private static final int START_PC = 0xC000;
    private static final int START_SP = 0xFD;
    private static final int START_STATUS = 0x24;
    private static final int RESET_CYCLES = 7;

    private static final Pattern LINE = Pattern.compile(
            "^([0-9A-F]{4})\\s+"
                    + "([0-9A-F]{2}(?:\\s+[0-9A-F]{2}){0,2})\\s+"
                    + ".*?A:([0-9A-F]{2})\\s+X:([0-9A-F]{2})\\s+Y:([0-9A-F]{2})\\s+"
                    + "P:([0-9A-F]{2})\\s+SP:([0-9A-F]{2})\\s+PPU:.*?CYC:(\\d+)\\s*$");

    private NestestRunner() {
    }

    public record Result(int totalLines, int matchedLines, int mismatchLine, String message) {

        public boolean passed() {
            return mismatchLine == 0;
        }
    }

    public static Result run(Path romPath, Path logPath) throws IOException {
        return run(romPath, logPath, null);
    }

    /**
     * @param actualLogPath optional path where the actual CPU execution log is written;
     *                      {@code null} disables logging
     */
    public static Result run(Path romPath, Path logPath, Path actualLogPath) throws IOException {
        try (FileMedia media = FileMedia.open(romPath, MediaKind.CARTRIDGE)) {
            NesMachine machine = new NesMachine();
            machine.insert(0, media);
            return run(machine, logPath, actualLogPath);
        }
    }

    public static Result run(NesMachine machine, Path logPath) throws IOException {
        return run(machine, logPath, null);
    }

    public static Result run(NesMachine machine, Path logPath, Path actualLogPath) throws IOException {
        Objects.requireNonNull(machine, "machine");
        List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
        CpuDebugger cpu = machine.debugCpu();

        machine.reset();
        cpu.setRegisters(new CpuRegisters(0, 0, 0, START_SP, START_PC, START_STATUS));

        try (BufferedWriter actualLog = openActualLog(actualLogPath)) {
            if (actualLog != null) {
                actualLog.write("# jemuxg nestest CPU log: PC, opcode bytes, A, X, Y, P, SP, cumulative CYC");
                actualLog.newLine();
            }

            long actualCycles = RESET_CYCLES;
            int matched = 0;
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank()) {
                    continue;
                }

                ParsedLine expected = parse(line, index + 1);
                CpuRegisters actual = cpu.registers();
                if (actualLog != null) {
                    writeActualLine(actualLog, actual, cpu, expected.bytes().length, actualCycles);
                }

                String mismatch = compare(expected, actual, cpu);
                if (mismatch != null) {
                    return new Result(lines.size(), matched, index + 1,
                            "line " + (index + 1) + ": " + mismatch);
                }

                if (actualCycles != expected.cycles()) {
                    return new Result(lines.size(), matched, index + 1,
                            "line " + (index + 1) + ": CYC expected "
                                    + expected.cycles() + ", got " + actualCycles);
                }

                try {
                    actualCycles += cpu.stepInstruction();
                } catch (RuntimeException e) {
                    return new Result(lines.size(), matched, index + 1,
                            "line " + (index + 1) + ": CPU exception: " + e.getMessage());
                }
                matched++;
            }

            return new Result(lines.size(), matched, 0, "nestest passed");
        }
    }

    private static BufferedWriter openActualLog(Path path) throws IOException {
        if (path == null) {
            return null;
        }
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void writeActualLine(
            BufferedWriter writer,
            CpuRegisters registers,
            CpuDebugger cpu,
            int byteCount,
            long cycles) throws IOException {
        StringBuilder bytes = new StringBuilder(byteCount * 3);
        for (int i = 0; i < byteCount; i++) {
            if (i > 0) {
                bytes.append(' ');
            }
            bytes.append(hex(cpu.readMemory((registers.pc() + i) & 0xFFFF), 2));
        }
        writer.write(String.format(
                "%04X  %-8s  A:%02X X:%02X Y:%02X P:%02X SP:%02X CYC:%d",
                registers.pc(),
                bytes,
                registers.a(),
                registers.x(),
                registers.y(),
                registers.status(),
                registers.sp(),
                cycles));
        writer.newLine();
    }

    private static ParsedLine parse(String line, int lineNumber) {
        Matcher matcher = LINE.matcher(line);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("unsupported nestest log line " + lineNumber + ": " + line);
        }

        String[] byteStrings = matcher.group(2).trim().split("\\s+");
        int[] bytes = new int[byteStrings.length];
        for (int i = 0; i < byteStrings.length; i++) {
            bytes[i] = Integer.parseInt(byteStrings[i], 16);
        }

        return new ParsedLine(
                Integer.parseInt(matcher.group(1), 16),
                bytes,
                Integer.parseInt(matcher.group(3), 16),
                Integer.parseInt(matcher.group(4), 16),
                Integer.parseInt(matcher.group(5), 16),
                Integer.parseInt(matcher.group(6), 16),
                Integer.parseInt(matcher.group(7), 16),
                Integer.parseInt(matcher.group(8)));
    }

    private static String compare(ParsedLine expected, CpuRegisters actual, CpuDebugger cpu) {
        if (actual.pc() != expected.pc()) {
            return "PC expected " + hex(expected.pc(), 4) + ", got " + hex(actual.pc(), 4);
        }
        if (actual.a() != expected.a()) {
            return "A expected " + hex(expected.a(), 2) + ", got " + hex(actual.a(), 2);
        }
        if (actual.x() != expected.x()) {
            return "X expected " + hex(expected.x(), 2) + ", got " + hex(actual.x(), 2);
        }
        if (actual.y() != expected.y()) {
            return "Y expected " + hex(expected.y(), 2) + ", got " + hex(actual.y(), 2);
        }
        if (actual.status() != expected.status()) {
            return "P expected " + hex(expected.status(), 2) + ", got " + hex(actual.status(), 2);
        }
        if (actual.sp() != expected.sp()) {
            return "SP expected " + hex(expected.sp(), 2) + ", got " + hex(actual.sp(), 2);
        }

        for (int i = 0; i < expected.bytes().length; i++) {
            int address = (expected.pc() + i) & 0xFFFF;
            int actualByte = cpu.readMemory(address);
            if (actualByte != expected.bytes()[i]) {
                return "opcode byte " + i + " at " + hex(address, 4)
                        + " expected " + hex(expected.bytes()[i], 2)
                        + ", got " + hex(actualByte, 2);
            }
        }
        return null;
    }

    private static String hex(int value, int digits) {
        return String.format("%0" + digits + "X", value);
    }

    private record ParsedLine(
            int pc,
            int[] bytes,
            int a,
            int x,
            int y,
            int status,
            int sp,
            int cycles) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 4) {
            System.err.println("usage: NestestRunner <nestest.nes> <nestest.log>"
                    + " [--write-log <actual.log>]");
            System.exit(2);
        }
        if (args.length == 4 && !"--write-log".equals(args[2])) {
            System.err.println("unknown option: " + args[2]);
            System.err.println("usage: NestestRunner <nestest.nes> <nestest.log>"
                    + " [--write-log <actual.log>]");
            System.exit(2);
        }

        Path actualLog = args.length == 4 ? Path.of(args[3]) : null;
        Result result = run(Path.of(args[0]), Path.of(args[1]), actualLog);
        System.out.printf("nestest: %d/%d instructions, %s%n",
                result.matchedLines(), result.totalLines(), result.message());
        if (actualLog != null) {
            System.out.println("actual log: " + actualLog.toAbsolutePath().normalize());
        }
        if (!result.passed()) {
            System.exit(1);
        }
    }
}
