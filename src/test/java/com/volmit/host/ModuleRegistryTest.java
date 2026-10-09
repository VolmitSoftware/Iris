package com.volmit.host;

import com.volmit.*;
import com.volmit.config.Feature;
import com.volmit.mantle.*;
import com.volmit.resource.ResourceKind;
import com.volmit.service.Providers;
import com.volmit.service.Service;
import com.volmit.validation.Validator;
import com.volmit.vanilla.VanillaStructureHook;
import com.volmit.vanilla.VanillaStructureRule;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
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
    @Feature(id = "test:core/one", name = "One", targets = FeatureAttachment.Target.BIOME)
    static class One implements IrisFeature {}
    @Feature(id = "test:core/two", name = "Two", targets = FeatureAttachment.Target.DIMENSION)
    static class Two implements IrisFeature {}
    @Feature(id = "test:core/one", name = "Duplicate", targets = FeatureAttachment.Target.REGION)
    static class Duplicate implements IrisFeature {}
    static class MissingAnnotation implements IrisFeature {}
    @Feature(id = "test:core/abstract", name = "Abstract", targets = FeatureAttachment.Target.BIOME)
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
            public void install(StageGraph graph) { graph.define(WORK).when(One.class).run((context, one) -> {}); }
            public void register(FeatureRegistry registry) { registry.registerObject(Two.class, ignored -> context -> null); }
        };
        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(conditioned)), Set.of(), Set.of());
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
                () -> LoadedModules.load(List.of(DiscoveredModule.builtIn(first), DiscoveredModule.builtIn(second)), Set.of(), Set.of()));

        assertTrue(clash.getMessage().startsWith("Duplicate module test:core from "), clash.getMessage());
        assertTrue(clash.getMessage().contains("already loaded from "), clash.getMessage());
    }

    private static IrisModule ownerOfOne(String name) {
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:core", name, "1"); }
            public void install(StageGraph graph) {}
            public void register(FeatureRegistry registry) { registry.registerSettings(One.class); }
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
            public void install(StageGraph graph) {}
            public void register(FeatureRegistry registry) { registry.registerSettings(One.class); }
        };
        var user = new IrisModule() {
            static final PlacementStage<Void> WORK = Stage.placement("test:user/work");
            public ModuleInfo info() { return ModuleInfo.of("test:user", "User", "1"); }
            public void install(StageGraph graph) { graph.define(WORK).when(One.class).run((context, one) -> {}); }
        };

        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(owner), DiscoveredModule.builtIn(user)), Set.of(), Set.of());
        assertEquals(List.of("test:core/one"), loaded.catalog().settings().stream().map(value -> value.id().toString()).toList());

        var unowned = assertThrows(IllegalArgumentException.class,
                () -> LoadedModules.load(List.of(DiscoveredModule.builtIn(user)), Set.of(), Set.of()));
        assertTrue(unowned.getMessage().contains("which none of the modules naming it in when(...) owns"), unowned.getMessage());
    }

    static class Castle {}
    static class Pool {}

    @Test
    void resourceKindsHaveOwnedIdsAndOneIdTypeAndFolderEach() {
        var registry = new ModuleRegistry(Set.of("biomes"));
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
                "registration outside IrisModule.register");
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
    void vanillaStructureHooksAreCataloguedByModuleIdInTheOrderEachModuleRegisteredThem() {
        var registry = new ModuleRegistry();
        VanillaStructureHook first = (structure, world) -> VanillaStructureRule.REPLACE;
        VanillaStructureHook second = (structure, world) -> VanillaStructureRule.GENERATE;
        VanillaStructureHook other = (structure, world) -> VanillaStructureRule.BLOCK;
        registerAs(registry, "test:zeta", hooks -> hooks.registerVanillaStructureHook(other));
        registerAs(registry, "test:alpha", hooks -> {
            hooks.registerVanillaStructureHook(first);
            hooks.registerVanillaStructureHook(second);
        });

        assertThrows(IllegalStateException.class, () -> registry.registerVanillaStructureHook(first), "outside register");
        var hooks = registry.freeze().vanillaStructureHooks();
        assertEquals(List.of(first, second, other), hooks.stream().map(VanillaStructureHookRegistration::hook).toList());
        assertEquals("test:alpha", hooks.getFirst().module().info().id().toString());
        assertThrows(IllegalStateException.class, () -> registry.registerVanillaStructureHook(first), "registration has ended");
    }

    private static MantleCodec<String> codec(int version) {
        return new MantleCodec<>() {
            public int version() { return version; }
            public void write(DataOutput output, String value) {}
            public String read(DataInput input, int storedVersion) { return ""; }
        };
    }
}
