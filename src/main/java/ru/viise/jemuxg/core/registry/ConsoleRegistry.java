package ru.viise.jemuxg.core.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import ru.viise.jemuxg.api.console.ConsoleProvider;

/**
 * Реестр консолей. Реализации подтягиваются через {@link ServiceLoader},
 * поэтому новая консоль = новый пакет + строка в META-INF/services.
 */
public final class ConsoleRegistry {

    private final Map<String, ConsoleProvider> providers;

    private ConsoleRegistry(Map<String, ConsoleProvider> providers) {
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers));
    }

    public static ConsoleRegistry load() {
        Map<String, ConsoleProvider> found = new LinkedHashMap<>();
        for (ConsoleProvider provider : ServiceLoader.load(ConsoleProvider.class)) {
            found.put(provider.info().id(), provider);
        }
        return new ConsoleRegistry(found);
    }

    public static ConsoleRegistry of(ConsoleProvider... providers) {
        Map<String, ConsoleProvider> found = new LinkedHashMap<>();
        for (ConsoleProvider provider : providers) {
            found.put(provider.info().id(), provider);
        }
        return new ConsoleRegistry(found);
    }

    public ConsoleProvider require(String id) {
        ConsoleProvider provider = providers.get(id);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown console '" + id + "', available: " + providers.keySet());
        }
        return provider;
    }

    public Collection<ConsoleProvider> all() {
        return providers.values();
    }
}
