package ru.viise.jemuxg.hardware.riot.riot6532;

import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.Memory;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;
import ru.viise.jemuxg.core.memory.Ram;

/**
 * 128-байтная RAM RIOT 6532.
 *
 * <p>Адрес маскируется до 7 бит, поэтому обращения к $0080-$00FF
 * (и любым зеркалам в адресном пространстве) попадают в одну и ту же
 * страницу памяти.</p>
 */
public final class RiotRam implements Memory, Stateful {

    private final Ram memory = new Ram(Atari2600Constants.RIOT_RAM_SIZE);

    @Override
    public int size() {
        return memory.size();
    }

    @Override
    public int read(int address) {
        return memory.read(address & Atari2600Constants.RIOT_RAM_OFFSET_MASK);
    }

    @Override
    public void write(int address, int value) {
        memory.write(address & Atari2600Constants.RIOT_RAM_OFFSET_MASK, value);
    }

    public void clear() {
        memory.clear();
    }

    /**
     * Копия содержимого RAM для отладочных снимков.
     */
    public byte[] snapshot() {
        return memory.snapshot();
    }

    @Override
    public void save(StateWriter writer) {
        memory.save(writer);
    }

    @Override
    public void restore(StateReader reader) {
        memory.restore(reader);
    }
}
