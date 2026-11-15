package ru.viise.jemuxg.hardware.riot.riot6532;

import ru.viise.jemuxg.api.Clocked;
import ru.viise.jemuxg.api.Component;
import ru.viise.jemuxg.api.Stateful;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Memory;

/**
 * RIOT 6532: 128 байт RAM, программируемый таймер и два порта ввода-вывода.
 *
 * <p>RAM и регистры RIOT занимают разные окна в адресном пространстве 6507:
 * {@code $0080..$00FF} и {@code $0280..$029F} соответственно.</p>
 */
public interface Riot6532 extends Component, Clocked, Stateful {

    /**
     * @return шина регистров RIOT
     */
    Bus registers();

    /**
     * @return встроенная RAM RIOT
     */
    Memory ram();
}
