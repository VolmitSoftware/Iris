package com.volmit.host;

import com.volmit.*;
import com.volmit.config.Feature;
import com.volmit.generation.*;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.*;
import com.volmit.placement.PlacementModifier;
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
    @Feature(id = "test:placement", name = "Placement", field = "foundation", targets = FeatureAttachment.Target.OBJECT_PLACEMENT)
    static class Placement implements IrisFeature {}
    static class MissingAnnotation implements IrisFeature {}
    @Feature(id = "test:abstract", name = "Abstract", field = "abstract", targets = FeatureAttachment.Target.BIOME)
    abstract static class AbstractFeature implements IrisFeature {}

    private static GenerationModifier<String> modifier(IrisFeature ignored) {
        return new GenerationModifier<>() {
            public String id() { return "test:modifier"; }
            public void modify(GenerationContext context, Hunk<String> output) {}
        };
    }

    @Test
    void validatesIdsTypesAndFieldsAcrossFeatureKindsWithoutPartialRegistration() {
        var registry = new ModuleRegistry();
        registry.register(One.class, String.class, ModuleRegistryTest::modifier);
        assertThrows(IllegalArgumentException.class, () -> registry.register(Collision.class, String.class, ModuleRegistryTest::modifier));
        registry.register(Two.class, String.class, ModuleRegistryTest::modifier);
        assertThrows(IllegalArgumentException.class, () -> registry.registerObject(Duplicate.class, ignored -> context -> null));
        assertThrows(IllegalArgumentException.class, () -> registry.register(One.class, String.class, ModuleRegistryTest::modifier));
        var catalog = registry.freeze();
        assertEquals(List.of("test:one", "test:two"), catalog.generation().stream().map(value -> value.descriptor().id().toString()).toList());
        assertThrows(IllegalArgumentException.class, () -> catalog.generation(One.class, Integer.class));
    }

    @Test
    void reservesHostFieldsAndRejectsInvalidFeaturesOrFactories() {
        var registry = new ModuleRegistry(Map.of(FeatureAttachment.Target.BIOME, Set.of("shared")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(One.class, String.class, ModuleRegistryTest::modifier));
        assertThrows(IllegalArgumentException.class, () -> registry.register(MissingAnnotation.class, String.class, ModuleRegistryTest::modifier));
        assertThrows(IllegalArgumentException.class, () -> registry.register(AbstractFeature.class, String.class, ModuleRegistryTest::modifier));
        assertThrows(NullPointerException.class, () -> registry.register(Two.class, String.class, null));
        registry.register(Two.class, String.class, ModuleRegistryTest::modifier);
    }

    @Test
    void placementMustAttachToPlacementAndFreezeEndsRegistration() {
        var registry = new ModuleRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.registerPlacement(One.class, ignored -> new PlacementModifier() {
            public String id() { return "test:placement"; }
        }));
        registry.registerPlacement(Placement.class, ignored -> new PlacementModifier() {
            public String id() { return "test:placement"; }
        });
        assertEquals(1, registry.freeze().placement().size());
        assertSame(registry.freeze(), registry.freeze());
        assertThrows(IllegalStateException.class, () -> registry.register(One.class, String.class, ModuleRegistryTest::modifier));
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

    private static MantleCodec<String> codec(int version) {
        return new MantleCodec<>() {
            public int version() { return version; }
            public void write(DataOutput output, String value) {}
            public String read(DataInput input, int storedVersion) { return ""; }
        };
    }
}
