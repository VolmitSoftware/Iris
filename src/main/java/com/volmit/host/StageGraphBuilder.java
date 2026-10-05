package com.volmit.host;

import com.volmit.IrisFeature;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Reference host collection of module stages. Install every module, then resolve each pipeline
 * once per pack load. Install order never affects the result. If a module's install throws,
 * discard the whole builder: it may hold a partial module. Errors about one module name its origin.
 *
 * <p>A module defines only stages whose ids start with its own id and '/', and a settings type
 * named in {@code when(...)} must have a feature id in the namespace of a module that names it.
 */
public final class StageGraphBuilder implements StageGraph {
    private final Map<ResourceId, StageDeclaration> declarations = new HashMap<>();
    private final Map<ResourceId, DiscoveredModule> modules = new HashMap<>();
    private DiscoveredModule installing;

    public void install(DiscoveredModule discovered) {
        Objects.requireNonNull(discovered, "discovered");
        claimModule(discovered);
        installing = discovered;
        try {
            discovered.module().install(this);
        } finally {
            installing = null;
        }
        requireCompleteDefinitions(discovered.info().id());
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

    /** Settings types named by when(...), ordered by feature id. Fails if a type's feature id is not
     * in the namespace of any module that names it: some module of that namespace must own it.
     */
    public List<Class<? extends IrisFeature>> settingsTypes() {
        var uses = settingsUses();
        uses.forEach(this::requireSettingsOwner);
        return uses.stream()
                .<Class<? extends IrisFeature>>map(use -> use.settings().type())
                .toList();
    }

    private void claimModule(DiscoveredModule discovered) {
        var moduleId = discovered.info().id();
        if (installing != null) {
            throw new IllegalStateException("Module " + moduleId + " installed during " + installing.info().id());
        }
        var previous = modules.putIfAbsent(moduleId, discovered);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate module " + moduleId + " from " + discovered.origin()
                    + "; already loaded from " + previous.origin());
        }
    }

    /** Each settings type named by when(...), ordered by feature id, with the modules naming it. */
    private List<SettingsUse> settingsUses() {
        var uses = new HashMap<Class<?>, SettingsUse>();
        for (var declaration : declarations.values()) {
            declaration.settings().ifPresent(settings -> uses
                    .computeIfAbsent(settings.type(), ignored -> new SettingsUse(settings, new TreeSet<>()))
                    .namers().add(declaration.module()));
        }
        return uses.values().stream()
                .sorted(Comparator.comparing((SettingsUse use) -> use.settings().id())
                        .thenComparing(use -> use.settings().type().getName()))
                .toList();
    }

    private void requireSettingsOwner(SettingsUse use) {
        var settings = use.settings();
        var namers = use.namers();
        var namespace = settings.id().namespace();
        if (namers.stream().anyMatch(module -> module.namespace().equals(namespace))) return;
        throw new IllegalArgumentException("Settings type " + settings.type().getName() + " has feature id "
                + settings.id() + ", but no module of namespace '" + namespace + "' names it in when(...); it is named by "
                + describeModules(namers) + ". A feature id uses the namespace of the module that owns the settings");
    }

    private String describeModules(SortedSet<ResourceId> moduleIds) {
        return moduleIds.stream()
                .map(modules::get)
                .map(DiscoveredModule::toString)
                .collect(Collectors.joining(", "));
    }

    private StageDeclaration declare(Stage<?> stage) {
        Objects.requireNonNull(stage, "stage");
        var discovered = requireInstallingModule(stage);
        requireOwnedStageId(stage, discovered);

        var module = discovered.info().id();
        var declaration = new StageDeclaration(stage, module);
        var previous = declarations.putIfAbsent(stage.id(), declaration);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate definition of stage " + stage.id() + " by module "
                    + module + "; already defined by module " + previous.module());
        }
        return declaration;
    }

    private DiscoveredModule requireInstallingModule(Stage<?> stage) {
        if (installing == null) {
            throw new IllegalStateException("Stage " + stage + " defined outside IrisModule.install");
        }
        return installing;
    }

    private static void requireOwnedStageId(Stage<?> stage, DiscoveredModule discovered) {
        var info = discovered.info();
        if (info.ownsStage(stage.id())) return;
        throw new IllegalArgumentException("Stage " + stage + " must start with " + info.stageIdPrefix()
                + ", the id of its defining module " + discovered);
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

    private record SettingsUse(FeatureDescriptor<?> settings, SortedSet<ResourceId> namers) {}
}
