package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RouterCompilerTest {
    private static final Field HEIGHT = new Field.Noise("iris_export:height", 1.0, 0.0, null, null, null);
    private static final Field VOLUMETRIC = new Field.Noise("iris_export:terrain3d", 1.0, 1.0, null, null, null);
    private static final Field CAVES = new Field.Noise("iris_export:caves", 1.0, 1.0, null, null, null);
    private static final Field SURFACE_CODE = new Field.Noise("iris_export:surface_code", 1.0, 0.0, null, null, null);
    private static final Field CAVE_CODE = new Field.Noise("iris_export:cave_code", 1.0, 0.0, null, null, null);
    private static final Field CLASS_3D = new Field.Noise("iris_export:class3d", 1.0, 1.0, null, null, null);

    @Test
    public void finalDensityIsTheMinimumOfTerrainAndCaves() {
        RouterDef router = RouterCompiler.compile(HEIGHT, VOLUMETRIC, CAVES, AquiferDef.dry(HEIGHT), codes(), dimension());

        Field y = new Field.Coord(Axis.Y, Field.Tiling.CLAMP, -64, 320, -64.5, 319.5);
        Field ramp = new Field.Binary(Field.BinaryOp.MUL, new Field.Const(1.0), new Field.Binary(Field.BinaryOp.SUB, HEIGHT, y));
        Field terrain = new Field.Binary(Field.BinaryOp.ADD, ramp, new Field.Grid(VOLUMETRIC, 4, 8));
        assertEquals(new Field.Binary(Field.BinaryOp.MIN, terrain, CAVES), router.finalDensity());
    }

    @Test
    public void topSolidBlockIsTheRoundedHeight() {
        for (double height : new double[]{63.0, 63.4, 63.6, -12.2, 150.7}) {
            Field surface = new Field.Const(height);
            RouterDef router = RouterCompiler.compile(surface, new Field.Const(0.0), new Field.Const(1.0e6), AquiferDef.dry(surface), codes(), dimension());
            int top = (int) Math.round(height);

            assertTrue(height + " solid at " + top, FieldEvaluator.sample(router.finalDensity(), Map.of(), 0L, DataVersion.V26_3, 0, top, 0) > 0.0);
            assertTrue(height + " air at " + (top + 1), FieldEvaluator.sample(router.finalDensity(), Map.of(), 0L, DataVersion.V26_3, 0, top + 1, 0) <= 0.0);
        }
    }

    @Test
    public void cavesCarveFarBelowTheSurface() {
        Field surface = new Field.Const(100.0);
        RouterDef router = RouterCompiler.compile(surface, new Field.Const(0.0), new Field.Const(-0.25), AquiferDef.dry(surface), codes(), dimension());

        assertTrue(FieldEvaluator.sample(router.finalDensity(), Map.of(), 0L, DataVersion.V26_3, 0, -40, 0) <= 0.0);
    }

    @Test
    public void chunkSurfaceLevelIsHeightOnASixteenBlockGrid() {
        RouterDef router = RouterCompiler.compile(HEIGHT, VOLUMETRIC, CAVES, AquiferDef.dry(HEIGHT), codes(), dimension());

        assertEquals(new Field.Grid(HEIGHT, 16, 1), router.chunkSurfaceLevel());
    }

    @Test
    public void biomeCodesDriveTheClimateAxesAndTheAquiferPassesThrough() {
        AquiferDef aquifer = AquiferDef.dry(HEIGHT);
        RouterDef router = RouterCompiler.compile(HEIGHT, VOLUMETRIC, CAVES, aquifer, codes(), dimension());

        assertSame(SURFACE_CODE, router.temperature());
        assertSame(CAVE_CODE, router.vegetation());
        assertSame(CLASS_3D, router.depth());
        assertEquals(new Field.Const(0.0), router.continents());
        assertEquals(new Field.Const(0.0), router.erosion());
        assertEquals(new Field.Const(0.0), router.ridges());
        assertSame(aquifer, router.aquifer());
    }

    @Test
    public void heightMustBeTwoDimensional() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RouterCompiler.compile(VOLUMETRIC, VOLUMETRIC, CAVES, AquiferDef.dry(HEIGHT), codes(), dimension()));
        assertEquals("2D field 'height' depends on Y", failure.getMessage());
    }

    private static BiomeCodes codes() {
        ClimateLayout layout = new ClimateLayout(List.of(
                new ClimateLayout.ClimateEntry("iris_export:plains", ClimateLayout.ClimateAxis.TEMPERATURE, -1.0, 1.0)));
        return new BiomeCodes(SURFACE_CODE, CAVE_CODE, CLASS_3D, layout, Map.of("iris_export:plains", "plains"));
    }

    private static DimensionDef dimension() {
        return DimensionDef.aligned("overworld", -64, 384, 384, 63, "minecraft:stone", "minecraft:water", new JsonObject(), new ArrayList<>());
    }
}
