package com.volmit.host;

import com.volmit.*;
import com.volmit.config.Feature;
import com.volmit.mantle.*;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import org.junit.jupiter.api.Test;
import java.io.DataInput;
import java.io.DataOutput;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryTest {
    @Feature(id = "test:one", name = "One", field = "shared", targets = FeatureAttachment.Target.BIOME)
    static class One implements IrisFeature {}
    @Feature(id = "test:two", name = "Two", field = "shared", targets = FeatureAttachment.Target.DIMENSION)
    static class Two implements IrisFeature {}
    @Feature(id = "test:collision", name = "Collision", field = "shared", targets = {FeatureAttachment.Target.DIMENSION, FeatureAttachment.Target.BIOME})
    static class Collision implements IrisFeature {}
    @Feature(id = "test:one", name = "Duplicate", field = "different", targets = FeatureAttachment.Target.REGION)
    static class Duplicate implements IrisFeature {}
    static class MissingAnnotation implements IrisFeature {}
    @Feature(id = "test:abstract", name = "Abstract", field = "abstract", targets = FeatureAttachment.Target.BIOME)
    abstract static class AbstractFeature implements IrisFeature {}

    @Test
    void validatesIdsTypesAndFieldsAcrossFeatureKindsWithoutPartialRegistration() {
        var registry = new ModuleRegistry();
        registry.registerSettings(One.class);
        registry.registerSettings(One.class);
        assertThrows(IllegalArgumentException.class, () -> registry.registerObject(Collision.class, ignored -> context -> null));
        registry.registerObject(Two.class, ignored -> context -> null);
        assertThrows(IllegalArgumentException.class, () -> registry.registerObject(Duplicate.class, ignored -> context -> null));
        assertThrows(IllegalArgumentException.class, () -> registry.registerObject(One.class, ignored -> context -> null));
        var catalog = registry.freeze();
        assertEquals(List.of("test:one"), catalog.settings().stream().map(value -> value.id().toString()).toList());
        assertEquals(List.of("test:two"), catalog.objects().stream().map(value -> value.descriptor().id().toString()).toList());
    }

    @Test
    void reservesHostFieldsAndRejectsInvalidFeaturesOrFactories() {
        var registry = new ModuleRegistry(Map.of(FeatureAttachment.Target.BIOME, Set.of("shared")));
        assertThrows(IllegalArgumentException.class, () -> registry.registerSettings(One.class));
        assertThrows(IllegalArgumentException.class, () -> registry.registerSettings(MissingAnnotation.class));
        assertThrows(IllegalArgumentException.class, () -> registry.registerSettings(AbstractFeature.class));
        assertThrows(NullPointerException.class, () -> registry.registerObject(Two.class, null));
        registry.registerObject(Two.class, ignored -> context -> null);
        assertSame(registry.freeze(), registry.freeze());
        assertThrows(IllegalStateException.class, () -> registry.registerSettings(One.class));
    }

    @Test
    void mantleIdsAreUniqueAndCodecVersionIsSnapshotted() {
        var registry = new ModuleRegistry();
        var key = new MantleKey<>("test:owners", String.class);
        var codec = codec(3);
        registry.registerMantle(key, MantleLifetime.GENERATION, codec);
        assertThrows(IllegalArgumentException.class, () -> registry.registerMantle(key, MantleLifetime.PERSISTENT, codec));
        assertThrows(IllegalArgumentException.class, () -> registry.registerMantle(new MantleKey<>("test:bad", String.class), MantleLifetime.GENERATION, codec(-1)));
        assertEquals(3, registry.freeze().mantle().getFirst().version());
        assertEquals(1, registry.freeze().mantle().size());
    }

    @Test
    void loadingModulesCatalogsStageSettingsAndRegistrationsTogether() {
        var conditioned = new IrisModule() {
            static final PlacementStage<Void> WORK = Stage.placement("test:conditioned/work");
            public ModuleInfo info() { return ModuleInfo.of("test:conditioned", "Conditioned", "1"); }
            public void install(StageGraph graph) { graph.define(WORK).when(One.class).run((context, one) -> {}); }
            public void register(FeatureRegistry registry) { registry.registerObject(Two.class, ignored -> context -> null); }
        };
        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(conditioned)), Map.of());
        assertEquals(List.of("test:one"), loaded.catalog().settings().stream().map(value -> value.id().toString()).toList());
        assertEquals(1, loaded.catalog().objects().size());
        assertEquals(1, loaded.placement().stages().size());
        assertTrue(loaded.chunk().stages().isEmpty());

        var reserved = Map.of(FeatureAttachment.Target.BIOME, Set.of("shared"));
        assertThrows(IllegalArgumentException.class, () -> LoadedModules.load(List.of(DiscoveredModule.builtIn(conditioned)), reserved));
    }

    private static MantleCodec<String> codec(int version) {
        return new MantleCodec<>() {
            public int version() { return version; }
            public void write(DataOutput output, String value) {}
            public String read(DataInput input, int storedVersion) { return ""; }
        };
    }
}
