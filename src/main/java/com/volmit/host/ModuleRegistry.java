package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.FeatureRegistry;
import com.volmit.IrisFeature;
import com.volmit.config.Feature;
import com.volmit.config.OptionsMetadata;
import com.volmit.generation.ModifierFactory;
import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.object.ObjectGeneratorFactory;
import com.volmit.placement.PlacementModifierFactory;
import com.volmit.resource.ResourceId;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reference host registry. Single-threaded registration ends with freeze(); the catalog is
 * immutable. IDs are unique across feature kinds; JSON fields are unique per attachment target.
 * Pass existing host JSON fields to reserve them before modules register. A failed registration
 * leaves the registry unchanged. A failed module callback invalidates the host's whole build.
 * This does not decode JSON, discover modules or instantiate settings.
 */
public final class ModuleRegistry implements FeatureRegistry {
    private final Map<Class<?>, GenerationRegistration<?, ?>> generation = new HashMap<>();
    private final Map<Class<?>, ObjectRegistration<?>> objects = new HashMap<>();
    private final Map<Class<?>, PlacementRegistration<?>> placement = new HashMap<>();
    private final Map<String, MantleRegistration<?>> mantle = new HashMap<>();
    private final Set<ResourceId> featureIds = new HashSet<>();
    private final Set<Class<?>> featureTypes = new HashSet<>();
    private final Map<Field, String> fields = new HashMap<>();
    private FeatureCatalog catalog;

    public ModuleRegistry() {
        this(Map.of());
    }

    public ModuleRegistry(Map<FeatureAttachment.Target, Set<String>> reservedFields) {
        reserveHostFields(reservedFields);
    }

    @Override
    public <F extends IrisFeature, T> void register(
            Class<F> featureType,
            Class<T> outputType,
            ModifierFactory<F, T> factory) {
        var descriptor = describe(featureType);
        var registration = new GenerationRegistration<>(descriptor, outputType, factory);
        claim(descriptor);
        generation.put(featureType, registration);
    }

    @Override
    public <F extends IrisFeature> void registerObject(
            Class<F> featureType,
            ObjectGeneratorFactory<F> factory) {
        var descriptor = describe(featureType);
        var registration = new ObjectRegistration<>(descriptor, factory);
        claim(descriptor);
        objects.put(featureType, registration);
    }

    @Override
    public <F extends IrisFeature> void registerPlacement(
            Class<F> featureType,
            PlacementModifierFactory<F> factory) {
        var descriptor = describe(featureType);
        if (!isPlacementFeature(descriptor)) {
            throw new IllegalArgumentException(
                    "Placement modifiers must attach only to OBJECT_PLACEMENT: " + descriptor.id());
        }

        var registration = new PlacementRegistration<>(descriptor, factory);
        claim(descriptor);
        placement.put(featureType, registration);
    }

    @Override
    public <T> void registerMantle(MantleKey<T> key, MantleLifetime lifetime, MantleCodec<T> codec) {
        checkOpen();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(codec, "codec");

        var registration = new MantleRegistration<>(key, lifetime, codec, codec.version());
        if (mantle.putIfAbsent(key.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate mantle layer: " + key.id());
        }
    }

    public FeatureCatalog freeze() {
        if (catalog == null) {
            catalog = new FeatureCatalog(generation, objects, placement, mantle);
        }
        return catalog;
    }

    private void reserveHostFields(Map<FeatureAttachment.Target, Set<String>> reservedFields) {
        for (var entry : reservedFields.entrySet()) {
            for (var name : entry.getValue()) {
                reserveHostField(entry.getKey(), name);
            }
        }
    }

    private void reserveHostField(FeatureAttachment.Target target, String name) {
        new FeatureAttachment(name, Set.of(target));
        fields.put(new Field(target, name), "host");
    }

    private <F extends IrisFeature> FeatureDescriptor<F> describe(Class<F> type) {
        checkOpen();
        Objects.requireNonNull(type, "featureType");
        validateFeatureType(type);

        var annotation = type.getDeclaredAnnotation(Feature.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Missing @Feature: " + type.getName());
        }

        return new FeatureDescriptor<>(
                ResourceId.parse(annotation.id()),
                annotation.name(),
                type,
                new FeatureAttachment(annotation.field(), Set.of(annotation.targets())),
                OptionsMetadata.inspect(type));
    }

    private static void validateFeatureType(Class<? extends IrisFeature> type) {
        if (!IrisFeature.class.isAssignableFrom(type)
                || type.isInterface()
                || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException(
                    "Feature type must be a concrete IrisFeature: " + type.getName());
        }
    }

    private static boolean isPlacementFeature(FeatureDescriptor<?> descriptor) {
        return descriptor.attachment().targets().equals(Set.of(FeatureAttachment.Target.OBJECT_PLACEMENT));
    }

    private void claim(FeatureDescriptor<?> descriptor) {
        validateClaim(descriptor);
        recordClaim(descriptor);
    }

    private void validateClaim(FeatureDescriptor<?> descriptor) {
        if (featureTypes.contains(descriptor.type()) || featureIds.contains(descriptor.id())) {
            throw new IllegalArgumentException("Duplicate feature: " + descriptor.id());
        }

        for (var target : descriptor.attachment().targets()) {
            var field = new Field(target, descriptor.attachment().field());
            if (fields.containsKey(field)) {
                throw new IllegalArgumentException(
                        "JSON field collision at " + field + ": " + fields.get(field));
            }
        }
    }

    private void recordClaim(FeatureDescriptor<?> descriptor) {
        featureTypes.add(descriptor.type());
        featureIds.add(descriptor.id());
        for (var target : descriptor.attachment().targets()) {
            var field = new Field(target, descriptor.attachment().field());
            fields.put(field, descriptor.id().toString());
        }
    }

    private void checkOpen() {
        if (catalog != null) {
            throw new IllegalStateException("Registration has ended");
        }
    }

    private record Field(FeatureAttachment.Target target, String name) {}
}
