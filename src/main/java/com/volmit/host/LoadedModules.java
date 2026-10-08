package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.host.schema.PackSchema;
import com.volmit.stage.Pipeline;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Everything one pack load derives from its modules: the frozen feature catalog (including the
 * settings types stages are conditioned on, resource kinds and validators), its description for studio and one
 * resolved graph per pipeline.
 */
public record LoadedModules(
        FeatureCatalog catalog, PackSchema schema, ResolvedGraph placement, ResolvedGraph chunk, ResolvedGraph mantle) {
    public LoadedModules {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(mantle, "mantle");
    }

    /** Pass the built-in modules together with {@link PackModules#modules()} of the pack.
     *
     * @param reservedHostFields JSON fields the host itself uses, per attachment target
     * @param reservedNamespaces namespaces only built-in modules may use, such as {@code iris}; a
     *     pack module whose id uses one fails the load
     * @param hostTypePackages packages whose classes the host describes itself, each with its
     *     subpackages; only built-in modules' settings may use them (see {@link ModuleHost#describingHostTypesIn})
     * @param reservedFolders pack folders the host reads itself, which no resource kind may read (see
     *     {@link ModuleHost#reservingFolders})
     */
    public static LoadedModules load(
            List<DiscoveredModule> modules,
            Map<FeatureAttachment.Target, Set<String>> reservedHostFields,
            Collection<String> reservedNamespaces,
            Collection<String> hostTypePackages,
            Collection<String> reservedFolders) {
        var reserved = ReservedNamespaces.of(reservedNamespaces);
        modules.forEach(reserved::requireAllowed);
        var registry = new ModuleRegistry(reservedHostFields, Set.copyOf(reservedFolders));
        var stages = new StageGraphBuilder();
        for (var discovered : modules) {
            installModule(discovered, stages, registry);
        }
        stages.settingsTypes(registry::registeredByOwner).forEach(registry::registerStageSettings);
        var catalog = registry.freeze();
        var schema = PackSchemaBuilder.describe(catalog, modules, HostTypes.of(hostTypePackages));
        var placement = stages.resolve(Pipeline.PLACEMENT);
        var chunk = stages.resolve(Pipeline.CHUNK);
        var mantle = stages.resolve(Pipeline.MANTLE);
        requireRegisteredMantleLayers(catalog, placement);
        requireRegisteredMantleLayers(catalog, mantle);
        return new LoadedModules(catalog, schema, placement, chunk, mantle);
    }

    /** A stage's reach names the layers it writes; a layer nobody registered has no codec or lifetime. */
    private static void requireRegisteredMantleLayers(FeatureCatalog catalog, ResolvedGraph graph) {
        for (var stage : graph.stages()) {
            for (var layer : stage.reach().mantleLayers()) {
                var registered = catalog.mantle().stream()
                        .map(MantleRegistration::key)
                        .filter(key -> key.id().equals(layer.id()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Stage " + stage.stage().id() + " of module "
                                + stage.module() + " writes mantle layer " + layer.id() + ", which no module registers;"
                                + " its owner registers it with FeatureRegistry.registerMantle"));
                if (registered.type() != layer.type()) {
                    throw new IllegalArgumentException("Stage " + stage.stage().id() + " of module " + stage.module()
                            + " writes mantle layer " + layer.id() + " as " + layer.type().getName()
                            + ", but it is registered as " + registered.type().getName());
                }
            }
        }
    }

    /** The pack load checks that every class a module uses exists, not that every method or field
     * it uses does, so a jar built against another Iris API can still fail to link here.
     */
    private static void installModule(DiscoveredModule discovered, StageGraphBuilder stages, ModuleRegistry registry) {
        try {
            stages.install(discovered);
            registry.register(discovered);
        } catch (LinkageError error) {
            throw new IllegalArgumentException("Module " + discovered.info().id() + " from "
                    + discovered.origin() + " failed to link or initialize a class while installing: " + error
                    + "; a missing method or field means the jar was built against a different Iris API", error);
        }
    }
}
