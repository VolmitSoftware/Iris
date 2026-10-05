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
    private final Map<Class<?>, GenerationRegistration<?, ?>> generation;
    private final Map<Class<?>, ObjectRegistration<?>> objects;
    private final Map<Class<?>, PlacementRegistration<?>> placement;
    private final Map<String, MantleRegistration<?>> mantle;

    FeatureCatalog(
            Map<Class<?>, GenerationRegistration<?, ?>> generation,
            Map<Class<?>, ObjectRegistration<?>> objects,
            Map<Class<?>, PlacementRegistration<?>> placement,
            Map<String, MantleRegistration<?>> mantle) {
        this.generation = Map.copyOf(generation);
        this.objects = Map.copyOf(objects);
        this.placement = Map.copyOf(placement);
        this.mantle = Map.copyOf(mantle);
    }

    public List<GenerationRegistration<?, ?>> generation() {
        return sorted(generation.values(), registration -> registration.descriptor().id());
    }

    public List<ObjectRegistration<?>> objects() {
        return sorted(objects.values(), registration -> registration.descriptor().id());
    }

    public List<PlacementRegistration<?>> placement() {
        return sorted(placement.values(), registration -> registration.descriptor().id());
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

    @SuppressWarnings("unchecked") // The registry key and output type establish the generic pairing.
    public <F extends IrisFeature, T> GenerationRegistration<F, T> generation(
            Class<F> type,
            Class<T> outputType) {
        var registration = generation.get(type);
        if (registration == null) {
            throw new NoSuchElementException("No generation feature: " + type.getName());
        }
        if (!registration.outputType().equals(outputType)) {
            throw new IllegalArgumentException("Output type mismatch: " + type.getName());
        }
        return (GenerationRegistration<F, T>) registration;
    }

    @SuppressWarnings("unchecked") // The registry key is the factory's feature type.
    public <F extends IrisFeature> ObjectRegistration<F> object(Class<F> type) {
        var registration = objects.get(type);
        if (registration == null) {
            throw new NoSuchElementException("No object feature: " + type.getName());
        }
        return (ObjectRegistration<F>) registration;
    }

    @SuppressWarnings("unchecked") // The registry key is the factory's feature type.
    public <F extends IrisFeature> PlacementRegistration<F> placement(Class<F> type) {
        var registration = placement.get(type);
        if (registration == null) {
            throw new NoSuchElementException("No placement feature: " + type.getName());
        }
        return (PlacementRegistration<F>) registration;
    }
}
