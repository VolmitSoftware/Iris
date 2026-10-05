package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.IrisModule;
import com.volmit.stage.Pipeline;
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

    /** Module discovery and version gating stay host work; pass the accepted modules here. */
    public static LoadedModules load(
            List<? extends IrisModule> modules,
            Map<FeatureAttachment.Target, Set<String>> reservedHostFields) {
        var registry = new ModuleRegistry(reservedHostFields);
        var stages = new StageGraphBuilder();
        for (var module : modules) {
            stages.install(module);
            module.register(registry);
        }
        stages.settingsTypes().forEach(registry::registerSettings);
        return new LoadedModules(registry.freeze(), stages.resolve(Pipeline.PLACEMENT), stages.resolve(Pipeline.CHUNK));
    }
}
