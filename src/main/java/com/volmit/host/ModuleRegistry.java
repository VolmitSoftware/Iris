package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.FeatureRegistry;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleLifecycle;
import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.object.ObjectGeneratorFactory;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.service.Providers;
import com.volmit.validation.Validator;
import com.volmit.vanilla.VanillaStructureHook;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reference host registry. Single-threaded registration ends with freeze(); the catalog is
 * immutable. IDs are unique across feature kinds, and every feature id is its JSON key. Aliases,
 * which only built-in modules may set, are unique per attachment target and may not equal the
 * host JSON fields passed in to reserve them. Modules register through
 * {@link #register(DiscoveredModule)}; object feature ids, mantle keys, resource kind ids and explicitly registered
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
    private final Map<Field, String> fields = new HashMap<>();
    private final Map<ResourceId, ResourceKindRegistration> resourceKinds = new HashMap<>();
    private final List<ValidatorRegistration> validators = new ArrayList<>();
    private final List<LifecycleRegistration> lifecycles = new ArrayList<>();
    private final List<ServiceRegistration> services = new ArrayList<>();
    private final List<VanillaStructureHookRegistration> vanillaStructureHooks = new ArrayList<>();
    private final Set<String> reservedFolders;
    private final List<DiscoveredModule> registered = new ArrayList<>();
    private FeatureCatalog catalog;
    private DiscoveredModule registering;

    public ModuleRegistry() {
        this(Map.of());
    }

    public ModuleRegistry(Map<FeatureAttachment.Target, Set<String>> reservedFields) {
        this(reservedFields, Set.of());
    }

    /** @param reservedFolders folders below the pack root that the host reads itself, which no resource kind may read */
    public ModuleRegistry(Map<FeatureAttachment.Target, Set<String>> reservedFields, Set<String> reservedFolders) {
        reserveHostFields(reservedFields);
        this.reservedFolders = Set.copyOf(reservedFolders);
    }

    /** Lets the module register its contributions through {@link IrisModule#register}. */
    public void register(DiscoveredModule discovered) {
        Objects.requireNonNull(discovered, "discovered");
        checkOpen();
        if (registering != null) {
            throw new IllegalStateException("Module " + discovered.info().id() + " registered during " + registering.info().id());
        }
        registering = discovered;
        registered.add(discovered);
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
    public void registerSettings(Class<? extends IrisFeature> settingsType) {
        checkOpen();
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
    public void registerValidator(Validator validator) {
        checkOpen();
        Objects.requireNonNull(validator, "validator");
        if (registering == null) {
            throw new IllegalStateException("Validator " + validator + " registered outside IrisModule.register");
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
        var providers = FeatureCatalog.requireService(service).value();
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

    @Override
    public void registerVanillaStructureHook(VanillaStructureHook hook) {
        checkOpen();
        Objects.requireNonNull(hook, "hook");
        requireRegistering("Vanilla structure hook " + hook);
        vanillaStructureHooks.add(new VanillaStructureHookRegistration(hook, registering));
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
                    vanillaStructureHooks);
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
            throw new IllegalStateException(registration + " " + id + " registered outside IrisModule.register");
        }
        var info = registering.info();
        if (info.ownsId(id)) return;
        throw new IllegalArgumentException(registration + " " + id + " must be " + info.id() + " or start with "
                + info.idPrefix() + ", the id of its registering module " + registering);
    }

    private void requireRegistering(String registration) {
        if (registering == null) {
            throw new IllegalStateException(registration + " registered outside IrisModule.register");
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

    private void reserveHostFields(Map<FeatureAttachment.Target, Set<String>> reservedFields) {
        for (var entry : reservedFields.entrySet()) {
            for (var name : entry.getValue()) {
                reserveHostField(entry.getKey(), name);
            }
        }
    }

    private void reserveHostField(FeatureAttachment.Target target, String name) {
        fields.put(new Field(target, FeatureAttachment.requireFieldName(name)), "host");
    }

    private void claim(FeatureDescriptor<?> descriptor, DiscoveredModule owner) {
        validateClaim(descriptor, owner);
        recordClaim(descriptor);
    }

    private void validateClaim(FeatureDescriptor<?> descriptor, DiscoveredModule owner) {
        if (featureTypes.contains(descriptor.type()) || featureIds.contains(descriptor.id())) {
            throw new IllegalArgumentException("Duplicate feature: " + descriptor.id());
        }
        descriptor.alias().ifPresent(alias -> validateAlias(descriptor, alias, owner));
    }

    private void validateAlias(FeatureDescriptor<?> descriptor, String alias, DiscoveredModule owner) {
        if (owner.source() == DiscoveredModule.Source.PACK) {
            throw new IllegalArgumentException("Settings " + descriptor.type().getName() + " of module " + owner
                    + " set alias '" + alias + "'; only modules that ship with the host may set one, so remove it:"
                    + " packs write these settings under their id \"" + descriptor.key() + "\"");
        }
        for (var target : descriptor.attachment().targets()) {
            var field = new Field(target, alias);
            if (fields.containsKey(field)) {
                throw new IllegalArgumentException("Alias collision at " + field + " of " + descriptor.id()
                        + ": " + fields.get(field));
            }
        }
    }

    private void recordClaim(FeatureDescriptor<?> descriptor) {
        featureTypes.add(descriptor.type());
        featureIds.add(descriptor.id());
        descriptor.alias().ifPresent(alias -> {
            for (var target : descriptor.attachment().targets()) {
                fields.put(new Field(target, alias), descriptor.id().toString());
            }
        });
    }

    private void checkOpen() {
        if (catalog != null) {
            throw new IllegalStateException("Registration has ended");
        }
    }

    private record Field(FeatureAttachment.Target target, String name) {}
}
