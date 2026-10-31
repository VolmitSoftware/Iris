package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WorldgenExportDeepCaves263Test {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final long SEED = 1337L;
    private static final String NAMESPACE = WorldgenExportRequest.DEFAULT_NAMESPACE;
    private static final String DIMENSION_ID = NAMESPACE + ":" + ExportTestPacks.DIMENSION;
    private static final int MIN_Y = -64;
    private static final int LAVA_Y = -62;
    private static final int LAVA_LINE = -54;
    private static final int CAVE_TOP = -14;
    private static final int CHUNK_WIDTH = 16;
    private static final String CAVE_FIELD = "cave/field";
    private static final String DIMENSION = """
            {
              "regions": ["region"],
              "dimensionHeight": {"min": -64, "max": 192},
              "fluidHeight": 63,
              "caveLavaHeight": 2,
              "caveProfile": {
                "enabled": true,
                "verticalRange": {"min": 0, "max": 50},
                "verticalEdgeFade": 0,
                "baseDensityStyle": {"style": "SIMPLEX", "zoom": 0.6},
                "detailDensityStyle": {"style": "SIMPLEX", "zoom": 0.3},
                "baseWeight": 1.0,
                "detailWeight": 0.0,
                "densityThreshold": {"min": 5.0, "max": 5.0, "style": {"style": "SIMPLEX"}},
                "thresholdBias": 0.0,
                "surfaceClearance": 0,
                "allowSurfaceBreak": false,
                "allowFluid": false,
                "allowLava": true,
                "modules": []
              }
            }
            """;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void deepCavesOpenAsAirAboveTheIrisLavaBand() throws IOException {
        WorldgenExportResult result = export();

        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        JsonObject head = JsonParser.parseString(Files.readString(result.output().resolve("data/" + NAMESPACE + "/worldgen/material_rule/"
                + ExportTestPacks.DIMENSION + ".json"), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("sequence").get(0).getAsJsonObject();
        assertEquals("minecraft:ore_vein", head.get("type").getAsString());
        String opening = head.get("density").getAsString();
        assertTrue(opening, Files.isRegularFile(densityFunction(result.output(), opening)));
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(result.output(), WorldgenExportMode.OVERWORLD, DIMENSION_ID, SEED)) {
            ProtoChunk chunk = sampler.terrainChunk(0, 0);
            for (int x = 0; x < CHUNK_WIDTH; x++) {
                for (int z = 0; z < CHUNK_WIDTH; z++) {
                    for (int y = MIN_Y; y <= CAVE_TOP; y++) {
                        BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
                        String where = state + " at " + x + "," + y + "," + z;
                        if (y <= LAVA_Y) {
                            assertTrue(where, state.is(Blocks.LAVA));
                        } else {
                            assertTrue(where, state.isAir());
                        }
                    }
                }
            }
            assertTrue(chunk.getBlockState(new BlockPos(0, LAVA_LINE - 1, 0)).isAir());
        }
    }

    @Test
    public void deepCaveRuleSharesTheCaveFieldCacheWithTheFinalDensity() throws IOException {
        WorldgenExportResult result = export();
        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());

        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(result.output(), WorldgenExportMode.OVERWORLD, DIMENSION_ID, SEED)) {
            CompiledCaches caches = CompiledCaches.open(sampler.registries(), SEED);
            DensityFunction opening = caches.oreVeins().getFirst().density();
            int field = caches.rootId(caches.function(NAMESPACE + ":" + CAVE_FIELD));
            Set<Integer> finalDensity = caches.ids(caches.finalDensity());

            assertTrue(caches.ids(opening).toString(), caches.ids(opening).contains(field));
            assertTrue(finalDensity.toString(), finalDensity.contains(field));
            assertTrue(finalDensity.toString(), finalDensity.contains(caches.rootId(opening)));
        }
    }

    private WorldgenExportResult export() throws IOException {
        File pack = ExportTestPacks.withDimension(temporaryFolder.newFolder("pack"), DIMENSION);
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(pack.toPath())
                .dimension(ExportTestPacks.DIMENSION)
                .exportRoot(temporaryFolder.newFolder("exports").toPath())
                .format("directory")
                .build();
        return WorldgenDatapackExporter.create().export(request, WorldgenExportProgress.NONE);
    }

    private static Path densityFunction(Path datapack, String id) {
        int separator = id.indexOf(':');
        return datapack.resolve("data/" + id.substring(0, separator) + "/worldgen/density_function/" + id.substring(separator + 1) + ".json");
    }
}
