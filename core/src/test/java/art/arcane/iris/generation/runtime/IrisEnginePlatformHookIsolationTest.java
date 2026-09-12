package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.mode.ModeOverworld;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class IrisEnginePlatformHookIsolationTest {
    private static final List<String> BUKKIT_ENGINE_CLASSES = List.of(
            "art/arcane/iris/studio/view/PregeneratorJob",
            "art/arcane/iris/pack/datapack/ServerConfigurator",
            "art/arcane/iris/pack/datapack/DatapackIngestService",
            "art/arcane/iris/world/event/IrisEngineHotloadEvent",
            "art/arcane/iris/studio/workspace/IrisProject",
            "art/arcane/iris/world/IrisToolbelt",
            "art/arcane/iris/platform/bukkit/",
            "org/bukkit/"
    );
    private static final List<String> BUKKIT_MODE_CLASSES = List.of(
            "art/arcane/iris/world/IrisToolbelt",
            "art/arcane/iris/spi/IrisServices",
            "art/arcane/iris/world/task/J",
            "art/arcane/iris/platform/bukkit/",
            "org/bukkit/"
    );
    private static final List<String> ENGINE_POLICY_CLASSES = List.of(
            "art/arcane/iris/studio/view/PregeneratorJob",
            "art/arcane/iris/world/WorldMaintenance",
            "art/arcane/iris/platform/bukkit/",
            "org/bukkit/"
    );
    private static final List<String> PLATFORM_POLICY_CLASSES = List.of(
            "art/arcane/iris/studio/view/PregeneratorJob",
            "art/arcane/iris/world/WorldMaintenance",
            "art/arcane/iris/world/task/J",
            "art/arcane/iris/platform/bukkit/",
            "org/bukkit/"
    );

    @Test
    public void sharedGeneratorHotPathsDoNotLinkBukkitImplementations() throws IOException, ClassNotFoundException {
        assertNoClassLinks(IrisEngine.class, BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(EngineBackgroundTasks.class, BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(EngineDataStore.class, BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(EngineHotloader.class, BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(EngineMetricsReport.class, BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(EngineRuntimeBuilder.class, BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(EngineShutdownSequence.class, BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(EngineTickRegistry.class, BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(IrisEngineMantle.class, BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(Class.forName(IrisEngineMantle.class.getName() + "$1"), BUKKIT_ENGINE_CLASSES);
        assertNoClassLinks(Engine.class, ENGINE_POLICY_CLASSES);
        assertNoClassLinks(EngineMode.class, PLATFORM_POLICY_CLASSES);
        assertNoClassLinks(EngineMantle.class, PLATFORM_POLICY_CLASSES);
        assertNoClassLinks(ModeOverworld.class, BUKKIT_MODE_CLASSES);
    }

    private static void assertNoClassLinks(Class<?> type, List<String> forbiddenClasses) throws IOException {
        String resourceName = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
        InputStream stream = type.getResourceAsStream(resourceName);
        assertNotNull(stream);
        String bytecode;
        try (InputStream input = stream) {
            bytecode = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
        for (String className : forbiddenClasses) {
            assertFalse(type.getName() + " -> " + className, bytecode.contains(className));
        }
    }
}
