package ru.viise.jemuxg.hardware.video.tia;

/**
 * TIA, пригодная для подключения к {@code Atari2600Machine}.
 *
 * <p>Кроме общего {@link TiaVideo} контракта отладочному слою нужны
 * framebuffer и снимок регистров. Интерфейс реализует
 * Stella-совместимый слой {@link StellaTiaVideo}.</p>
 */
public interface AtariTiaDevice extends TiaVideo {

    long frameCount();

    int[] framebufferArgb();

    byte[] framebufferIndices();

    byte[] registerSnapshot();
}
