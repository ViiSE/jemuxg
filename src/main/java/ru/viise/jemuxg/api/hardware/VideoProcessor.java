package ru.viise.jemuxg.api.hardware;

import ru.viise.jemuxg.api.Clocked;
import ru.viise.jemuxg.api.Component;
import ru.viise.jemuxg.api.Stateful;

/**
 * Видеопроцессор: PPU, VDP, GPU и т.п.
 */
public interface VideoProcessor extends Component, Clocked, Stateful {

    void connect(VideoSink sink);

    int width();

    int height();
}
