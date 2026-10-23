package com.volmit.host;

import com.volmit.*;
import com.volmit.config.Feature;
import com.volmit.mantle.*;
import com.volmit.resource.ResourceKind;
import com.volmit.service.Providers;
import com.volmit.service.Service;
import com.volmit.validation.Validator;
import com.volmit.service.VanillaStructures;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import org.junit.jupiter.api.Test;
import java.io.DataInput;
import java.io.DataOutput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryTest {
    @Feature(id = "test:core/one", name = "One", key = "coreOne", targets = FeatureAttachment.Target.BIOME)
    static class One implements IrisFeature {}
    @Feature(id = "test:core/two", name = "Two", key = "coreTwo", targets = FeatureAttachment.Target.DIMENSION)
    static class Two implements IrisFeature {}
    @Feature(id = "test:core/one", name = "Duplicate", key = "coreOne", targets = FeatureAttachment.Target.REGION)
    static class Duplicate implements IrisFeature {}
    @Feature(id = "test:ores", name = "Ores", key = "deposits", targets = {FeatureAttachment.Target.BIOME, FeatureAttachment.Target.REGION})
    static class Ores implements IrisFeature {}
    @Feature(id = "test:minerals", name = "Minerals", key = "deposits", targets = FeatureAttachment.Target.REGION)
    static class Minerals implements IrisFeature {}
    @Feature(id = "test:veins", name = "Veins", key = "deposits", targets = FeatureAttachment.Target.DIMENSION)
    static class Veins implements IrisFeature {}
    @Feature(id = "test:named", name = "Named", key = "name", targets = FeatureAttachment.Target.BIOME)
    static class Named implements IrisFeature {}
    static class MissingAnnotation implements IrisFeature {}
    @Feature(id = "test:core/abstract", name = "Abstract", key = "coreAbstract", targets = FeatureAttachment.Target.BIOME)
    abstract static class AbstractFeature implements IrisFeature {}

    /** Runs {@code registrations} as the setup(...) of a built-in module with the given id. */
    private static void registerAs(ModuleRegistry registry, String moduleId, Consumer<FeatureRegistry> registrations) {
        ModuleSetups.run(DiscoveredModule.builtIn(new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(moduleId, "Test module " + moduleId, "1"); }
            public void setup(ModuleSetup setup) { registrations.accept(setup.registry()); }
        }), new StageGraphBuilder(), registry);
    }

    @Test
    void validatesIdsTypesAndFieldsAcrossFeatureKindsWithoutPartialRegistration() {
        var registry = new ModuleRegistry();
        registerAs(registry, "test:core", settings -> settings.registerSettings(One.class));
        registry.registerStageSettings(One.class);
        registerAs(registry, "test:core", objects -> {
            objects.registerObject(Two.class, ignored -> context -> null);
            assertThrows(IllegalArgumentException.class, () -> objects.registerObject(Duplicate.class, ignored -> context -> null));
            assertThrows(IllegalArgumentException.class, () -> objects.registerObject(One.class, ignored -> context -> null));
        });
        var catalog = registry.freeze();
        assertEquals(List.of("test:core/one"), catalog.settings().stream().map(value -> value.id().toString()).toList());
        assertEquals(List.of("test:core/two"), catalog.objects().stream().map(value -> value.descriptor().id().toString()).toList());
    }

    @Test
    void rejectsInvalidFeaturesOrFactories() {
        var registry = new ModuleRegistry();
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
            public void setup(ModuleSetup setup) {
                setup.stages().define(WORK).when(One.class).run(context -> {});
                setup.registry().registerObject(Two.class, ignored -> context -> null);
            }
        };
        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(conditioned)), Map.of(), Set.of(), Set.of());
        assertEquals(List.of("test:core/one"), loaded.catalog().settings().stream().map(value -> value.id().toString()).toList());
        assertEquals(1, loaded.catalog().objects().size());
        assertEquals(1, loaded.placement().stages().size());
        assertTrue(loaded.chunk().stages().isEmpty());
    }

    @Test
    void twoModulesWithOneIdRefuseTheLoadNamingBothSoASettingsKeyIsNeverShared() {
        var first = ownerOfOne("First");
        var second = ownerOfOne("Second");

        var clash = assertThrows(IllegalArgumentException.class,
                () -> LoadedModules.load(List.of(DiscoveredModule.builtIn(first), DiscoveredModule.builtIn(second)), Map.of(), Set.of(), Set.of()));

        assertTrue(clash.getMessage().startsWith("Duplicate module test:core from "), clash.getMessage());
        assertTrue(clash.getMessage().contains("already loaded from "), clash.getMessage());
    }

    private static IrisModule settingsModule(String id, Class<? extends IrisFeature> settings) {
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(id, id, "1"); }
            public void setup(ModuleSetup setup) { setup.registry().registerSettings(settings); }
        };
    }

    private static List<DiscoveredModule> discovered(IrisModule... modules) {
        return java.util.Arrays.stream(modules).map(DiscoveredModule::builtIn).toList();
    }

    @Test
    void twoModulesTakingOneKeyAtOneTargetRefuseTheLoadNamingKeyTargetAndBothClaimants() {
        var ores = settingsModule("test:ores", Ores.class);
        var minerals = settingsModule("test:minerals", Minerals.class);

        var clash = assertThrows(IllegalArgumentException.class,
                () -> LoadedModules.load(discovered(ores, minerals), Map.of(), Set.of(), Set.of()));

        assertEquals("region key \"deposits\" is claimed by test:minerals (host classpath) and test:ores (host classpath);"
                + " give one of the settings another key in its @Feature", clash.getMessage());
    }

    @Test
    void theSameKeyAtDifferentTargetsIsNotAClash() {
        var loaded = LoadedModules.load(discovered(settingsModule("test:ores", Ores.class), settingsModule("test:veins", Veins.class)),
                Map.of(), Set.of(), Set.of());

        assertEquals(List.of("test:ores", "test:veins"), loaded.catalog().settings().stream().map(value -> value.id().toString()).sorted().toList());
    }

    @Test
    void aKeyTheHostUsesAsAFieldOfTheTargetIsRefusedNamingIris() {
        var reserved = Map.of(FeatureAttachment.Target.BIOME, Set.of("name", "generators"));

        var clash = assertThrows(IllegalArgumentException.class,
                () -> LoadedModules.load(discovered(settingsModule("test:named", Named.class)), reserved, Set.of(), Set.of()));

        assertEquals("biome key \"name\" is claimed by Iris and test:named (host classpath);"
                + " give one of the settings another key in its @Feature", clash.getMessage());
    }

    @Test
    void aHostFieldOfAnotherTargetDoesNotTakeTheKey() {
        var reserved = Map.of(FeatureAttachment.Target.REGION, Set.of("name"));

        var loaded = LoadedModules.load(discovered(settingsModule("test:named", Named.class)), reserved, Set.of(), Set.of());

        assertEquals(1, loaded.catalog().settings().size());
    }

    @Test
    void aClashingObjectGeneratorOrStageSettingsTypeClaimsTheKeyToo() {
        var registry = new ModuleRegistry();
        registerAs(registry, "test:ores", settings -> settings.registerSettings(Ores.class));
        var object = assertThrows(IllegalArgumentException.class, () -> registerAs(registry, "test:minerals",
                objects -> objects.registerObject(Minerals.class, ignored -> context -> null)));
        assertTrue(object.getMessage().startsWith("region key \"deposits\" is claimed by test:minerals (host classpath) and test:ores"), object.getMessage());

        var conditioned = new IrisModule() {
            static final PlacementStage<Void> WORK = Stage.placement("test:minerals/work");
            public ModuleInfo info() { return ModuleInfo.of("test:minerals", "Minerals", "1"); }
            public void setup(ModuleSetup setup) { setup.stages().define(WORK).when(Minerals.class).run(context -> {}); }
        };
        var stage = assertThrows(IllegalArgumentException.class,
                () -> LoadedModules.load(discovered(settingsModule("test:ores", Ores.class), conditioned), Map.of(), Set.of(), Set.of()));
        assertTrue(stage.getMessage().startsWith("region key \"deposits\" is claimed by test:minerals (host classpath) and test:ores"), stage.getMessage());
    }

    @Test
    void aRefusedKeyLeavesNothingClaimedByTheFailedFeature() {
        var registry = new ModuleRegistry(Map.of(FeatureAttachment.Target.REGION, Set.of("deposits")), Set.of());

        assertThrows(IllegalArgumentException.class, () -> registerAs(registry, "test:ores", settings -> settings.registerSettings(Ores.class)));

        registerAs(registry, "test:veins", settings -> settings.registerSettings(Veins.class));
        assertEquals(List.of("test:veins"), registry.freeze().settings().stream().map(value -> value.id().toString()).toList());
    }

    private static IrisModule ownerOfOne(String name) {
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:core", name, "1"); }
            public void setup(ModuleSetup setup) { setup.registry().registerSettings(One.class); }
        };
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
                () -> registerAs(registry, "test:core", settings -> settings.registerSettings(Two.class)));
        assertEquals("Duplicate feature: test:core/two", collision.getMessage());
        assertTrue(registry.freeze().settings().isEmpty());
    }

    @Test
    void settingsTheirOwnerRegisteredMayBeNamedByAnyModule() {
        var owner = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:core", "Owner", "1"); }
            public void setup(ModuleSetup setup) { setup.registry().registerSettings(One.class); }
        };
        var user = new IrisModule() {
            static final PlacementStage<Void> WORK = Stage.placement("test:user/work");
            public ModuleInfo info() { return ModuleInfo.of("test:user", "User", "1"); }
            public void setup(ModuleSetup setup) { setup.stages().define(WORK).when(One.class).run(context -> {}); }
        };

        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(owner), DiscoveredModule.builtIn(user)), Map.of(), Set.of(), Set.of());
        assertEquals(List.of("test:core/one"), loaded.catalog().settings().stream().map(value -> value.id().toString()).toList());

        var unowned = assertThrows(IllegalArgumentException.class,
                () -> LoadedModules.load(List.of(DiscoveredModule.builtIn(user)), Map.of(), Set.of(), Set.of()));
        assertTrue(unowned.getMessage().contains("which none of the modules naming it in when(...) owns"), unowned.getMessage());
    }

    static class Castle {}
    static class Pool {}

    @Test
    void resourceKindsHaveOwnedIdsAndOneIdTypeAndFolderEach() {
        var registry = new ModuleRegistry(Map.of(), Set.of("biomes"));
        var castles = ResourceKind.of("test:core/castle", "castles", Castle.class);
        registerAs(registry, "test:core", kinds -> {
            kinds.registerResourceKind(castles);
            var duplicate = assertThrows(IllegalArgumentException.class, () -> kinds.registerResourceKind(castles));
            assertEquals("Duplicate resource kind: test:core/castle of module test:core from host classpath", duplicate.getMessage());
        });

        var foreign = assertThrows(IllegalArgumentException.class, () -> registerAs(registry, "test:other",
                kinds -> kinds.registerResourceKind(ResourceKind.of("test:core/pool", "pools", Pool.class))));
        assertEquals("Resource kind test:core/pool must be test:other or start with test:other/, the id of its registering"
                + " module test:other from host classpath", foreign.getMessage());

        var sameType = assertThrows(IllegalArgumentException.class, () -> registerAs(registry, "test:other",
                kinds -> kinds.registerResourceKind(ResourceKind.of("test:other/castle", "keeps", Castle.class))));
        assertEquals("resource kind test:other/castle of module test:other from host classpath reads into " + Castle.class.getName()
                + ", which resource kind test:core/castle of module test:core from host classpath reads into already; resources"
                + " are found by their type, so each kind needs a class of its own", sameType.getMessage());

        var sameFolder = assertThrows(IllegalArgumentException.class, () -> registerAs(registry, "test:other",
                kinds -> kinds.registerResourceKind(ResourceKind.of("test:other/pool", "castles", Pool.class))));
        assertEquals("resource kind test:other/pool of module test:other from host classpath reads folder 'castles', which"
                + " resource kind test:core/castle of module test:core from host classpath reads already; a folder holds one kind,"
                + " so give one of them a folder of its own", sameFolder.getMessage());

        var hostFolder = assertThrows(IllegalArgumentException.class, () -> registerAs(registry, "test:other",
                kinds -> kinds.registerResourceKind(ResourceKind.of("test:other/pool", "biomes", Pool.class))));
        assertEquals("Resource kind test:other/pool of module test:other from host classpath reads folder 'biomes', which the"
                + " host reads itself; give the kind a folder of its own", hostFolder.getMessage());

        assertThrows(IllegalStateException.class, () -> registry.registerResourceKind(ResourceKind.of("test:core/pool", "pools", Pool.class)));
        assertEquals(List.of(castles), registry.freeze().resourceKinds().stream().map(ResourceKindRegistration::kind).toList());
        assertEquals("test:core", registry.freeze().resourceKinds().getFirst().module().info().id().toString());
    }

    @Test
    void validatorsAreCataloguedByModuleIdInTheOrderEachModuleRegisteredThem() {
        var registry = new ModuleRegistry();
        Validator first = pack -> {};
        Validator second = pack -> {};
        Validator other = pack -> {};
        registerAs(registry, "test:zeta", validators -> validators.registerValidator(other));
        registerAs(registry, "test:alpha", validators -> {
            validators.registerValidator(first);
            validators.registerValidator(second);
        });

        assertThrows(IllegalStateException.class, () -> registry.registerValidator(pack -> {}));
        assertEquals(List.of(first, second, other), registry.freeze().validators().stream().map(ValidatorRegistration::validator).toList());
        assertEquals("test:alpha", registry.freeze().validators().getFirst().module().info().id().toString());
        assertThrows(IllegalStateException.class, () -> registry.registerValidator(pack -> {}), "registration has ended");
    }

    @Service(Providers.ALL)
    interface Greeting { String greet(); }
    @Service(Providers.ONE)
    interface Clock { long now(); }
    interface NotAService { void run(); }

    @Test
    void lifecyclesAreCataloguedByModuleIdInTheOrderEachModuleRegisteredThem() {
        var registry = new ModuleRegistry();
        ModuleLifecycle first = new ModuleLifecycle() {};
        ModuleLifecycle second = new ModuleLifecycle() {};
        ModuleLifecycle other = new ModuleLifecycle() {};
        registerAs(registry, "test:zeta", hooks -> hooks.registerLifecycle(other));
        registerAs(registry, "test:alpha", hooks -> {
            hooks.registerLifecycle(first);
            hooks.registerLifecycle(second);
        });

        assertThrows(IllegalStateException.class, () -> registry.registerLifecycle(new ModuleLifecycle() {}),
                "registration outside IrisModule.setup");
        var catalog = registry.freeze();
        assertEquals(List.of(first, second, other), catalog.lifecycles().stream().map(LifecycleRegistration::lifecycle).toList());
        assertEquals("test:alpha", catalog.lifecycles().getFirst().module().info().id().toString());
    }

    @Test
    void serviceProvidersComeInModuleIdOrderAndEachModulesInRegistrationOrder() {
        var registry = new ModuleRegistry();
        Greeting zeta = () -> "zeta";
        Greeting alphaFirst = () -> "alpha 1";
        Greeting alphaSecond = () -> "alpha 2";
        registerAs(registry, "test:zeta", services -> services.registerService(Greeting.class, zeta));
        registerAs(registry, "test:alpha", services -> {
            services.registerService(Greeting.class, alphaFirst);
            services.registerService(Greeting.class, alphaSecond);
        });

        var catalog = registry.freeze();
        assertEquals(List.of(alphaFirst, alphaSecond, zeta), catalog.services(Greeting.class));
        assertEquals(List.of(), catalog.services(Clock.class), "a service nobody provides has no providers");
        assertEquals(Optional.empty(), catalog.service(Clock.class));
        var several = assertThrows(IllegalArgumentException.class, () -> catalog.service(Greeting.class));
        assertTrue(several.getMessage().contains("services(...)"), several.getMessage());
        assertThrows(IllegalArgumentException.class, () -> catalog.services(NotAService.class));
        assertThrows(IllegalArgumentException.class, () -> catalog.registrations(NotAService.class), "asking again still refuses it");
        assertEquals(List.of(alphaFirst, alphaSecond, zeta),
                catalog.registrations(Greeting.class).stream().map(ServiceRegistration::provider).toList());
        assertEquals(List.of(), catalog.registrations(Clock.class));
        assertThrows(UnsupportedOperationException.class, () -> catalog.services(Greeting.class).clear(), "the catalog is frozen");
    }

    @Test
    void aServiceWithOneProviderFailsTheLoadWhenASecondModuleProvidesIt() {
        var registry = new ModuleRegistry();
        Clock clock = () -> 1L;
        registerAs(registry, "test:alpha", services -> services.registerService(Clock.class, clock));
        registerAs(registry, "test:zeta", services -> {
            var duplicate = assertThrows(IllegalArgumentException.class, () -> services.registerService(Clock.class, () -> 2L));
            assertTrue(duplicate.getMessage().contains("test:alpha from host classpath"), duplicate.getMessage());
            assertTrue(duplicate.getMessage().contains("test:zeta from host classpath"), duplicate.getMessage());
        });

        assertEquals(Optional.of(clock), registry.freeze().service(Clock.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"}) // A raw class lets a provider of the wrong type reach the check.
    void servicesMustBeMarkedInterfacesThatTheProviderImplements() {
        var registry = new ModuleRegistry();
        registerAs(registry, "test:alpha", services -> {
            var unmarked = assertThrows(IllegalArgumentException.class,
                    () -> services.registerService(NotAService.class, () -> {}));
            assertTrue(unmarked.getMessage().contains("@" + Service.class.getName()), unmarked.getMessage());
            assertThrows(IllegalArgumentException.class, () -> services.registerService(Object.class, new Object()));
            assertThrows(IllegalArgumentException.class, () -> services.registerService((Class) Greeting.class, (Clock) () -> 1L));
            assertThrows(NullPointerException.class, () -> services.registerService(Greeting.class, null));
        });
        assertThrows(IllegalStateException.class, () -> registry.registerService(Greeting.class, () -> "outside"));
        assertEquals(List.of(), registry.freeze().services(Greeting.class));
    }

    @Test
    void vanillaStructuresAreAServiceEveryModuleMayProvide() {
        var registry = new ModuleRegistry();
        VanillaStructures first = new VanillaStructures() { };
        VanillaStructures other = new VanillaStructures() { };
        registerAs(registry, "test:zeta", services -> services.registerService(VanillaStructures.class, other));
        registerAs(registry, "test:alpha", services -> services.registerService(VanillaStructures.class, first));

        assertEquals(List.of(first, other), registry.freeze().services(VanillaStructures.class));
        var registrations = registry.freeze().registrations(VanillaStructures.class);
        assertEquals(List.of(first, other), registrations.stream().map(ServiceRegistration::provider).toList());
        assertEquals("test:alpha", registrations.getFirst().module().info().id().toString());
    }

    private static MantleCodec<String> codec(int version) {
        return new MantleCodec<>() {
            public int version() { return version; }
            public void write(DataOutput output, String value) {}
            public String read(DataInput input, int storedVersion) { return ""; }
        };
    }
}
