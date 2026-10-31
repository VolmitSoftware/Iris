package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockProperty;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockStateResolverTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void invalidPropertyExportsDefaultStateWithDiagnostic() throws Exception {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisData data = IrisData.openRuntime(ExportTestPacks.basic(temporaryFolder.newFolder("pack")));
        try {
            BlockStateResolver resolver = new BlockStateResolver(data, registries(), diagnostics);

            Optional<VanillaBlockState> valid = resolver.resolve("oak_log[axis=x]", "biomes/a");
            Optional<VanillaBlockState> invalid = resolver.resolve("OAK_LOG[axis=q]", "biomes/a");

            assertEquals("minecraft:oak_log[axis=x]", valid.orElseThrow().canonical());
            assertEquals("minecraft:oak_log", invalid.orElseThrow().canonical());
            assertEquals(1, diagnostics.size());
            assertSame(ExportLimit.BLOCK_STATE, diagnostics.getFirst().limit());
            assertEquals("biomes/a", diagnostics.getFirst().resource());
            resolver.resolve("oak_log[axis=q]", "biomes/a");
            assertEquals(1, diagnostics.size());
        } finally {
            data.close();
        }
    }

    @Test
    public void foreignBlocksAreNotExported() throws Exception {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisData data = IrisData.openRuntime(ExportTestPacks.basic(temporaryFolder.newFolder("pack")));
        try {
            BlockStateResolver resolver = new BlockStateResolver(data, null, diagnostics);

            assertTrue(resolver.resolve("somemod:glowing_rock", "biomes/b").isEmpty());
            assertEquals(1, diagnostics.size());
            assertSame(ExportLimit.BLOCK_STATE, diagnostics.getFirst().limit());
        } finally {
            data.close();
        }
    }

    @Test
    public void legacyNamesFollowTheRenameTable() throws Exception {
        IrisData data = IrisData.openRuntime(ExportTestPacks.basic(temporaryFolder.newFolder("pack")));
        try {
            BlockStateResolver resolver = new BlockStateResolver(data, registries(), new ArrayList<>());

            assertEquals("minecraft:short_grass", resolver.resolve("grass", "biomes/c").orElseThrow().canonical());
        } finally {
            data.close();
        }
    }

    private static PlatformRegistries registries() {
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.blockTypeKeys()).thenReturn(List.of("minecraft:oak_log", "minecraft:short_grass", "minecraft:stone"));
        when(registries.blockStateProperties()).thenReturn(Map.of("minecraft:oak_log",
                List.of(new NativeBlockProperty("axis", "string", "y", List.of("x", "y", "z"), null))));
        return registries;
    }
}
