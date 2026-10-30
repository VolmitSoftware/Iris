package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import art.arcane.iris.testsupport.CompatTestPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WorldgenExportSeaLevel263Test {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final long SEED = 1337L;
    private static final String DIMENSION_ID = WorldgenExportRequest.DEFAULT_NAMESPACE + ":" + ExportTestPacks.DIMENSION;
    private static final int FLUID_HEIGHT = 40;
    private static final int SEA_FLOOR = 28;
    private static final int CHUNK_WIDTH = 16;
    private static final String DIMENSION = """
            {
              "regions": ["region"],
              "dimensionHeight": {"min": -64, "max": 192},
              "fluidHeight": 40,
              "landChance": 0,
              "carvingEnabled": false
            }
            """;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void waterFillsThroughTheIrisFluidHeight() throws IOException {
        WorldgenExportResult result = export();

        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(result.output(), WorldgenExportMode.OVERWORLD, DIMENSION_ID, SEED)) {
            ProtoChunk chunk = sampler.terrainChunk(0, 0);
            for (int x = 0; x < CHUNK_WIDTH; x++) {
                for (int z = 0; z < CHUNK_WIDTH; z++) {
                    assertSolid(chunk.getBlockState(new BlockPos(x, SEA_FLOOR, z)), x, z);
                    for (int y = SEA_FLOOR + 1; y <= FLUID_HEIGHT; y++) {
                        BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
                        assertTrue(state + " at " + x + "," + y + "," + z, state.is(Blocks.WATER));
                    }
                    BlockState above = chunk.getBlockState(new BlockPos(x, FLUID_HEIGHT + 1, z));
                    assertTrue(above + " above the fluid height at " + x + "," + z, above.isAir());
                }
            }
            assertEquals(FLUID_HEIGHT + 1, sampler.seaLevel());
        }
    }

    private static void assertSolid(BlockState state, int x, int z) {
        assertTrue(state + " sea floor at " + x + "," + z, !state.isAir() && state.getFluidState().isEmpty());
    }

    private WorldgenExportResult export() throws IOException {
        File pack = ExportTestPacks.withDimension(temporaryFolder.newFolder("pack"), DIMENSION);
        CompatTestPlatform.write(pack, "generators/flat.json", "{\"composite\":[]}");
        CompatTestPlatform.write(pack, "biomes/ocean.json", """
                {"name":"Ocean","generators":[{"generator":"flat","min":-12,"max":-12}]}
                """);
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(pack.toPath())
                .dimension(ExportTestPacks.DIMENSION)
                .exportRoot(temporaryFolder.newFolder("exports").toPath())
                .format("directory")
                .build();
        return WorldgenDatapackExporter.create().export(request, WorldgenExportProgress.NONE);
    }
}
