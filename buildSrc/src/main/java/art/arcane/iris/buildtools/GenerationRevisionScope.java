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
            "core/src/main/java/art/arcane/iris/generation/runtime/EngineDiagnostics.java",
            "core/src/main/java/art/arcane/iris/generation/runtime/EngineMetricsReport.java",
            "core/src/main/java/art/arcane/iris/world/IrisStartupValidation.java",
            "core/src/main/java/art/arcane/iris/world/safeguard/IrisSafeguard.java",
            "core/src/main/java/art/arcane/iris/world/safeguard/Mode.java",
            "core/src/main/java/art/arcane/iris/world/safeguard/Task.java",
            "core/src/main/java/art/arcane/iris/world/safeguard/Tasks.java",
            "core/src/main/java/art/arcane/iris/world/safeguard/Diagnostic.java",
            "core/src/main/java/art/arcane/iris/world/safeguard/ValueWithDiagnostics.java",
            "core/src/main/java/art/arcane/iris/diagnostics/splash/IrisSplashRenderer.java",
            "adapters/bukkit/nms/v26_2_R1/src/main/java/art/arcane/iris/platform/bukkit/nms/v26_2_R1/NmsWorldLifecycle.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandDeveloper.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandPregen.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandIris.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsaw.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawAdopt.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawConnector.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawExports.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawOpen.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawPiece.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawPool.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawPreview.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawRules.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawVariant.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandJigsawWorkcell.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandStudio.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandObject.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/PaperCommandRegistrar.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandPack.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandStructure.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandWhat.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandFind.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandEdit.java",
            "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/CommandDatapack.java"
    );

    private GenerationRevisionScope() {
    }

    public static GenerationBuildRevision.CaptureOptions current(Path repository) {
        return new GenerationBuildRevision.CaptureOptions(repository, 1,
                "art.arcane.iris.world.history.GenerationKernelV1",
                List.of(new GenerationBuildRevision.AlgorithmVersion(1, 1)), SOURCE_ROOTS, OPERATIONAL_EXCLUSIONS);
    }
}
