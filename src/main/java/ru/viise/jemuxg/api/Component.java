package ru.viise.jemuxg.api;

/**
 * Базовый контракт любого аппаратного компонента.
 */
public interface Component {

    String name();

    void reset();
}
