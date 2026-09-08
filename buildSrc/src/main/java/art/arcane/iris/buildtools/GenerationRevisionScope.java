package art.arcane.iris.buildtools;

import java.nio.file.Path;
import java.util.List;

public final class GenerationRevisionScope {
    private static final List<String> SOURCE_ROOTS = List.of(
            "lombok.config",
            "core/agent/build.gradle",
            "core/agent/src/main/java",
            "core/src/main/java",
            "spi/src/main/java",
            "adapters/bukkit/plugin/src/main/java",
            "adapters/bukkit/nms/v26_2_R1/src/main/java",
            "adapters/minecraft-common/src/main/java",
            "adapters/modded-common/src/main/java",
            "adapters/client-common/src/main/java",
            "adapters/fabric/src/main/java",
            "adapters/forge/src/main/java",
            "adapters/neoforge/src/main/java"
    );
    private static final List<String> OPERATIONAL_EXCLUSIONS = List.of(
            "core/src/main/java/art/arcane/iris/engine/EngineDiagnostics.java",
            "core/src/main/java/art/arcane/iris/engine/EngineMetricsReport.java",
            "core/src/main/java/art/arcane/iris/core/IrisStartupValidation.java",
            "core/src/main/java/art/arcane/iris/core/safeguard/IrisSafeguard.java",
            "core/src/main/java/art/arcane/iris/core/safeguard/Mode.java",
            "core/src/main/java/art/arcane/iris/core/safeguard/task/Task.java",
            "core/src/main/java/art/arcane/iris/core/safeguard/task/Tasks.java",
            "core/src/main/java/art/arcane/iris/core/safeguard/task/Diagnostic.java",
            "core/src/main/java/art/arcane/iris/core/safeguard/task/ValueWithDiagnostics.java",
            "core/src/main/java/art/arcane/iris/core/splash/IrisSplashRenderer.java",
            "adapters/bukkit/nms/v26_2_R1/src/main/java/art/arcane/iris/core/nms/v26_2_R1/NmsWorldLifecycle.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandDeveloper.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandPregen.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandIris.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsaw.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawAdopt.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawConnector.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawExports.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawOpen.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawPiece.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawPool.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawPreview.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawRules.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawVariant.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandJigsawWorkcell.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandStudio.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandObject.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/PaperCommandRegistrar.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandPack.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandStructure.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandWhat.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandFind.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandEdit.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/core/commands/CommandDatapack.java"
    );

    private GenerationRevisionScope() {
    }

    public static GenerationBuildRevision.CaptureOptions current(Path repository) {
        return new GenerationBuildRevision.CaptureOptions(repository, 1,
                "art.arcane.iris.engine.history.GenerationKernelV1",
                List.of(new GenerationBuildRevision.AlgorithmVersion(1, 1)), SOURCE_ROOTS, OPERATIONAL_EXCLUSIONS);
    }
}
