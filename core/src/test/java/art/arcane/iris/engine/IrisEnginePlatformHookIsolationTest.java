package art.arcane.iris.engine;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.mantle.EngineMantle;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class IrisEnginePlatformHookIsolationTest {
    private static final List<String> BUKKIT_ENGINE_CLASSES = List.of(
            "art/arcane/iris/core/gui/PregeneratorJob",
            "art/arcane/iris/core/ServerConfigurator",
            "art/arcane/iris/core/datapack/DatapackIngestService",
            "art/arcane/iris/core/events/IrisEngineHotloadEvent",
            "art/arcane/iris/core/project/IrisProject",
            "art/arcane/iris/core/tools/IrisToolbelt",
            "art/arcane/iris/platform/bukkit/",
            "org/bukkit/"
    );
    private static final List<String> BUKKIT_GENERATION_CLASSES = List.of(
            "art/arcane/iris/core/tools/IrisToolbelt",
            "art/arcane/iris/spi/IrisServices",
            "art/arcane/iris/util/common/scheduling/J",
            "art/arcane/iris/platform/bukkit/",
            "org/bukkit/"
    );
    private static final List<String> ENGINE_POLICY_CLASSES = List.of(
            "art/arcane/iris/core/gui/PregeneratorJob",
            "art/arcane/iris/core/tools/WorldMaintenance",
            "art/arcane/iris/platform/bukkit/",
            "org/bukkit/"
    );
    private static final List<String> PLATFORM_POLICY_CLASSES = List.of(
            "art/arcane/iris/core/gui/PregeneratorJob",
            "art/arcane/iris/core/tools/WorldMaintenance",
            "art/arcane/iris/util/common/scheduling/J",
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
        assertNoClassLinks(EngineGeneration.class, PLATFORM_POLICY_CLASSES);
        assertNoClassLinks(EngineMantle.class, PLATFORM_POLICY_CLASSES);
        assertNoClassLinks(EngineGeneration.class, BUKKIT_GENERATION_CLASSES);
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
