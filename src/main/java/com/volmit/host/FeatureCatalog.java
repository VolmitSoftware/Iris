package com.volmit.host;

import com.volmit.IrisFeature;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

/** Frozen registrations. Lists use lexical feature/layer ID order, independent of discovery.
 * Factory and codec implementations must themselves obey their concurrency contracts.
 */
public final class FeatureCatalog {
    private final Map<Class<?>, FeatureDescriptor<?>> settings;
    private final Map<Class<?>, ObjectRegistration<?>> objects;
    private final Map<String, MantleRegistration<?>> mantle;

    FeatureCatalog(
            Map<Class<?>, FeatureDescriptor<?>> settings,
            Map<Class<?>, ObjectRegistration<?>> objects,
            Map<String, MantleRegistration<?>> mantle) {
        this.settings = Map.copyOf(settings);
        this.objects = Map.copyOf(objects);
        this.mantle = Map.copyOf(mantle);
    }

    /** Settings types that activate stages; the host decodes them from pack JSON. */
    public List<FeatureDescriptor<?>> settings() {
        return sorted(settings.values(), FeatureDescriptor::id);
    }

    public List<ObjectRegistration<?>> objects() {
        return sorted(objects.values(), registration -> registration.descriptor().id());
    }

    public List<MantleRegistration<?>> mantle() {
        return sorted(mantle.values(), registration -> registration.key().id());
    }

    private static <T, K extends Comparable<? super K>> List<T> sorted(
            Collection<T> registrations,
            Function<? super T, ? extends K> key) {
        return registrations.stream()
                .sorted(Comparator.comparing(key))
                .toList();
    }

    @SuppressWarnings("unchecked") // The registry key is the factory's feature type.
    public <F extends IrisFeature> ObjectRegistration<F> object(Class<F> type) {
        var registration = objects.get(type);
        if (registration == null) {
            throw new NoSuchElementException("No object feature: " + type.getName());
        }
        return (ObjectRegistration<F>) registration;
    }
}
