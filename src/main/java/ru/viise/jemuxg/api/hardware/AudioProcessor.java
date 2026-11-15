package ru.viise.jemuxg.api.hardware;

import ru.viise.jemuxg.api.Clocked;
import ru.viise.jemuxg.api.Component;
import ru.viise.jemuxg.api.Stateful;

/**
 * Звуковой чип (APU, PSG, FM-синтезатор и т.п.).
 */
public interface AudioProcessor extends Component, Clocked, Stateful {

    void connect(AudioSink sink);

    int sampleRate();
}
