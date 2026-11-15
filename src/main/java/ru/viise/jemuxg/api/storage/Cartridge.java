package ru.viise.jemuxg.api.storage;

/**
 * Плата картриджа: носитель + маппер (+ при необходимости дополнительный hardware).
 */
public interface Cartridge extends Storage {

    Mapper mapper();
}
