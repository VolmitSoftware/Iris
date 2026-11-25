package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Listed caves are not exported yet: the exported world has no Iris caves, and the export says so. */
public final class WorldgenExportDeepCaves263Test {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final long SEED = 1337L;
    private static final String NAMESPACE = WorldgenExportRequest.DEFAULT_NAMESPACE;
    private static final String DIMENSION_ID = NAMESPACE + ":" + ExportTestPacks.DIMENSION;
    private static final int MIN_Y = -64;
    private static final int DEEP_Y = -14;
    private static final int CHUNK_WIDTH = 16;
    private static final String DIMENSION = """
            {
              "regions": ["region"],
              "dimensionHeight": {"min": -64, "max": 192},
              "fluidHeight": 63,
              "caveLavaHeight": 2,
              "caves": ["tunnels"]
            }
            """;
    private static final String TUNNELS = """
            {"tunnel": {"width": {"min": 5, "max": 8}, "spacing": {"horizontal": 32, "vertical": 24}},
             "depth": {"min": -60, "max": 0}, "surfaceClearance": 0, "allowLava": true}
            """;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void listedCavesAreReportedAsNotExportedOnce() throws IOException {
        WorldgenExportResult result = export();

        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        long reports = result.diagnostics().stream().filter(diagnostic -> diagnostic.limit() == ExportLimit.LISTED_CAVES).count();
        assertEquals(result.diagnostics().toString(), 1, reports);
        assertTrue(result.diagnostics().stream().filter(diagnostic -> diagnostic.limit() == ExportLimit.LISTED_CAVES)
                .allMatch(diagnostic -> diagnostic.severity() == ExportDiagnostic.Severity.WARNING));
    }

    @Test
    public void theExportedWorldHasNoCaveFieldNoDeepCaveOpeningAndNoCaveAir() throws IOException {
        WorldgenExportResult result = export();
        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());

        Path materialRule = result.output().resolve("data/" + NAMESPACE + "/worldgen/material_rule/" + ExportTestPacks.DIMENSION + ".json");
        JsonObject rules = JsonParser.parseString(Files.readString(materialRule, StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse(rules.toString(), rules.toString().contains("cave/"));
        assertFalse(Files.exists(result.output().resolve("data/" + NAMESPACE + "/worldgen/density_function/cave/field.json")));
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(result.output(), WorldgenExportMode.OVERWORLD, DIMENSION_ID, SEED)) {
            ProtoChunk chunk = sampler.terrainChunk(0, 0);
            for (int x = 0; x < CHUNK_WIDTH; x++) {
                for (int z = 0; z < CHUNK_WIDTH; z++) {
                    for (int y = MIN_Y + 1; y <= DEEP_Y; y++) {
                        BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
                        assertFalse(state + " at " + x + "," + y + "," + z, state.isAir() || state.is(Blocks.LAVA));
                    }
                }
            }
        }
    }

    private WorldgenExportResult export() throws IOException {
        File pack = ExportTestPacks.withDimension(temporaryFolder.newFolder("pack"), DIMENSION);
        Files.createDirectories(pack.toPath().resolve("caves"));
        Files.writeString(pack.toPath().resolve("caves/tunnels.json"), TUNNELS, StandardCharsets.UTF_8);
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(pack.toPath())
                .dimension(ExportTestPacks.DIMENSION)
                .exportRoot(temporaryFolder.newFolder("exports").toPath())
                .format("directory")
                .build();
        return WorldgenDatapackExporter.create().export(request, WorldgenExportProgress.NONE);
    }
}
