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
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryTest {
    @Feature(id = "test:core/one", name = "One", field = "shared", targets = FeatureAttachment.Target.BIOME)
    static class One implements IrisFeature {}
    @Feature(id = "test:core/two", name = "Two", field = "shared", targets = FeatureAttachment.Target.DIMENSION)
    static class Two implements IrisFeature {}
    @Feature(id = "test:core/collision", name = "Collision", field = "shared", targets = {FeatureAttachment.Target.DIMENSION, FeatureAttachment.Target.BIOME})
    static class Collision implements IrisFeature {}
    @Feature(id = "test:core/one", name = "Duplicate", field = "different", targets = FeatureAttachment.Target.REGION)
    static class Duplicate implements IrisFeature {}
    static class MissingAnnotation implements IrisFeature {}
    @Feature(id = "test:core/abstract", name = "Abstract", field = "abstract", targets = FeatureAttachment.Target.BIOME)
    abstract static class AbstractFeature implements IrisFeature {}

    /** Runs {@code registrations} as the register(...) of a built-in module with the given id. */
    private static void registerAs(ModuleRegistry registry, String moduleId, Consumer<FeatureRegistry> registrations) {
        registry.register(DiscoveredModule.builtIn(new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(moduleId, "Test module " + moduleId, "1"); }
            public void install(StageGraph graph) {}
            public void register(FeatureRegistry registry) { registrations.accept(registry); }
        }));
    }

    @Test
    void validatesIdsTypesAndFieldsAcrossFeatureKindsWithoutPartialRegistration() {
        var registry = new ModuleRegistry();
        registry.registerStageSettings(One.class);
        registry.registerStageSettings(One.class);
        registerAs(registry, "test:core", objects -> {
            assertThrows(IllegalArgumentException.class, () -> objects.registerObject(Collision.class, ignored -> context -> null));
            objects.registerObject(Two.class, ignored -> context -> null);
            assertThrows(IllegalArgumentException.class, () -> objects.registerObject(Duplicate.class, ignored -> context -> null));
            assertThrows(IllegalArgumentException.class, () -> objects.registerObject(One.class, ignored -> context -> null));
        });
        var catalog = registry.freeze();
        assertEquals(List.of("test:core/one"), catalog.settings().stream().map(value -> value.id().toString()).toList());
        assertEquals(List.of("test:core/two"), catalog.objects().stream().map(value -> value.descriptor().id().toString()).toList());
    }

    @Test
    void reservesHostFieldsAndRejectsInvalidFeaturesOrFactories() {
        var registry = new ModuleRegistry(Map.of(FeatureAttachment.Target.BIOME, Set.of("shared")));
        assertThrows(IllegalArgumentException.class, () -> registry.registerStageSettings(One.class));
        assertThrows(IllegalArgumentException.class, () -> registry.registerStageSettings(MissingAnnotation.class));
        assertThrows(IllegalArgumentException.class, () -> registry.registerStageSettings(AbstractFeature.class));
        registerAs(registry, "test:core", objects -> {
            assertThrows(NullPointerException.class, () -> objects.registerObject(Two.class, null));
            objects.registerObject(Two.class, ignored -> context -> null);
        });
        assertSame(registry.freeze(), registry.freeze());
        assertThrows(IllegalStateException.class, () -> registry.registerStageSettings(One.class));
    }

    @Test
    void mantleIdsAreUniqueAndCodecVersionIsSnapshotted() {
        var registry = new ModuleRegistry();
        var key = new MantleKey<>("test:core/layer", String.class);
        var codec = codec(3);
        registerAs(registry, "test:core", layers -> {
            layers.registerMantle(key, MantleLifetime.GENERATION, codec);
            assertThrows(IllegalArgumentException.class, () -> layers.registerMantle(key, MantleLifetime.PERSISTENT, codec));
            assertThrows(IllegalArgumentException.class, () -> layers.registerMantle(new MantleKey<>("test:core/bad", String.class), MantleLifetime.GENERATION, codec(-1)));
        });
        assertEquals(3, registry.freeze().mantle().getFirst().version());
        assertEquals(1, registry.freeze().mantle().size());
    }

    @Test
    void objectFeatureIdsAndMantleKeysAreTheRegisteringModulesIdOrBelowIt() {
        var registry = new ModuleRegistry();
        var object = assertThrows(IllegalArgumentException.class, () -> registerAs(registry, "other:objects",
                objects -> objects.registerObject(Two.class, ignored -> context -> null)));
        assertEquals("Object generator settings " + Two.class.getName() + " with feature id test:core/two must be "
                + "other:objects or start with other:objects/, the id of its registering module other:objects from host classpath",
                object.getMessage());

        var sameNamespace = assertThrows(IllegalArgumentException.class, () -> registerAs(registry, "test:other",
                objects -> objects.registerObject(Two.class, ignored -> context -> null)));
        assertTrue(sameNamespace.getMessage().contains("must be test:other or start with test:other/"), sameNamespace.getMessage());

        var mantle = assertThrows(IllegalArgumentException.class, () -> registerAs(registry, "iris:snow",
                layers -> layers.registerMantle(new MantleKey<>("iris:snowdepth", String.class), MantleLifetime.GENERATION, codec(1))));
        assertEquals("Mantle layer iris:snowdepth must be iris:snow or start with iris:snow/, the id of its registering module "
                + "iris:snow from host classpath", mantle.getMessage());

        assertThrows(IllegalStateException.class, () -> registry.registerMantle(new MantleKey<>("test:loose", String.class),
                MantleLifetime.GENERATION, codec(1)));
        assertTrue(registry.freeze().objects().isEmpty());
        assertTrue(registry.freeze().mantle().isEmpty());
    }

    @Test
    void moduleMayRegisterItsOwnIdAndIdsBelowIt() {
        var registry = new ModuleRegistry();
        registerAs(registry, "iris:snow", layers -> {
            layers.registerMantle(new MantleKey<>("iris:snow", String.class), MantleLifetime.GENERATION, codec(1));
            layers.registerMantle(new MantleKey<>("iris:snow/depth/deep", String.class), MantleLifetime.GENERATION, codec(1));
        });

        assertEquals(List.of("iris:snow", "iris:snow/depth/deep"),
                registry.freeze().mantle().stream().map(layer -> layer.key().id()).sorted().toList());
    }

    @Test
    void loadingModulesCatalogsStageSettingsAndRegistrationsTogether() {
        var conditioned = new IrisModule() {
            static final PlacementStage<Void> WORK = Stage.placement("test:core/work");
            public ModuleInfo info() { return ModuleInfo.of("test:core", "Conditioned", "1"); }
            public void install(StageGraph graph) { graph.define(WORK).when(One.class).run((context, one) -> {}); }
            public void register(FeatureRegistry registry) { registry.registerObject(Two.class, ignored -> context -> null); }
        };
        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(conditioned)), Map.of(), Set.of());
        assertEquals(List.of("test:core/one"), loaded.catalog().settings().stream().map(value -> value.id().toString()).toList());
        assertEquals(1, loaded.catalog().objects().size());
        assertEquals(1, loaded.placement().stages().size());
        assertTrue(loaded.chunk().stages().isEmpty());

        var reserved = Map.of(FeatureAttachment.Target.BIOME, Set.of("shared"));
        assertThrows(IllegalArgumentException.class, () -> LoadedModules.load(List.of(DiscoveredModule.builtIn(conditioned)), reserved, Set.of()));
    }

    @Test
    void moduleRegistersItsOwnSettingsExplicitlyAndRepeatingItChangesNothing() {
        var registry = new ModuleRegistry();
        registerAs(registry, "test:core", settings -> {
            settings.registerSettings(One.class);
            settings.registerSettings(One.class);
        });
        registry.registerStageSettings(One.class);

        assertEquals(List.of("test:core/one"), registry.freeze().settings().stream().map(value -> value.id().toString()).toList());
        assertTrue(registry.registeredByOwner(One.class));
    }

    @Test
    void explicitSettingsFollowTheRegisteringModulesIdAndStillCollide() {
        var registry = new ModuleRegistry();
        var foreign = assertThrows(IllegalArgumentException.class,
                () -> registerAs(registry, "test:other", settings -> settings.registerSettings(One.class)));
        assertEquals("Settings " + One.class.getName() + " with feature id test:core/one must be test:other or start with "
                + "test:other/, the id of its registering module test:other from host classpath", foreign.getMessage());
        assertThrows(IllegalStateException.class, () -> registry.registerSettings(One.class));

        registerAs(registry, "test:core", settings -> settings.registerObject(Two.class, ignored -> context -> null));
        var collision = assertThrows(IllegalArgumentException.class,
                () -> registerAs(registry, "test:core", settings -> settings.registerSettings(Collision.class)));
        assertTrue(collision.getMessage().startsWith("JSON field collision"), collision.getMessage());
        assertTrue(registry.freeze().settings().isEmpty());
    }

    @Test
    void settingsTheirOwnerRegisteredMayBeNamedByAnyModule() {
        var owner = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:core", "Owner", "1"); }
            public void install(StageGraph graph) {}
            public void register(FeatureRegistry registry) { registry.registerSettings(One.class); }
        };
        var user = new IrisModule() {
            static final PlacementStage<Void> WORK = Stage.placement("test:user/work");
            public ModuleInfo info() { return ModuleInfo.of("test:user", "User", "1"); }
            public void install(StageGraph graph) { graph.define(WORK).when(One.class).run((context, one) -> {}); }
        };

        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(owner), DiscoveredModule.builtIn(user)), Map.of(), Set.of());
        assertEquals(List.of("test:core/one"), loaded.catalog().settings().stream().map(value -> value.id().toString()).toList());

        var unowned = assertThrows(IllegalArgumentException.class,
                () -> LoadedModules.load(List.of(DiscoveredModule.builtIn(user)), Map.of(), Set.of()));
        assertTrue(unowned.getMessage().contains("which none of the modules naming it in when(...) owns"), unowned.getMessage());
    }

    private static MantleCodec<String> codec(int version) {
        return new MantleCodec<>() {
            public int version() { return version; }
            public void write(DataOutput output, String value) {}
            public String read(DataInput input, int storedVersion) { return ""; }
        };
    }
}
