package ru.viise.jemuxg.api.hardware;

/**
 * Аналоговый контроллер- paddle (Atari 2600).
 *
 * <p>Позиция задаётся в диапазоне {@code 0..1000}; конкретная реализация
 * может управлять ею клавиатурой, мышью или внешним устройством.</p>
 */
public interface PaddleController {

    /**
     * @return положение ручки от 0 (одно крайнее) до 1000 (другое)
     */
    int paddlePosition();

    /**
     * Режим paddle: кнопка fire дополнительно замыкает trigger-линию SWCHA,
     * как этого ожидают некоторые игры с paddle-управлением.
     */
    default boolean paddleMode() {
        return false;
    }
}
