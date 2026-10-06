package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.FeatureRegistry;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.object.ObjectGeneratorFactory;
import com.volmit.resource.ResourceId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reference host registry. Single-threaded registration ends with freeze(); the catalog is
 * immutable. IDs are unique across feature kinds; JSON fields are unique per attachment target.
 * Pass existing host JSON fields to reserve them before modules register. Modules register through
 * {@link #register(DiscoveredModule)}; object feature ids, mantle keys and explicitly registered
 * settings ids must be the registering module's id or start with it and '/' ({@link com.volmit.ModuleInfo#ownsId}). A failed registration leaves the registry unchanged. A failed module callback
 * invalidates the host's whole build. This does not decode JSON, discover modules or instantiate
 * settings.
 */
public final class ModuleRegistry implements FeatureRegistry {
    private final Map<Class<?>, FeatureDescriptor<?>> settings = new HashMap<>();
    private final Map<Class<?>, ObjectRegistration<?>> objects = new HashMap<>();
    private final Map<String, MantleRegistration<?>> mantle = new HashMap<>();
    private final Set<ResourceId> featureIds = new HashSet<>();
    private final Set<Class<?>> featureTypes = new HashSet<>();
    private final Set<Class<?>> registeredByOwner = new HashSet<>();
    private final Map<Field, String> fields = new HashMap<>();
    private FeatureCatalog catalog;
    private DiscoveredModule registering;

    public ModuleRegistry() {
        this(Map.of());
    }

    public ModuleRegistry(Map<FeatureAttachment.Target, Set<String>> reservedFields) {
        reserveHostFields(reservedFields);
    }

    /** Lets the module register its contributions through {@link IrisModule#register}. */
    public void register(DiscoveredModule discovered) {
        Objects.requireNonNull(discovered, "discovered");
        checkOpen();
        if (registering != null) {
            throw new IllegalStateException("Module " + discovered.info().id() + " registered during " + registering.info().id());
        }
        registering = discovered;
        try {
            discovered.module().register(this);
        } finally {
            registering = null;
        }
    }

    @Override
    public <F extends IrisFeature> void registerObject(
            Class<F> featureType,
            ObjectGeneratorFactory<F> factory) {
        checkOpen();
        var descriptor = FeatureDescriptor.of(featureType);
        requireOwnedId("Object generator settings " + featureType.getName() + " with feature id", descriptor.id());
        var registration = new ObjectRegistration<>(descriptor, factory);
        claim(descriptor);
        objects.put(featureType, registration);
    }

    @Override
    public <T> void registerMantle(MantleKey<T> key, MantleLifetime lifetime, MantleCodec<T> codec) {
        checkOpen();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(codec, "codec");
        requireOwnedId("Mantle layer", ResourceId.parse(key.id()));

        var registration = new MantleRegistration<>(key, lifetime, codec, codec.version());
        if (mantle.putIfAbsent(key.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate mantle layer: " + key.id());
        }
    }

    @Override
    public void registerSettings(Class<? extends IrisFeature> settingsType) {
        checkOpen();
        var descriptor = FeatureDescriptor.of(settingsType);
        requireOwnedId("Settings " + settingsType.getName() + " with feature id", descriptor.id());
        catalogSettings(descriptor);
        registeredByOwner.add(settingsType);
    }

    /** Catalogs a settings type named by a stage's when(...), whose owner {@link StageGraphBuilder}
     * has checked. Several stages, and the owner's {@link #registerSettings}, may name one type.
     */
    void registerStageSettings(Class<? extends IrisFeature> settingsType) {
        checkOpen();
        if (settings.containsKey(settingsType)) {
            return;
        }
        catalogSettings(FeatureDescriptor.of(settingsType));
    }

    /** Whether the module owning {@code settingsType} registered it through {@link #registerSettings}. */
    boolean registeredByOwner(Class<?> settingsType) {
        return registeredByOwner.contains(settingsType);
    }

    public FeatureCatalog freeze() {
        if (catalog == null) {
            catalog = new FeatureCatalog(settings, objects, mantle);
        }
        return catalog;
    }

    private void catalogSettings(FeatureDescriptor<?> descriptor) {
        if (settings.containsKey(descriptor.type())) {
            return;
        }
        claim(descriptor);
        settings.put(descriptor.type(), descriptor);
    }

    private void requireOwnedId(String registration, ResourceId id) {
        if (registering == null) {
            throw new IllegalStateException(registration + " " + id + " registered outside IrisModule.register");
        }
        var info = registering.info();
        if (info.ownsId(id)) return;
        throw new IllegalArgumentException(registration + " " + id + " must be " + info.id() + " or start with "
                + info.idPrefix() + ", the id of its registering module " + registering);
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
