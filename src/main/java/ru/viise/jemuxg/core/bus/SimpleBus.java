package ru.viise.jemuxg.core.bus;

import java.util.ArrayList;
import java.util.List;
import ru.viise.jemuxg.api.hardware.AddressRange;
import ru.viise.jemuxg.api.hardware.Bus;

/**
 * Простая шина: диапазон адресов -> устройство. Первое совпадение выигрывает.
 * Устройство получает абсолютный адрес шины, а не смещение внутри диапазона.
 */
public final class SimpleBus implements Bus {

    private record Mapping(AddressRange range, Bus device) {
    }

    private final List<Mapping> mappings = new ArrayList<>();

    public SimpleBus map(int start, int end, Bus device) {
        return map(new AddressRange(start, end), device);
    }

    public SimpleBus map(AddressRange range, Bus device) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        mappings.add(new Mapping(range, device));
        return this;
    }

    @Override
    public int read(int address) {
        for (Mapping mapping : mappings) {
            if (mapping.range().contains(address)) {
                return mapping.device().read(address);
            }
        }
        return 0xFF;
    }

    @Override
    public void write(int address, int value) {
        for (Mapping mapping : mappings) {
            if (mapping.range().contains(address)) {
                mapping.device().write(address, value);
                return;
            }
        }
    }
}
