package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.resource.ResourceId;
import com.volmit.stage.BlockStage;
import com.volmit.stage.BlockStageDefinition;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.Pipeline;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.Stage;
import com.volmit.stage.StageDefinition;
import com.volmit.stage.StageGraph;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reference host collection of module stages. Install every module, then resolve each pipeline
 * once per pack load. Install order never affects the result. If a module's install throws,
 * discard the whole builder: it may hold a partial module.
 */
public final class StageGraphBuilder implements StageGraph {
    private final Map<ResourceId, StageDeclaration> declarations = new HashMap<>();
    private final Set<ResourceId> modules = new HashSet<>();
    private ResourceId installingModule;

    public void install(IrisModule module) {
        Objects.requireNonNull(module, "module");
        var moduleId = Objects.requireNonNull(module.info(), "module info").id();
        claimModule(moduleId);
        installingModule = moduleId;
        try {
            module.install(this);
        } finally {
            installingModule = null;
        }
        requireCompleteDefinitions(moduleId);
    }

    @Override
    public <O> StageDefinition<PlacementStageContext, O> define(PlacementStage<O> stage) {
        return new InvocationStageDraft<>(declare(stage));
    }

    @Override
    public <O> BlockStageDefinition<O> define(BlockStage<O> stage) {
        return new BlockStageDraft<>(declare(stage));
    }

    @Override
    public <O> StageDefinition<ChunkStageContext, O> define(ChunkStage<O> stage) {
        return new InvocationStageDraft<>(declare(stage));
    }

    public ResolvedGraph resolve(Pipeline pipeline) {
        Objects.requireNonNull(pipeline, "pipeline");
        return GraphResolver.resolve(pipeline, declarations.values());
    }

    /** Settings types named by when(...), ordered by feature id. */
    public List<Class<? extends IrisFeature>> settingsTypes() {
        return declarations.values().stream()
                .flatMap(declaration -> declaration.settings().stream())
                .distinct()
                .sorted(Comparator.comparing(FeatureDescriptor::id))
                .<Class<? extends IrisFeature>>map(FeatureDescriptor::type)
                .toList();
    }

    private void claimModule(ResourceId moduleId) {
        if (installingModule != null) {
            throw new IllegalStateException("Module " + moduleId + " installed during " + installingModule);
        }
        if (!modules.add(moduleId)) {
            throw new IllegalArgumentException("Duplicate module: " + moduleId);
        }
    }

    private StageDeclaration declare(Stage<?> stage) {
        Objects.requireNonNull(stage, "stage");
        var module = requireInstallingModule(stage);
        requireModuleNamespace(stage, module);

        var declaration = new StageDeclaration(stage, module);
        var previous = declarations.putIfAbsent(stage.id(), declaration);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate definition of stage " + stage.id() + " by module "
                    + module + "; already defined by module " + previous.module());
        }
        return declaration;
    }

    private ResourceId requireInstallingModule(Stage<?> stage) {
        if (installingModule == null) {
            throw new IllegalStateException("Stage " + stage + " defined outside IrisModule.install");
        }
        return installingModule;
    }

    private static void requireModuleNamespace(Stage<?> stage, ResourceId module) {
        if (!stage.id().namespace().equals(module.namespace())) {
            throw new IllegalArgumentException("Stage " + stage + " must use namespace '"
                    + module.namespace() + "' of its defining module " + module);
        }
    }

    private void requireCompleteDefinitions(ResourceId moduleId) {
        declarations.values().stream()
                .filter(declaration -> declaration.module().equals(moduleId))
                .filter(declaration -> !declaration.isComplete())
                .map(StageDeclaration::toString)
                .sorted()
                .findFirst()
                .ifPresent(incomplete -> {
                    throw new IllegalStateException("Stage " + incomplete
                            + " has no body; end its definition with run, produce or accumulate");
                });
    }
}
