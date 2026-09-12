package art.arcane.iris.world.history;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BoundaryColumnGeometryTest {
    private static final BoundaryColumnGeometry.Voxel STONE = new BoundaryColumnGeometry.Voxel(
            "minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);
    private static final BoundaryColumnGeometry.Voxel AIR = new BoundaryColumnGeometry.Voxel(
            "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);

    @Test
    public void preservesEveryVerticalRunIncludingCavesAndIslands() {
        BoundaryColumnGeometry geometry = BoundaryColumnGeometry.fromVoxels(-4,
                List.of(STONE, STONE, AIR, AIR, STONE, AIR));

        assertArrayEquals(new int[]{2, 4, 5, 6}, geometry.runEnds());
        assertEquals(STONE, geometry.voxelAt(-4));
        assertEquals(AIR, geometry.voxelAt(-2));
        assertEquals(STONE, geometry.voxelAt(0));
        assertEquals(AIR, geometry.voxelAt(1));
        assertEquals(AIR, geometry.voxelAt(Integer.MAX_VALUE));
        assertArrayEquals(new double[]{1.5D, 0.5D, -0.5D, -0.5D, 0.5D, -0.5D},
                geometry.solidDistances(), 0D);
    }

    @Test
    public void groundReferenceSelectsSurfaceBelowUpperRoofAndAboveCaves() {
        ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(384);
        for (int offset = 0; offset < 384; offset++) {
            boolean solid = offset <= 50 && (offset < 10 || offset > 20) || offset >= 300;
            voxels.add(solid ? STONE : AIR);
        }
        BoundaryColumnGeometry geometry = BoundaryColumnGeometry.fromVoxels(-64, voxels);

        assertEquals(50, geometry.surfaceOffsetNear(50D));
        assertEquals(50, geometry.surfaceOffsetNear(70D));
    }

    @Test
    public void enclosedOpenQueriesIncludeUpperCavesAndExcludeOpenSky() {
        BoundaryColumnGeometry geometry = BoundaryColumnGeometry.fromVoxels(-4,
                List.of(STONE, AIR, STONE, AIR, STONE, AIR));

        assertTrue(geometry.isEnclosedOpenAt(-3));
        assertTrue(geometry.isEnclosedOpenAt(-1));
        assertFalse(geometry.isEnclosedOpenAt(1));
        assertFalse(geometry.isEnclosedOpenAt(-4));
        assertFalse(geometry.isEnclosedOpenAt(-5));
    }

    @Test
    public void preservesFluidAndProtectionWithoutRegistryObjects() {
        BoundaryColumnGeometry.Voxel waterlogged = new BoundaryColumnGeometry.Voxel(
                "minecraft:oak_slab[type=bottom,waterlogged=true]", BoundaryColumnGeometry.Phase.SOLID,
                "minecraft:water[level=0]", true);
        BoundaryColumnGeometry geometry = BoundaryColumnGeometry.fromVoxels(3, List.of(waterlogged));

        assertEquals(waterlogged, geometry.voxelAt(3));
        assertTrue(geometry.solidDistances()[0] < 0D);
        assertThrows(IllegalArgumentException.class, () -> new BoundaryColumnGeometry.Voxel(
                "minecraft:water", BoundaryColumnGeometry.Phase.FLUID, "", false));
    }

    @Test
    public void repeatedVoxelOptimizationPreservesCanonicalPaletteAndRuns() {
        List<BoundaryColumnGeometry.Voxel> states = List.of(STONE, AIR,
                new BoundaryColumnGeometry.Voxel("minecraft:cave_air", BoundaryColumnGeometry.Phase.AIR, "", false),
                new BoundaryColumnGeometry.Voxel("minecraft:water[level=0]", BoundaryColumnGeometry.Phase.FLUID,
                        "minecraft:water[level=0]", false),
                new BoundaryColumnGeometry.Voxel("minecraft:water[level=3]", BoundaryColumnGeometry.Phase.FLUID,
                        "minecraft:water[level=3]", false),
                new BoundaryColumnGeometry.Voxel("minecraft:lava[level=0]", BoundaryColumnGeometry.Phase.FLUID,
                        "minecraft:lava[level=0]", false),
                new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", true),
                new BoundaryColumnGeometry.Voxel("minecraft:oak_slab[type=bottom,waterlogged=true]",
                        BoundaryColumnGeometry.Phase.SOLID, "minecraft:water[level=0]", true));
        assertCanonicalEncoding(List.of());
        for (BoundaryColumnGeometry.Voxel state : states) {
            assertCanonicalEncoding(Collections.nCopies(768, state));
        }
        Random random = new Random(42069);
        for (int sample = 0; sample < 64; sample++) {
            ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(768);
            while (voxels.size() < 768) {
                BoundaryColumnGeometry.Voxel state = states.get(random.nextInt(states.size()));
                int end = Math.min(768, voxels.size() + (sample % 2 == 0 ? 1 : 1 + random.nextInt(96)));
                while (voxels.size() < end) {
                    voxels.add(new BoundaryColumnGeometry.Voxel(state.stateKey(), state.phase(),
                            state.fluidStateKey(), state.protectedContent()));
                }
            }
            assertCanonicalEncoding(voxels);
        }
    }

    @Test
    public void voxelFactoryKeepsNullHeightAndCoordinateValidation() {
        assertThrows(NullPointerException.class, () -> BoundaryColumnGeometry.fromVoxels(0, null));
        assertThrows(NullPointerException.class, () -> BoundaryColumnGeometry.fromVoxels(0,
                Arrays.asList(STONE, STONE, null)));
        assertThrows(IllegalArgumentException.class, () -> BoundaryColumnGeometry.fromVoxels(0,
                Collections.nCopies(BoundaryColumnGeometry.MAXIMUM_HEIGHT + 1, STONE)));
        assertThrows(ArithmeticException.class, () -> BoundaryColumnGeometry.fromVoxels(Integer.MAX_VALUE,
                List.of(STONE, STONE)));
    }

    private static void assertCanonicalEncoding(List<BoundaryColumnGeometry.Voxel> voxels) {
        TreeSet<BoundaryColumnGeometry.Voxel> used = new TreeSet<>(Comparator
                .comparing(BoundaryColumnGeometry.Voxel::stateKey)
                .thenComparing(BoundaryColumnGeometry.Voxel::phase)
                .thenComparing(BoundaryColumnGeometry.Voxel::fluidStateKey)
                .thenComparing(BoundaryColumnGeometry.Voxel::protectedContent));
        used.addAll(voxels);
        List<BoundaryColumnGeometry.Voxel> palette = List.copyOf(used);
        Map<BoundaryColumnGeometry.Voxel, Short> indices = new HashMap<>();
        for (int index = 0; index < palette.size(); index++) {
            indices.put(palette.get(index), (short) index);
        }
        int[] ends = new int[voxels.size()];
        short[] values = new short[voxels.size()];
        int runs = 0;
        for (int offset = 0; offset < voxels.size(); offset++) {
            short value = indices.get(voxels.get(offset));
            if (runs == 0 || values[runs - 1] != value) {
                values[runs++] = value;
            }
            ends[runs - 1] = offset + 1;
        }
        BoundaryColumnGeometry actual = BoundaryColumnGeometry.fromVoxels(-256, voxels);
        assertEquals(palette, actual.palette());
        assertArrayEquals(Arrays.copyOf(ends, runs), actual.runEnds());
        assertArrayEquals(Arrays.copyOf(values, runs), actual.paletteIndices());
        assertEquals(voxels, actual.voxels());
    }

    @Test
    public void validatesAndOwnsCompactArrays() {
        int[] ends = new int[]{2};
        short[] indices = new short[]{0};
        BoundaryColumnGeometry geometry = new BoundaryColumnGeometry(0, List.of(STONE), ends, indices);
        ends[0] = 1;
        indices[0] = 42;
        geometry.runEnds()[0] = 19;

        assertEquals(2, geometry.height());
        assertEquals(STONE, geometry.voxelAt(1));
        assertThrows(IllegalArgumentException.class, () -> new BoundaryColumnGeometry(0,
                List.of(STONE), new int[]{2, 1}, new short[]{0, 0}));
        assertThrows(ArithmeticException.class, () -> new BoundaryColumnGeometry(Integer.MAX_VALUE,
                List.of(STONE), new int[]{2}, new short[]{0}));
    }
}
