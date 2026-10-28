package com.volmit.host;

import com.volmit.FeatureRegistry;
import com.volmit.IrisFeature;
import com.volmit.ModuleLifecycle;
import com.volmit.config.Level;
import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.object.ObjectGeneratorFactory;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.service.Providers;
import com.volmit.validation.Validator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reference host registry. Single-threaded registration ends with freeze(); the catalog is
 * immutable. IDs are unique across feature kinds. A feature's JSON key (see {@link com.volmit.config.Feature#key})
 * is unique per attachment target, and may not equal a host JSON field passed in to reserve it; a clash fails the
 * registration naming the key, the target and both claimants. Modules register during their setup
 * ({@link ModuleSetups#run}); object feature ids, mantle keys, resource kind ids and explicitly registered
 * settings ids must be the registering module's id or start with it and '/' ({@link com.volmit.ModuleInfo#ownsId}).
 * Resource kinds have unique ids, types and folders, and no kind may read a folder the host reserves for its own
 * files. Lifecycle hooks and service providers are registered by the module that owns them; a service whose providers
 * are {@link Providers#ONE} has at most one. A failed registration leaves the registry unchanged. A failed module callback
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
    private final Map<KeyAtTarget, KeyClaimant> keyClaimants = new HashMap<>();
    private final Map<ResourceId, ResourceKindRegistration> resourceKinds = new HashMap<>();
    private final List<ValidatorRegistration> validators = new ArrayList<>();
    private final List<LifecycleRegistration> lifecycles = new ArrayList<>();
    private final List<ServiceRegistration> services = new ArrayList<>();
    private final Set<String> reservedFolders;
    private final List<DiscoveredModule> registered = new ArrayList<>();
    private PlacementTypeRegistration placementType;
    private FeatureCatalog catalog;
    private DiscoveredModule registering;

    public ModuleRegistry() {
        this(Map.of(), Set.of());
    }

    /**
     * @param reservedHostFields JSON fields the host itself uses, per level, which no settings key may take
     * @param reservedFolders folders below the pack root that the host reads itself, which no resource kind may read
     */
    public ModuleRegistry(Map<Level, Set<String>> reservedHostFields, Set<String> reservedFolders) {
        reservedHostFields.forEach((target, names) -> names.forEach(name -> reserveHostField(target, name)));
        this.reservedFolders = Set.copyOf(reservedFolders);
    }

    /** Accepts {@code discovered}'s registrations until {@link #endSetup()}; see {@link ModuleSetups#run}. */
    void beginSetup(DiscoveredModule discovered) {
        Objects.requireNonNull(discovered, "discovered");
        checkOpen();
        if (registering != null) {
            throw new IllegalStateException("Module " + discovered.info().id() + " set up during " + registering.info().id());
        }
        registering = discovered;
        registered.add(discovered);
    }

    void endSetup() {
        registering = null;
    }

    @Override
    public <F extends IrisFeature> void registerObject(
            Class<F> featureType,
            ObjectGeneratorFactory<F> factory) {
        checkOpen();
        var descriptor = FeatureDescriptor.of(featureType);
        requireOwnedId("Object generator settings " + featureType.getName() + " with feature id", descriptor.id());
        var registration = new ObjectRegistration<>(descriptor, factory);
        claim(descriptor, registering);
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
    public void registerSettings(Class<? extends IrisFeature>... settingsTypes) {
        checkOpen();
        for (var settingsType : settingsTypes) {
            registerOneSettings(settingsType);
        }
    }

    private void registerOneSettings(Class<? extends IrisFeature> settingsType) {
        var descriptor = FeatureDescriptor.of(settingsType);
        requireOwnedId("Settings " + settingsType.getName() + " with feature id", descriptor.id());
        catalogSettings(descriptor, registering);
        registeredByOwner.add(settingsType);
    }

    @Override
    public <T> void registerResourceKind(ResourceKind<T> kind) {
        checkOpen();
        Objects.requireNonNull(kind, "kind");
        requireOwnedId("Resource kind", kind.id());
        var registration = new ResourceKindRegistration(kind, registering);
        requireUnclaimedKind(registration);
        resourceKinds.put(kind.id(), registration);
    }

    @Override
    public void registerPlacementType(Class<?> type, Set<String> jsonFields) {
        checkOpen();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(jsonFields, "jsonFields");
        requireRegistering("Placement type " + type.getName());
        var added = new PlacementTypeRegistration(type, jsonFields, registering);
        if (placementType != null) {
            throw new IllegalArgumentException("A pack has one placement type: " + placementType + " declared it already, and "
                    + added + " declares it too; remove one of them");
        }
        added.jsonFields().forEach(FeatureDescriptor::requireFieldName);
        for (var field : added.jsonFields().stream().sorted().toList()) {
            requireUnclaimedPlacementField(field, added);
        }
        added.jsonFields().forEach(field -> keyClaimants.put(new KeyAtTarget(Level.PLACEMENT, field), KeyClaimant.of(added)));
        placementType = added;
    }

    /** A field of the placement type is a JSON key of the placement, which a setting cannot also be. */
    private void requireUnclaimedPlacementField(String field, PlacementTypeRegistration added) {
        var earlier = keyClaimants.get(new KeyAtTarget(Level.PLACEMENT, field));
        if (earlier == null) return;
        var later = KeyClaimant.of(added);
        var first = earlier.compareTo(later) <= 0 ? earlier : later;
        var second = first == earlier ? later : earlier;
        throw new IllegalArgumentException(targetName(Level.PLACEMENT) + " key \"" + field + "\" is claimed by " + first
                + " and " + second + "; give the setting another key in its @Feature");
    }

    @Override
    public void registerValidator(Validator validator) {
        checkOpen();
        Objects.requireNonNull(validator, "validator");
        if (registering == null) {
            throw new IllegalStateException("Validator " + validator + " registered outside IrisModule.setup");
        }
        validators.add(new ValidatorRegistration(validator, registering));
    }

    @Override
    public void registerLifecycle(ModuleLifecycle lifecycle) {
        checkOpen();
        Objects.requireNonNull(lifecycle, "lifecycle");
        requireRegistering("Lifecycle " + lifecycle);
        lifecycles.add(new LifecycleRegistration(lifecycle, registering));
    }

    @Override
    public <S> void registerService(Class<S> service, S provider) {
        checkOpen();
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(provider, "provider");
        requireRegistering("Provider of " + service.getName());
        var providers = FeatureCatalog.providersOf(service);
        if (!service.isInstance(provider)) {
            throw new IllegalArgumentException("Provider " + provider + " of module " + registering + " does not implement "
                    + service.getName());
        }
        var added = new ServiceRegistration(service, provider, registering);
        if (providers == Providers.ONE) {
            requireNoProvider(added);
        }
        services.add(added);
    }

    /** Catalogs a settings type named by a stage's when(...), whose owner {@link StageGraphBuilder}
     * has checked. Several stages, and the owner's {@link #registerSettings}, may name one type. Call
     * it after every module has registered, so the owner is known.
     */
    void registerStageSettings(Class<? extends IrisFeature> settingsType) {
        checkOpen();
        if (settings.containsKey(settingsType)) {
            return;
        }
        var descriptor = FeatureDescriptor.of(settingsType);
        catalogSettings(descriptor, ownerOf(descriptor));
    }

    /** Whether the module owning {@code settingsType} registered it through {@link #registerSettings}. */
    boolean registeredByOwner(Class<?> settingsType) {
        return registeredByOwner.contains(settingsType);
    }

    public FeatureCatalog freeze() {
        if (catalog == null) {
            catalog = new FeatureCatalog(settings, objects, mantle, resourceKinds.values(), validators, lifecycles, services,
                    placementType);
        }
        return catalog;
    }

    private void catalogSettings(FeatureDescriptor<?> descriptor, DiscoveredModule owner) {
        if (settings.containsKey(descriptor.type())) {
            return;
        }
        claim(descriptor, owner);
        settings.put(descriptor.type(), descriptor);
    }

    private DiscoveredModule ownerOf(FeatureDescriptor<?> descriptor) {
        return registered.stream()
                .filter(module -> module.info().ownsId(descriptor.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No registered module owns feature id " + descriptor.id()));
    }

    private void requireOwnedId(String registration, ResourceId id) {
        if (registering == null) {
            throw new IllegalStateException(registration + " " + id + " registered outside IrisModule.setup");
        }
        var info = registering.info();
        if (info.ownsId(id)) return;
        throw new IllegalArgumentException(registration + " " + id + " must be " + info.id() + " or start with "
                + info.idPrefix() + ", the id of its registering module " + registering);
    }

    private void requireRegistering(String registration) {
        if (registering == null) {
            throw new IllegalStateException(registration + " registered outside IrisModule.setup");
        }
    }

    /** A service a pack has one provider of: a second provider, from this module or another, fails. */
    private void requireNoProvider(ServiceRegistration added) {
        for (var existing : services) {
            if (existing.service() == added.service()) {
                throw new IllegalArgumentException("Service " + added.service().getName() + " is provided by module "
                        + existing.module() + " already, and module " + added.module() + " provides it too; a pack has"
                        + " one provider of it (Providers.ONE), so remove one of them");
            }
        }
    }

    /** One id, one type and one folder per kind: the host finds resources by type and reads each folder as one kind. */
    private void requireUnclaimedKind(ResourceKindRegistration added) {
        var kind = added.kind();
        if (resourceKinds.containsKey(kind.id())) {
            throw new IllegalArgumentException("Duplicate resource kind: " + kind.id() + " of module " + added.module());
        }
        if (reservedFolders.contains(kind.folder())) {
            throw new IllegalArgumentException("Resource kind " + kind.id() + " of module " + added.module() + " reads folder '"
                    + kind.folder() + "', which the host reads itself; give the kind a folder of its own");
        }
        for (var existing : resourceKinds.values()) {
            if (existing.kind().type() == kind.type()) {
                throw new IllegalArgumentException(added + " reads into " + kind.type().getName() + ", which " + existing
                        + " reads into already; resources are found by their type, so each kind needs a class of its own");
            }
            if (existing.kind().folder().equals(kind.folder())) {
                throw new IllegalArgumentException(added + " reads folder '" + kind.folder() + "', which " + existing
                        + " reads already; a folder holds one kind, so give one of them a folder of its own");
            }
        }
    }

    private void reserveHostField(Level target, String name) {
        keyClaimants.put(new KeyAtTarget(target, FeatureDescriptor.requireFieldName(name)), KeyClaimant.hostField());
    }

    private void claim(FeatureDescriptor<?> descriptor, DiscoveredModule owner) {
        requireNewFeature(descriptor);
        requireUnclaimedKeys(descriptor, owner);
        featureTypes.add(descriptor.type());
        featureIds.add(descriptor.id());
        descriptor.targets().forEach(target ->
                keyClaimants.put(new KeyAtTarget(target, descriptor.key()), KeyClaimant.of(descriptor, owner)));
    }

    private void requireNewFeature(FeatureDescriptor<?> descriptor) {
        if (featureTypes.contains(descriptor.type()) || featureIds.contains(descriptor.id())) {
            throw new IllegalArgumentException("Duplicate feature: " + descriptor.id());
        }
    }

    /** One JSON key means one setting at a target: the pack's JSON could not say which one it meant. The message
     * names the claimants in a fixed order, so it does not depend on which module registered first.
     */
    private void requireUnclaimedKeys(FeatureDescriptor<?> descriptor, DiscoveredModule owner) {
        for (var target : descriptor.targets()) {
            var earlier = keyClaimants.get(new KeyAtTarget(target, descriptor.key()));
            if (earlier == null) continue;
            var later = KeyClaimant.of(descriptor, owner);
            var first = earlier.compareTo(later) <= 0 ? earlier : later;
            var second = first == earlier ? later : earlier;
            throw new IllegalArgumentException(targetName(target) + " key \"" + descriptor.key() + "\" is claimed by " + first
                    + " and " + second + "; give one of the settings another key in its @Feature");
        }
    }

    private static String targetName(Level target) {
        return target.name().toLowerCase(Locale.ROOT);
    }

    private void checkOpen() {
        if (catalog != null) {
            throw new IllegalStateException("Registration has ended");
        }
    }

    private record KeyAtTarget(Level target, String key) {}

    /** Who holds a key: a field of the host's own JSON, a built-in module's feature or a pack module's, in that order. */
    private record KeyClaimant(Source source, String feature, String text) implements Comparable<KeyClaimant> {
        private enum Source { HOST_FIELD, BUILT_IN, PACK }

        static KeyClaimant hostField() {
            return new KeyClaimant(Source.HOST_FIELD, "", "Iris");
        }

        static KeyClaimant of(FeatureDescriptor<?> descriptor, DiscoveredModule owner) {
            return new KeyClaimant(sourceOf(owner), descriptor.id().toString(), descriptor.id() + " (" + owner.origin() + ")");
        }

        static KeyClaimant of(PlacementTypeRegistration placementType) {
            var owner = placementType.module();
            return new KeyClaimant(sourceOf(owner), placementType.type().getName(),
                    "the fields of " + placementType.type().getName() + " (" + owner.origin() + ")");
        }

        private static Source sourceOf(DiscoveredModule owner) {
            return owner.source() == DiscoveredModule.Source.HOST ? Source.BUILT_IN : Source.PACK;
        }

        @Override
        public int compareTo(KeyClaimant other) {
            var bySource = source.compareTo(other.source);
            return bySource != 0 ? bySource : feature.compareTo(other.feature);
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
