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
import art.arcane.iris.pack.datapack.export.ir.MaterialProgram;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;
import art.arcane.iris.probe.PaperRegistryIsolation;
import art.arcane.iris.probe.WorldgenExportSampler;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class VanillaNoiseColumnSourceTest {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final long SEED = 1337L;
    private static final String DIMENSION_ID = ExportIrFixtures.NAMESPACE + ":" + ExportIrFixtures.DIMENSION;
    private static final String BIOME_ID = ExportIrFixtures.NAMESPACE + ":" + ExportIrFixtures.BIOME;
    private static final String LOAD_KEY = "Flat Plains";
    private static final int LAND_HEIGHT = 80;
    private static final int SEA_FLOOR_HEIGHT = 40;
    private static final int MAX_Y = ExportIrFixtures.MIN_Y + ExportIrFixtures.HEIGHT;

    @Test
    public void landColumnsReportTheTopSolidBlockAndStayDry() throws IOException {
        try (VanillaNoiseColumnSource source = new VanillaNoiseColumnSource(open(LAND_HEIGHT), Map.of(BIOME_ID, LOAD_KEY))) {
            assertEquals(ExportIrFixtures.MIN_Y, source.minY());
            assertEquals(MAX_Y, source.maxY());
            for (int x : new int[]{-37, 0, 113}) {
                FidelityColumn column = source.column(x, -x);
                assertEquals(new FidelityColumn(LOAD_KEY, LAND_HEIGHT - 1, false), column);
            }
        }
    }

    @Test
    public void seaColumnsReportTheFloorUnderWater() throws IOException {
        WorldgenExportSampler sampler = open(SEA_FLOOR_HEIGHT);
        try (VanillaNoiseColumnSource source = new VanillaNoiseColumnSource(sampler, Map.of(BIOME_ID, LOAD_KEY))) {
            FidelityStats stats = new FidelityStats(source.minY(), source.maxY());
            for (int x : new int[]{-37, 0, 113}) {
                FidelityColumn column = source.column(x, x);
                assertEquals(new FidelityColumn(LOAD_KEY, sampler.floorHeight(x, x) - 1, true), column);
                assertTrue(column.terrainY() + " under sea level", column.terrainY() < ExportIrFixtures.SEA_LEVEL - 1);
                assertEquals(ExportIrFixtures.SEA_LEVEL, sampler.baseHeight(x, x));
                stats.accept(column);
            }

            assertEquals(0.0D, stats.landFraction(), 0.0D);
        }
    }

    @Test
    public void exportedBiomeWithoutALoadKeyIsRejected() throws IOException {
        try (VanillaNoiseColumnSource source = new VanillaNoiseColumnSource(open(LAND_HEIGHT), Map.of())) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> source.column(0, 0));

            assertTrue(failure.getMessage(), failure.getMessage().contains(BIOME_ID));
        }
    }

    private static WorldgenExportSampler open(int height) throws IOException {
        return WorldgenExportSampler.open(flatExport(height), WorldgenExportMode.PRESET, DIMENSION_ID, SEED);
    }

    private static SortedMap<String, byte[]> flatExport(int height) {
        Field zero = new Field.Const(0.0);
        Field level = new Field.Const(height);
        Field y = new Field.Coord(Axis.Y, Field.Tiling.CLAMP, -4064.0, 4062.0, -4064.0, 4062.0);
        Field finalDensity = new Field.Grid(new Field.Binary(Field.BinaryOp.MUL, new Field.Const(0.125),
                new Field.Binary(Field.BinaryOp.SUB, level, y)), 4, 8);
        RouterDef router = new RouterDef(zero, zero, zero, zero, zero, zero, new Field.Grid(level, 16, 1), finalDensity, AquiferDef.dry(level));
        DimensionDef dimension = new DimensionDef(ExportIrFixtures.DIMENSION, ExportIrFixtures.MIN_Y, ExportIrFixtures.HEIGHT,
                ExportIrFixtures.HEIGHT, ExportIrFixtures.FLUID_HEIGHT, "minecraft:stone", "minecraft:water", ExportIrFixtures.dimensionType());
        ClimateLayout climate = new ClimateLayout(List.of(new ClimateLayout.ClimateEntry(BIOME_ID, ClimateLayout.ClimateAxis.TEMPERATURE, -2.0, 2.0)));
        ExportIr ir = new ExportIr(ExportIrFixtures.NAMESPACE, DataVersion.V26_3, WorldgenExportMode.PRESET, dimension, List.of(), router, climate,
                List.of(new BiomeDef(ExportIrFixtures.BIOME, ExportIrFixtures.biome())), MaterialProgram.rock("minecraft:stone"),
                List.of(), List.of(), List.of());
        return WorldgenJsonWriter.forTarget(DataVersion.V26_3).write(ir);
    }
}
