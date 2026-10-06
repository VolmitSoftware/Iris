package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.stage.Pipeline;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Everything one pack load derives from its modules: the frozen feature catalog (including the
 * settings types stages are conditioned on) and one resolved graph per pipeline.
 */
public record LoadedModules(FeatureCatalog catalog, ResolvedGraph placement, ResolvedGraph chunk) {
    public LoadedModules {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(chunk, "chunk");
    }

    /** Pass the built-in modules together with {@link PackModules#modules()} of the pack.
     *
     * @param reservedHostFields JSON fields the host itself uses, per attachment target
     * @param reservedNamespaces namespaces only built-in modules may use, such as {@code iris}; a
     *     pack module whose id uses one fails the load
     */
    public static LoadedModules load(
            List<DiscoveredModule> modules,
            Map<FeatureAttachment.Target, Set<String>> reservedHostFields,
            Collection<String> reservedNamespaces) {
        var reserved = ReservedNamespaces.of(reservedNamespaces);
        modules.forEach(reserved::requireAllowed);
        var registry = new ModuleRegistry(reservedHostFields);
        var stages = new StageGraphBuilder();
        for (var discovered : modules) {
            installModule(discovered, stages, registry);
        }
        stages.settingsTypes(registry::registeredByOwner).forEach(registry::registerStageSettings);
        return new LoadedModules(registry.freeze(), stages.resolve(Pipeline.PLACEMENT), stages.resolve(Pipeline.CHUNK));
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
