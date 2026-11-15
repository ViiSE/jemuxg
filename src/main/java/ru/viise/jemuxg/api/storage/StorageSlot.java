package ru.viise.jemuxg.api.storage;

import java.util.Optional;

/**
 * Слот на корпусе консоли, в который вставляется носитель.
 */
public interface StorageSlot {

    String id();

    boolean isPresent();

    Optional<Storage> storage();

    void insert(Media media);

    Media eject();
}
