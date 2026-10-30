package art.arcane.iris.probe.fidelity;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportIrFixtures;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.emit.WorldgenJsonWriter;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.ir.MaterialProgram;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;
import art.arcane.iris.probe.PaperRegistryIsolation;
import art.arcane.iris.probe.WorldgenExportSampler;
import com.google.gson.JsonParser;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class VanillaNoiseFidelitySourceTest {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final long SEED = 1337L;
    private static final String DIMENSION_ID = ExportIrFixtures.NAMESPACE + ":" + ExportIrFixtures.DIMENSION;
    private static final String BIOME_ID = ExportIrFixtures.NAMESPACE + ":" + ExportIrFixtures.BIOME;
    private static final String LOAD_KEY = "Flat Plains";
    private static final int LAND_HEIGHT = 80;
    private static final int MAX_Y = ExportIrFixtures.MIN_Y + ExportIrFixtures.HEIGHT;
    private static final String WALL_SHELL = ExportIrFixtures.NAMESPACE + ":cave/wall_shell";
    private static final int WALL_MIN_Y = 10;
    private static final int WALL_MAX_Y = 20;
    private static final int CARVED_HEIGHT = 120;
    private static final int VOID_MIN_Y = 96;
    private static final int CAVE_MIN_Y = 64;
    private static final Field BLOCK_Y = new Field.Coord(Axis.Y, Field.Tiling.CLAMP, -4064.0, 4062.0, -4064.0, 4062.0);

    @Test
    public void flatExportReportsTheConstantSurface() throws IOException {
        WorldgenExportSampler sampler = open(LAND_HEIGHT);
        try (VanillaNoiseFidelitySource source = new VanillaNoiseFidelitySource(sampler, Map.of(BIOME_ID, LOAD_KEY), WALL_SHELL)) {
            assertEquals(ExportIrFixtures.MIN_Y, source.minY());
            assertEquals(MAX_Y, source.maxY());
            for (int x : new int[]{-37, 0, 113}) {
                assertEquals(LAND_HEIGHT, sampler.baseHeight(x, -x));
            }
            for (int[] chunkPosition : new int[][]{{0, 0}, {-3, 5}}) {
                FidelityChunk chunk = source.sample(chunkPosition[0], chunkPosition[1]);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        assertEquals(LAND_HEIGHT - 1, chunk.surfaceY(x, z));
                        assertEquals(LOAD_KEY, chunk.surfaceBiome(x, z));
                        assertTrue(chunk.open(x, LAND_HEIGHT, z));
                        assertNull(chunk.block(x, LAND_HEIGHT - 1, z));
                    }
                }
                for (int y = ExportIrFixtures.MIN_Y; y < LAND_HEIGHT; y++) {
                    assertFalse("y " + y, chunk.open(7, y, 9));
                    assertFalse("y " + y, chunk.fluid(7, y, 9));
                }
            }

            FidelityStats stats = FidelityScanner.scan(source, 0, 0, 1, 4, 2);

            assertEquals(144L, stats.heightColumns());
            assertEquals(LAND_HEIGHT - 1, stats.heightQuantile(0.1D), 0.0D);
            assertEquals(LAND_HEIGHT - 1, stats.heightQuantile(0.9D), 0.0D);
            assertEquals(1.0D, stats.landFraction(), 0.0D);
            assertEquals(Map.of(LOAD_KEY, 144L), stats.biomeColumns());
            assertEquals(0L, stats.blockColumns());
            assertEquals(0L, stats.wallSolidVoxels());
        }
    }

    @Test
    public void wallShellDensityMarksWallVoxels() throws IOException {
        WorldgenExportSampler sampler = WorldgenExportSampler.open(flatExport(LAND_HEIGHT, wallShell()), WorldgenExportMode.PRESET, DIMENSION_ID, SEED);
        try (VanillaNoiseFidelitySource source = new VanillaNoiseFidelitySource(sampler, Map.of(BIOME_ID, LOAD_KEY), WALL_SHELL)) {
            FidelityChunk chunk = source.sample(2, -1);

            assertTrue(chunk.wallsKnown());
            assertTrue(chunk.wall(7, WALL_MIN_Y, 9));
            assertTrue(chunk.wall(7, WALL_MAX_Y - 1, 9));
            assertFalse(chunk.wall(7, WALL_MIN_Y - 1, 9));
            assertFalse(chunk.wall(7, WALL_MAX_Y, 9));

            FidelityStats stats = FidelityScanner.scan(source, 0, 0, 1, 4, 2);

            long interiorColumns = 9L * 9L;
            assertEquals(interiorColumns * (LAND_HEIGHT - 1 - ExportIrFixtures.MIN_Y), stats.wallSolidVoxels());
            assertEquals(interiorColumns * (WALL_MAX_Y - WALL_MIN_Y), stats.wallVoxels());
        }
    }

    @Test
    public void openSpaceOutsideTheTerrainOperandIsATerrainVoid() throws IOException {
        WorldgenExportSampler sampler = WorldgenExportSampler.open(carvedExport(), WorldgenExportMode.PRESET, DIMENSION_ID, SEED);
        try (VanillaNoiseFidelitySource source = new VanillaNoiseFidelitySource(sampler, Map.of(BIOME_ID, LOAD_KEY), WALL_SHELL)) {
            FidelityChunk chunk = source.sample(1, -2);

            assertEquals(CARVED_HEIGHT - 1, chunk.surfaceY(7, 9));
            assertTrue(chunk.open(7, VOID_MIN_Y, 9));
            assertFalse(chunk.terrainMass(7, VOID_MIN_Y, 9));
            assertTrue(chunk.open(7, CAVE_MIN_Y + 3, 9));
            assertTrue(chunk.terrainMass(7, CAVE_MIN_Y + 3, 9));
            assertFalse(chunk.open(7, VOID_MIN_Y - 4, 9));
            assertTrue(chunk.terrainMass(7, VOID_MIN_Y - 4, 9));

            FidelityStats stats = FidelityScanner.scan(source, 0, 0, 1, 4, 2);

            int voidBand = (VOID_MIN_Y - ExportIrFixtures.MIN_Y) / 16;
            int caveBand = (CAVE_MIN_Y - ExportIrFixtures.MIN_Y) / 16;
            assertEquals(1.0D, stats.voidShare(voidBand), 0.0D);
            assertEquals(0.0D, stats.caveShare(voidBand), 0.0D);
            assertEquals(1.0D, stats.caveShare(caveBand), 0.0D);
            assertEquals(0.0D, stats.voidShare(caveBand), 0.0D);
        }
    }

    @Test
    public void exportWithoutAWallShellReportsNoWalls() throws IOException {
        try (VanillaNoiseFidelitySource source = new VanillaNoiseFidelitySource(open(LAND_HEIGHT), Map.of(BIOME_ID, LOAD_KEY), WALL_SHELL)) {
            assertFalse(source.sample(0, 0).wallsKnown());
        }
    }

    @Test
    public void exportedBiomeWithoutALoadKeyIsRejected() throws IOException {
        try (VanillaNoiseFidelitySource source = new VanillaNoiseFidelitySource(open(LAND_HEIGHT), Map.of(), WALL_SHELL)) {
            FidelityChunk chunk = source.sample(0, 0);

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> chunk.surfaceBiome(0, 0));

            assertTrue(failure.getMessage(), failure.getMessage().contains(BIOME_ID));
        }
    }

    private static WorldgenExportSampler open(int height) throws IOException {
        return WorldgenExportSampler.open(flatExport(height), WorldgenExportMode.PRESET, DIMENSION_ID, SEED);
    }

    private static JsonResource wallShell() {
        return new JsonResource("data/" + ExportIrFixtures.NAMESPACE + "/worldgen/density_function/cave/wall_shell.json", JsonParser.parseString(
                "{\"type\":\"minecraft:range_choice\",\"input\":{\"type\":\"minecraft:gradient\",\"axis\":\"y\",\"tiling\":\"clamp_to_edge\","
                        + "\"from_coordinate\":-4064,\"to_coordinate\":4062,\"from_value\":-4064.0,\"to_value\":4062.0},"
                        + "\"min_inclusive\":" + WALL_MIN_Y + ".0,\"max_exclusive\":" + WALL_MAX_Y + ".0,\"when_in_range\":1.0,\"when_out_of_range\":0.0}"));
    }

    private static SortedMap<String, byte[]> flatExport(int height, JsonResource... resources) {
        Field level = new Field.Const(height);
        Field finalDensity = new Field.Grid(new Field.Binary(Field.BinaryOp.MUL, new Field.Const(0.125),
                new Field.Binary(Field.BinaryOp.SUB, level, BLOCK_Y)), 4, 8);
        return export(height, finalDensity, resources);
    }

    private static SortedMap<String, byte[]> carvedExport() {
        Field ramp = new Field.Binary(Field.BinaryOp.MUL, new Field.Const(0.125), new Field.Binary(Field.BinaryOp.SUB, new Field.Const(CARVED_HEIGHT), BLOCK_Y));
        Field terrain = new Field.RangeIf(BLOCK_Y, VOID_MIN_Y, VOID_MIN_Y + 16, new Field.Const(-1.0), ramp);
        Field caves = new Field.RangeIf(BLOCK_Y, CAVE_MIN_Y, CAVE_MIN_Y + 16, new Field.Const(-1.0), new Field.Const(1.0));
        return export(CARVED_HEIGHT, new Field.Binary(Field.BinaryOp.MIN, terrain, caves));
    }

    private static SortedMap<String, byte[]> export(int height, Field finalDensity, JsonResource... resources) {
        Field zero = new Field.Const(0.0);
        Field level = new Field.Const(height);
        RouterDef router = new RouterDef(zero, zero, zero, zero, zero, zero, new Field.Grid(level, 16, 1), finalDensity, AquiferDef.dry(level));
        DimensionDef dimension = new DimensionDef(ExportIrFixtures.DIMENSION, ExportIrFixtures.MIN_Y, ExportIrFixtures.HEIGHT,
                ExportIrFixtures.HEIGHT, ExportIrFixtures.FLUID_HEIGHT, "minecraft:stone", "minecraft:water", ExportIrFixtures.dimensionType());
        ClimateLayout climate = new ClimateLayout(List.of(new ClimateLayout.ClimateEntry(BIOME_ID, ClimateLayout.ClimateAxis.TEMPERATURE, -2.0, 2.0)));
        ExportIr ir = new ExportIr(ExportIrFixtures.NAMESPACE, DataVersion.V26_3, WorldgenExportMode.PRESET, dimension, List.of(), router, climate,
                List.of(new BiomeDef(ExportIrFixtures.BIOME, ExportIrFixtures.biome())), MaterialProgram.rock("minecraft:stone"),
                List.of(), List.of(resources), List.of());
        return WorldgenJsonWriter.forTarget(DataVersion.V26_3).write(ir);
    }
}
