package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.GenerationKernelRegistry;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import org.junit.Test;
import org.junit.Rule;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import art.arcane.iris.pack.PackValidationCache;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.studio.StudioMode;
import static org.junit.Assert.assertNotNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.IrisServices;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mockStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PreparedHydrologyCacheIdentityTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void frozenStudioAndProductionCopiesShareUntilPackBytesChange() throws Exception {
        Path studio = folder.newFolder("studio").toPath();
        Path production = folder.newFolder("production").toPath();
        Files.writeString(studio.resolve("terrain.json"), "{\"height\":80}");
        Files.copy(studio.resolve("terrain.json"), production.resolve("terrain.json"));
        GenerationKernelRegistry.RuntimeKernel kernel = kernel("implementation");
        try (MockedStatic<PackValidationCache> validation = mockStatic(PackValidationCache.class)) {
            validation.when(PackValidationCache::contextFingerprint).thenReturn("platform");
            PreparedHydrologyCacheIdentity prepared = PreparedHydrologyCacheIdentity.capture(target(studio), kernel, null, true);
            assertEquals(prepared, PreparedHydrologyCacheIdentity.capture(target(production), kernel, null, false));
            Files.writeString(production.resolve("terrain.json"), "{\"height\":81}");
            assertNotEquals(prepared, PreparedHydrologyCacheIdentity.capture(target(production), kernel, null, false));
        }
    }

    @Test
    public void biomeBuffetLayoutsAreIsolatedWhileNormalStudioSharesProduction() throws Exception {
        Path pack = folder.newFolder("layout").toPath();
        Files.writeString(pack.resolve("terrain.json"), "{}");
        EngineTarget target = target(pack);
        GenerationKernelRegistry.RuntimeKernel kernel = kernel("implementation");
        when(IrisPlatforms.get().platformName()).thenReturn("bukkit");
        try (MockedStatic<PackValidationCache> validation = mockStatic(PackValidationCache.class)) {
            validation.when(PackValidationCache::contextFingerprint).thenReturn("platform");
            PreparedHydrologyCacheIdentity normal = PreparedHydrologyCacheIdentity.capture(target, kernel, null, false);
            assertNotNull(normal);
            assertEquals(normal, PreparedHydrologyCacheIdentity.capture(target, kernel, null, true));
            when(target.getDimension().getStudioMode()).thenReturn(StudioMode.BIOME_BUFFET_3x3);
            PreparedHydrologyCacheIdentity buffet = PreparedHydrologyCacheIdentity.capture(target, kernel, null, true);
            assertNotEquals(normal, buffet);
            assertEquals(normal, PreparedHydrologyCacheIdentity.capture(target, kernel, null, false));
            when(target.getDimension().getStudioMode()).thenReturn(StudioMode.BIOME_BUFFET_9x9);
            assertNotEquals(buffet, PreparedHydrologyCacheIdentity.capture(target, kernel, null, true));
        }
    }

    @Test
    public void visibleFallbackPackBytesAndNamesSeparatePlans() throws Exception {
        Path targetPack = folder.newFolder("target").toPath();
        Path packs = folder.newFolder("packs").toPath();
        Path dependency = Files.createDirectory(packs.resolve("shared"));
        Path content = dependency.resolve("terrain.json");
        Files.writeString(content, "{\"height\":80}");
        EngineTarget target = target(targetPack);
        when(target.getData().hasGenerationRegistryContract()).thenReturn(false);
        when(IrisPlatforms.get().packsFolderNoCreate()).thenReturn(packs.toFile());
        GenerationKernelRegistry.RuntimeKernel kernel = kernel("implementation");
        try (MockedStatic<PackValidationCache> validation = mockStatic(PackValidationCache.class)) {
            validation.when(PackValidationCache::contextFingerprint).thenReturn("platform");
            PreparedHydrologyCacheIdentity initial = PreparedHydrologyCacheIdentity.capture(target, kernel, null, false);
            assertNotNull(initial);
            Files.writeString(content, "{\"height\":81}");
            PreparedHydrologyCacheIdentity changed = PreparedHydrologyCacheIdentity.capture(target, kernel, null, false);
            assertNotNull(changed);
            assertNotEquals(initial, changed);
            Files.move(dependency, packs.resolve("renamed"));
            PreparedHydrologyCacheIdentity renamed = PreparedHydrologyCacheIdentity.capture(target, kernel, null, false);
            assertNotNull(renamed);
            assertNotEquals(changed, renamed);
            Path hidden = Files.createDirectory(packs.resolve(".hidden"));
            Files.writeString(hidden.resolve("terrain.json"), "{\"height\":90}");
            assertEquals(renamed, PreparedHydrologyCacheIdentity.capture(target, kernel, null, false));
        }
    }

    @Test
    public void visiblePackSymlinksUseLogicalNamesAndResolvedContent() throws Exception {
        Path targetPack = folder.newFolder("symlink-target").toPath();
        Path first = folder.newFolder("first-packs").toPath();
        Path second = folder.newFolder("second-packs").toPath();
        Path dependency = Files.createDirectory(first.resolve("shared"));
        Files.writeString(dependency.resolve("terrain.json"), "{}");
        Files.createSymbolicLink(second.resolve("shared"), dependency);
        EngineTarget target = target(targetPack);
        when(target.getData().hasGenerationRegistryContract()).thenReturn(false);
        GenerationKernelRegistry.RuntimeKernel kernel = kernel("implementation");
        try (MockedStatic<PackValidationCache> validation = mockStatic(PackValidationCache.class)) {
            validation.when(PackValidationCache::contextFingerprint).thenReturn("platform");
            when(IrisPlatforms.get().packsFolderNoCreate()).thenReturn(first.toFile());
            PreparedHydrologyCacheIdentity direct = PreparedHydrologyCacheIdentity.capture(target, kernel, null, false);
            assertNotNull(direct);
            when(IrisPlatforms.get().packsFolderNoCreate()).thenReturn(second.toFile());
            assertEquals(direct, PreparedHydrologyCacheIdentity.capture(target, kernel, null, false));
        }
    }

    private static EngineTarget target(Path path) {
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(path.toFile());
        when(data.hasGenerationRegistryContract()).thenReturn(true);
        when(data.generationPackFingerprint()).thenAnswer(invocation ->
                GenerationPackFingerprint.compute(path, GenerationPackFingerprint.CURRENT_VERSION));
        IrisWorld world = mock(IrisWorld.class);
        when(world.minHeight()).thenReturn(-64);
        when(world.maxHeight()).thenReturn(320);
        EngineTarget target = mock(EngineTarget.class);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getStudioMode()).thenReturn(StudioMode.NORMAL);
        when(target.getDimension()).thenReturn(dimension);
        when(target.getData()).thenReturn(data);
        when(target.getWorld()).thenReturn(world);
        return target;
    }

    @Test
    public void immutableGenerationPackUsesTheDataFingerprintWithoutRereadingThePack() throws Exception {
        Path missing = folder.getRoot().toPath().resolve("immutable-epoch-pack");
        EngineTarget target = target(missing);
        IrisData data = target.getData();
        doReturn("verified-epoch").when(data).generationPackFingerprint();
        GenerationKernelRegistry.RuntimeKernel kernel = kernel("implementation");
        try (MockedStatic<PackValidationCache> validation = mockStatic(PackValidationCache.class)) {
            validation.when(PackValidationCache::contextFingerprint).thenReturn("platform");

            PreparedHydrologyCacheIdentity captured = PreparedHydrologyCacheIdentity.capture(target, kernel, null, false);

            assertEquals(PreparedHydrologyCacheIdentity.fromInputs(
                    "verified-epoch", "closed", "platform", kernel, null, -64, 320, 0), captured);
        }
    }

    @Test
    public void identicalInputsShareRegardlessOfWorldLocation() {
        GenerationKernelRegistry.RuntimeKernel kernel = kernel("implementation");
        assertEquals(identity("pack", "closed", "platform", kernel, null, -64),
                identity("pack", "closed", "platform", kernel, null, -64));
    }

    @Test
    public void everyExternalInputAndTransitionSeparatesPlans() {
        GenerationKernelRegistry.RuntimeKernel kernel = kernel("implementation");
        PreparedHydrologyCacheIdentity base = identity("pack", "closed", "platform", kernel, null, -64);
        assertNotEquals(base, identity("changed", "closed", "platform", kernel, null, -64));
        assertNotEquals(base, identity("pack", "dependency-changed", "platform", kernel, null, -64));
        assertNotEquals(base, identity("pack", "closed", "platform-changed", kernel, null, -64));
        assertNotEquals(base, identity("pack", "closed", "platform", kernel("changed"), null, -64));
        assertNotEquals(base, identity("pack", "closed", "platform", kernel, null, 0));
        TransitionGenerationPlan.Specification transition = new TransitionGenerationPlan.Specification(
                1L, "old", "new", 1, 256, "boundary", "terrain");
        assertNotEquals(base, identity("pack", "closed", "platform", kernel, transition, -64));
        assertNotEquals(identity("pack", "closed", "platform", kernel, transition, -64),
                identity("pack", "closed", "platform", kernel,
                        new TransitionGenerationPlan.Specification(1L, "old", "new", 1, 256, "other", "terrain"), -64));
    }

    @Test
    public void hydrologyPlanScopeFollowsIdentitySeedAndSettings() {
        GenerationKernelRegistry.RuntimeKernel kernel = kernel("implementation");
        PreparedHydrologyCacheIdentity base = identity("pack", "closed", "platform", kernel, null, -64);
        String scope = base.planScope(1337L, 42L);
        assertEquals(scope, identity("pack", "closed", "platform", kernel, null, -64).planScope(1337L, 42L));
        assertEquals(64, scope.length());
        assertTrue(scope.matches("[0-9a-f]+"));
        assertNotEquals(scope, base.planScope(1338L, 42L));
        assertNotEquals(scope, base.planScope(1337L, 43L));
        assertNotEquals(scope, identity("changed", "closed", "platform", kernel, null, -64).planScope(1337L, 42L));
    }

    @Test
    public void onlyAConfirmedIdentityOpensAPlanStoreAndRecordingOneNeverDoes() throws Exception {
        Path world = folder.newFolder("world").toPath();
        Path root = world.resolve("iris").resolve("hydrology").resolve("main");
        Path pack = HydrologyFixture.pack(folder.newFolder("hydrology-pack").toPath(), null, null, true);
        withComplexEnvironment(() -> {
            try (HydrologyFixture unconfirmed = new HydrologyFixture(pack, world.toFile())) {
                unconfirmed.complex.openHydrologyPlanStore();
                assertFalse(Files.exists(root));
                IrisComplex detached = unconfirmed.detachedComplex();
                detached.enablePreparedHydrologyCache(new PreparedHydrologyCacheIdentity("b".repeat(64)));
                assertFalse(Files.exists(root));
            }
            try (HydrologyFixture main = new HydrologyFixture(pack, world.toFile())) {
                main.complex.enablePreparedHydrologyCache(new PreparedHydrologyCacheIdentity("a".repeat(64)));
                main.complex.openHydrologyPlanStore();
                assertEquals(1, scopes(root).size());
            }
        });
    }

    @Test
    public void detachedRuntimesNeverPruneTheMainRuntimePlans() throws Exception {
        Path world = folder.newFolder("shared-world").toPath();
        Path root = world.resolve("iris").resolve("hydrology").resolve("main");
        Path pack = HydrologyFixture.pack(folder.newFolder("shared-pack").toPath(), null, null, true);
        withComplexEnvironment(() -> {
            try (HydrologyFixture fixture = new HydrologyFixture(pack, world.toFile())) {
                fixture.complex.enablePreparedHydrologyCache(new PreparedHydrologyCacheIdentity("a".repeat(64)));
                fixture.complex.openHydrologyPlanStore();
                List<Path> opened = scopes(root);
                assertEquals(1, opened.size());
                Path plan = Files.writeString(opened.getFirst().resolve("w-5-5.bin.gz"), "plan");
                for (String identity : List.of("b".repeat(64), "c".repeat(64))) {
                    IrisComplex detached = fixture.detachedComplex();
                    detached.enablePreparedHydrologyCache(new PreparedHydrologyCacheIdentity(identity));
                }
                assertEquals(opened, scopes(root));
                assertTrue(Files.exists(plan));
            }
        });
    }

    @Test
    public void persistenceMovesToThePromotedRuntimeAfterTheDemotedStoreCloses() throws Exception {
        Path world = folder.newFolder("promoted-world").toPath();
        Path root = world.resolve("iris").resolve("hydrology").resolve("main");
        Path pack = HydrologyFixture.pack(folder.newFolder("promoted-pack").toPath(), null, null, true);
        withComplexEnvironment(() -> {
            try (HydrologyFixture fixture = new HydrologyFixture(pack, world.toFile())) {
                fixture.complex.enablePreparedHydrologyCache(new PreparedHydrologyCacheIdentity("a".repeat(64)));
                fixture.complex.openHydrologyPlanStore();
                Path demotedScope = scopes(root).getFirst();
                Path plan = Files.writeString(demotedScope.resolve("w-5-5.bin.gz"), "plan");
                IrisComplex promoted = fixture.detachedComplex();
                promoted.enablePreparedHydrologyCache(new PreparedHydrologyCacheIdentity("b".repeat(64)));
                assertEquals(List.of(demotedScope), scopes(root));
                fixture.complex.closeHydrologyPlanStore();
                promoted.openHydrologyPlanStore();
                assertEquals(2, scopes(root).size());
                assertTrue(Files.exists(plan));
                promoted.closeHydrologyPlanStore();
                fixture.complex.openHydrologyPlanStore();
                assertEquals(2, scopes(root).size());
                assertTrue(Files.exists(plan));
            }
        });
    }

    @Test
    public void packChangedDuringCompilationKeepsTheNewRuntimePlansInMemory() throws Exception {
        Path world = folder.newFolder("changing-world").toPath();
        Path root = world.resolve("iris").resolve("hydrology").resolve("main");
        Path pack = HydrologyFixture.pack(folder.newFolder("changing-hydrology-pack").toPath(), null, null, true);
        Path content = folder.newFolder("changing-content").toPath();
        Files.writeString(content.resolve("terrain.json"), "{\"height\":80}");
        EngineTarget target = target(content);
        GenerationKernelRegistry.RuntimeKernel kernel = kernel("implementation");
        withComplexEnvironment(() -> {
            try (MockedStatic<PackValidationCache> validation = mockStatic(PackValidationCache.class)) {
                validation.when(PackValidationCache::contextFingerprint).thenReturn("platform");
                PreparedHydrologyCacheIdentity captured = PreparedHydrologyCacheIdentity.capture(target, kernel, null, false);
                Files.writeString(content.resolve("terrain.json"), "{\"height\":81}");
                try (HydrologyFixture changed = new HydrologyFixture(pack, world.toFile())) {
                    captured.enableIfUnchanged(target, kernel, null, changed.complex, false);
                    changed.complex.openHydrologyPlanStore();
                    assertFalse(Files.exists(root));
                }
                PreparedHydrologyCacheIdentity settled = PreparedHydrologyCacheIdentity.capture(target, kernel, null, false);
                try (HydrologyFixture unchanged = new HydrologyFixture(pack, world.toFile())) {
                    settled.enableIfUnchanged(target, kernel, null, unchanged.complex, false);
                    unchanged.complex.openHydrologyPlanStore();
                    assertEquals(1, scopes(root).size());
                }
            }
        });
    }

    private static List<Path> scopes(Path root) throws IOException {
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory).sorted().toList();
        }
    }

    private static void withComplexEnvironment(ThrowingRunnable body) throws Exception {
        IrisSettings previous = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        try {
            body.run();
        } finally {
            IrisServices.remove(PreservationRegistry.class);
            IrisSettings.settings = previous;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static PreparedHydrologyCacheIdentity identity(String pack, String dependencies, String platform,
            GenerationKernelRegistry.RuntimeKernel kernel, TransitionGenerationPlan.Specification transition, int minY) {
        return PreparedHydrologyCacheIdentity.fromInputs(pack, dependencies, platform, kernel, transition, minY, 320, 0);
    }

    private static GenerationKernelRegistry.RuntimeKernel kernel(String implementation) {
        GenerationKernelRegistry.RuntimeKernel kernel = mock(GenerationKernelRegistry.RuntimeKernel.class);
        when(kernel.version()).thenReturn(new GenerationKernelRegistry.Version(1, 1, 1));
        when(kernel.implementationFingerprint()).thenReturn(implementation);
        return kernel;
    }
}
