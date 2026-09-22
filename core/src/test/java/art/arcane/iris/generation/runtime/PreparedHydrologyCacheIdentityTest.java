package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.GenerationKernelRegistry;
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
import static org.mockito.Mockito.mockStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
