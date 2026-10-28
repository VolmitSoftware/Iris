package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.service.Providers;
import com.volmit.service.Service;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Frozen registrations. Lists use lexical feature/layer/kind ID order, and validators, lifecycle hooks, service
 * providers are in module id order, each module's in the order it registered them, so
 * nothing depends on discovery. Factory, codec, validator, hook and provider implementations must themselves obey their
 * concurrency contracts.
 */
public final class FeatureCatalog {
    /** Services are asked for on the generation hot path, so each type's annotation is read once. */
    private static final ClassValue<Providers> SERVICE_PROVIDERS = new ClassValue<>() {
        @Override
        protected Providers computeValue(Class<?> type) {
            var service = type.isInterface() ? type.getAnnotation(Service.class) : null;
            if (service == null) {
                throw new IllegalArgumentException(type.getName() + " is not a service: services are interfaces marked @"
                        + Service.class.getName());
            }
            return service.value();
        }
    };

    private final Map<Class<?>, FeatureDescriptor<?>> settings;
    private final Map<Class<?>, ObjectRegistration<?>> objects;
    private final Map<String, MantleRegistration<?>> mantle;
    private final List<ResourceKindRegistration> resourceKinds;
    private final List<ValidatorRegistration> validators;
    private final List<LifecycleRegistration> lifecycles;
    private final Map<Class<?>, List<ServiceRegistration>> registrationsByService;
    private final Map<Class<?>, List<?>> providersByService;
    private final Optional<PlacementTypeRegistration> placementType;

    FeatureCatalog(
            Map<Class<?>, FeatureDescriptor<?>> settings,
            Map<Class<?>, ObjectRegistration<?>> objects,
            Map<String, MantleRegistration<?>> mantle,
            Collection<ResourceKindRegistration> resourceKinds,
            List<ValidatorRegistration> validators,
            List<LifecycleRegistration> lifecycles,
            List<ServiceRegistration> services,
            PlacementTypeRegistration placementType) {
        this.placementType = Optional.ofNullable(placementType);
        this.settings = Map.copyOf(settings);
        this.objects = Map.copyOf(objects);
        this.mantle = Map.copyOf(mantle);
        this.resourceKinds = sorted(resourceKinds, registration -> registration.kind().id());
        this.validators = inModuleOrder(validators, ValidatorRegistration::module);
        this.lifecycles = inModuleOrder(lifecycles, LifecycleRegistration::module);
        this.registrationsByService = byService(inModuleOrder(services, ServiceRegistration::module));
        this.providersByService = providersByService(registrationsByService);
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

    /** The type packs write their object placements as, if a module declared it ({@code FeatureRegistry.registerPlacementType}). */
    public Optional<PlacementTypeRegistration> placementType() {
        return placementType;
    }

    /** The pack resource kinds modules read, by kind id. */
    public List<ResourceKindRegistration> resourceKinds() {
        return resourceKinds;
    }

    /** The validators modules registered, by module id, each module's in the order it registered them. */
    public List<ValidatorRegistration> validators() {
        return validators;
    }

    /** The lifecycle hooks modules registered, by module id, each module's in the order it registered them. */
    public List<LifecycleRegistration> lifecycles() {
        return lifecycles;
    }

    /** Every provider of {@code service}, an interface marked {@link Service}, in module id order and each module's in the
     * order it registered them; empty when no module provides it.
     */
    @SuppressWarnings("unchecked") // ModuleRegistry.registerService checked that every provider implements its service.
    public <S> List<S> services(Class<S> service) {
        var providers = providersByService.get(service);
        if (providers == null) {
            providersOf(service);
            return List.of();
        }
        return (List<S>) providers;
    }

    /** Every provider of {@code service} with the module that registered it, in the order of {@link #services}, so the host
     * can name the module whose provider failed.
     */
    public List<ServiceRegistration> registrations(Class<?> service) {
        var registrations = registrationsByService.get(service);
        if (registrations == null) {
            providersOf(service);
            return List.of();
        }
        return registrations;
    }

    /** The one provider of {@code service}, a service whose providers are {@link Providers#ONE}, if a module provides it. */
    public <S> Optional<S> service(Class<S> service) {
        if (providersOf(service) != Providers.ONE) {
            throw new IllegalArgumentException("Service " + service.getName() + " may have several providers; ask for all of"
                    + " them with services(...)");
        }
        var providers = services(service);
        return providers.isEmpty() ? Optional.empty() : Optional.of(providers.getFirst());
    }

    /** How many modules may provide {@code service}, which must be an interface marked {@link Service}. */
    static Providers providersOf(Class<?> service) {
        return SERVICE_PROVIDERS.get(service);
    }

    private static Map<Class<?>, List<ServiceRegistration>> byService(List<ServiceRegistration> inModuleOrder) {
        return Map.copyOf(inModuleOrder.stream()
                .collect(Collectors.groupingBy(ServiceRegistration::service, Collectors.toUnmodifiableList())));
    }

    private static Map<Class<?>, List<?>> providersByService(Map<Class<?>, List<ServiceRegistration>> registrationsByService) {
        var providers = new HashMap<Class<?>, List<?>>();
        registrationsByService.forEach((service, registrations) ->
                providers.put(service, registrations.stream().map(ServiceRegistration::provider).toList()));
        return Map.copyOf(providers);
    }

    private static <T> List<T> inModuleOrder(List<T> registrations, Function<? super T, DiscoveredModule> module) {
        return registrations.stream()
                .sorted(Comparator.comparing(registration -> module.apply(registration).info().id()))
                .toList();
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
