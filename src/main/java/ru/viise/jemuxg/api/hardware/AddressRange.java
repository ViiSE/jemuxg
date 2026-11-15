package ru.viise.jemuxg.api.hardware;

/**
 * Включительный диапазон адресов.
 */
public record AddressRange(int start, int end) {

    public AddressRange {
        if (start < 0) {
            throw new IllegalArgumentException("start must be >= 0, got " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end must be >= start, got " + start + ".." + end);
        }
    }

    public int size() {
        return end - start + 1;
    }

    public boolean contains(int address) {
        return address >= start && address <= end;
    }
}
