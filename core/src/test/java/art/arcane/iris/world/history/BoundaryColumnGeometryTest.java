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
    private static final BoundaryColumnGeometry.Voxel WATER = new BoundaryColumnGeometry.Voxel(
            "minecraft:water[level=0]", BoundaryColumnGeometry.Phase.FLUID, "minecraft:water[level=0]", false);

    @Test
    public void solidAboveMatchesDenseColumnsWithProtectionAndExtremeWorldCoordinates() {
        BoundaryColumnGeometry.Voxel protectedSolid = new BoundaryColumnGeometry.Voxel(
                "minecraft:gold_block", BoundaryColumnGeometry.Phase.SOLID, "", true);
        BoundaryColumnGeometry.Voxel fluid = new BoundaryColumnGeometry.Voxel(
                "minecraft:water", BoundaryColumnGeometry.Phase.FLUID, "minecraft:water", false);
        Random random = new Random(83761L);
        for (int height : new int[]{0, 1, 2, 3, 64, 257, 4096, BoundaryColumnGeometry.MAXIMUM_HEIGHT}) {
            for (int minimumY : new int[]{Integer.MIN_VALUE, -2032, -64, 0,
                    Integer.MAX_VALUE - BoundaryColumnGeometry.MAXIMUM_HEIGHT}) {
                ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(height);
                for (int offset = 0; offset < height; offset++) {
                    voxels.add(switch (random.nextInt(8)) {
                        case 0 -> STONE;
                        case 1 -> protectedSolid;
                        case 2 -> fluid;
                        default -> AIR;
                    });
                }
                BoundaryColumnGeometry geometry = BoundaryColumnGeometry.fromVoxels(minimumY, voxels);
                for (int index = 0; index < 128; index++) {
                    long offset = index < 4 ? new long[]{-1, 0, height - 1L, height}[index]
                            : random.nextInt(Math.max(1, height + 2)) - 1L;
                    long worldY = (long) minimumY + offset;
                    if (worldY < Integer.MIN_VALUE || worldY > Integer.MAX_VALUE) {
                        continue;
                    }
                    boolean expected = false;
                    for (int above = (int) Math.max(0L, offset + 1L); above < height; above++) {
                        if (voxels.get(above).phase() == BoundaryColumnGeometry.Phase.SOLID) {
                            expected = true;
                            break;
                        }
                    }
                    assertEquals(expected, geometry.hasSolidAbove((int) worldY));
                }
                assertFalse(geometry.hasSolidAbove(Integer.MAX_VALUE));
            }
        }
        assertFalse(BoundaryColumnGeometry.empty().hasSolidAbove(Integer.MIN_VALUE));
        assertFalse(BoundaryColumnGeometry.fromVoxels(-64, List.of(AIR, fluid)).hasSolidAbove(Integer.MIN_VALUE));
        assertTrue(BoundaryColumnGeometry.fromVoxels(-64, List.of(protectedSolid)).hasSolidAbove(-65));
    }

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

        assertEquals(50, geometry.surfaceOffsetNear(50D, BoundaryColumnGeometry.WaterCap.none()));
        assertEquals(50, geometry.surfaceOffsetNear(70D, BoundaryColumnGeometry.WaterCap.none()));
    }

    @Test
    public void waterCapSpansSolidLayersFromTheWaterSurfaceDownToWaterOrTheSeabed() {
        BoundaryColumnGeometry.Voxel ice = new BoundaryColumnGeometry.Voxel(
                "minecraft:ice", BoundaryColumnGeometry.Phase.SOLID, "", false);
        BoundaryColumnGeometry.Voxel packedIce = new BoundaryColumnGeometry.Voxel(
                "minecraft:packed_ice", BoundaryColumnGeometry.Phase.SOLID, "", false);
        BoundaryColumnGeometry iced = column(STONE, STONE, STONE, STONE, WATER, WATER, WATER, WATER, WATER,
                packedIce, packedIce, ice, ice, AIR, AIR, AIR);
        BoundaryColumnGeometry.WaterCap cap = iced.waterCap(3, 12);

        assertEquals(new BoundaryColumnGeometry.WaterCap(9, 12), cap);
        assertEquals(3, iced.surfaceOffsetNear(3D, cap));
        assertTrue(iced.capsWater(cap, 3));
        assertEquals(WATER.fluidStateKey(), iced.fluidStateKeyAtOrBelow(12, 3));

        BoundaryColumnGeometry frozen = column(STONE, STONE, STONE, ice, ice, ice, ice, AIR);
        BoundaryColumnGeometry.WaterCap frozenCap = frozen.waterCap(2, 6);

        assertEquals(new BoundaryColumnGeometry.WaterCap(3, 6), frozenCap);
        assertEquals(6, frozen.surfaceOffsetNear(2D, BoundaryColumnGeometry.WaterCap.none()));
        assertEquals(2, frozen.surfaceOffsetNear(2D, frozenCap));
        assertTrue(frozen.capsWater(frozenCap, 2));
        assertEquals("", frozen.fluidStateKeyAtOrBelow(6, 2));

        BoundaryColumnGeometry frozenBed = column(STONE, ice, ice, ice, ice, AIR);
        BoundaryColumnGeometry.WaterCap bedCap = frozenBed.waterCap(1, 4);
        assertEquals(new BoundaryColumnGeometry.WaterCap(2, 4), bedCap);
        assertEquals(1, frozenBed.surfaceOffsetNear(1D, bedCap));
        assertEquals(1, frozenBed.surfaceOffsetNear(4D, bedCap));
    }

    @Test
    public void waterCapIgnoresOpenWaterLandAndSolidsThatContinueAboveTheSurface() {
        BoundaryColumnGeometry.Voxel ice = new BoundaryColumnGeometry.Voxel(
                "minecraft:ice", BoundaryColumnGeometry.Phase.SOLID, "", false);
        BoundaryColumnGeometry.Voxel waterlogged = new BoundaryColumnGeometry.Voxel(
                "minecraft:oak_slab[type=bottom,waterlogged=true]", BoundaryColumnGeometry.Phase.SOLID,
                WATER.fluidStateKey(), false);
        BoundaryColumnGeometry.Voxel protectedSolid = new BoundaryColumnGeometry.Voxel(
                "minecraft:oak_log[axis=y]", BoundaryColumnGeometry.Phase.SOLID, "", true);

        assertFalse(column(STONE, STONE, WATER, WATER, AIR).waterCap(1, 3).present());
        assertFalse(column(STONE, STONE, STONE, STONE, AIR).waterCap(3, 3).present());
        assertFalse(column(STONE, STONE, WATER, ice, ice, AIR).waterCap(1, 3).present());
        assertFalse(column(STONE, STONE, WATER, waterlogged, AIR).waterCap(1, 3).present());
        assertFalse(column(STONE, STONE, WATER, protectedSolid, AIR).waterCap(1, 3).present());
        assertFalse(column(STONE, STONE, WATER, ice).waterCap(1, 4).present());
        assertFalse(column(STONE, STONE, WATER, ice, AIR).waterCap(1, -1).present());
        assertTrue(column(STONE, STONE, WATER, ice).waterCap(1, 3).present());

        BoundaryColumnGeometry overAir = column(STONE, STONE, WATER, AIR, ice, AIR);
        BoundaryColumnGeometry.WaterCap cap = overAir.waterCap(1, 4);
        assertEquals(new BoundaryColumnGeometry.WaterCap(4, 4), cap);
        assertFalse(overAir.capsWater(cap, 1));
        assertFalse(overAir.capsWater(BoundaryColumnGeometry.WaterCap.none(), 1));
    }

    @Test
    public void nearestSurfaceMatchesDenseColumnsAcrossRunsProtectionAndTies() {
        List<BoundaryColumnGeometry.Voxel> states = List.of(STONE, AIR,
                new BoundaryColumnGeometry.Voxel("minecraft:oak_log[axis=y]",
                        BoundaryColumnGeometry.Phase.SOLID, "", true),
                new BoundaryColumnGeometry.Voxel("minecraft:water[level=0]",
                        BoundaryColumnGeometry.Phase.FLUID, "minecraft:water[level=0]", false));
        for (int combination = 0; combination < 4096; combination++) {
            int encoded = combination;
            ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(18);
            for (int run = 0; run < 6; run++) {
                BoundaryColumnGeometry.Voxel voxel = states.get(encoded & 3);
                encoded >>= 2;
                for (int offset = 0; offset <= run % 3; offset++) {
                    voxels.add(voxel);
                }
            }
            BoundaryColumnGeometry geometry = BoundaryColumnGeometry.fromVoxels(-64, voxels);
            for (BoundaryColumnGeometry.WaterCap cap : List.of(BoundaryColumnGeometry.WaterCap.none(),
                    new BoundaryColumnGeometry.WaterCap(0, 0), new BoundaryColumnGeometry.WaterCap(2, 4),
                    new BoundaryColumnGeometry.WaterCap(5, 9), new BoundaryColumnGeometry.WaterCap(11, 17))) {
                for (double expected : new double[]{-10D, 0D, 1.5D, 3D, 5.5D, 8D, 12D, 100D}) {
                    assertEquals(denseSurface(voxels, expected, cap), geometry.surfaceOffsetNear(expected, cap));
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> BoundaryColumnGeometry.empty()
                .surfaceOffsetNear(0D, BoundaryColumnGeometry.WaterCap.none()));
        BoundaryColumnGeometry solid = BoundaryColumnGeometry.fromVoxels(-64, List.of(STONE));
        assertThrows(IllegalArgumentException.class, () -> solid.surfaceOffsetNear(Double.NaN,
                BoundaryColumnGeometry.WaterCap.none()));
        assertThrows(IllegalArgumentException.class, () -> solid.surfaceOffsetNear(Double.POSITIVE_INFINITY,
                BoundaryColumnGeometry.WaterCap.none()));
    }

    private static int denseSurface(List<BoundaryColumnGeometry.Voxel> voxels, double expected,
                                    BoundaryColumnGeometry.WaterCap cap) {
        int selected = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (int index = 0; index + 1 < voxels.size(); index++) {
            if (denseTerrain(voxels, index, cap) && !denseTerrain(voxels, index + 1, cap)
                    && Math.abs(index - expected) < distance) {
                selected = index;
                distance = Math.abs(index - expected);
            }
        }
        if (selected >= 0) {
            return selected;
        }
        for (int index = voxels.size() - 1; index >= 0; index--) {
            if (denseTerrain(voxels, index, cap)) {
                return index;
            }
        }
        return 0;
    }

    private static boolean denseTerrain(List<BoundaryColumnGeometry.Voxel> voxels, int index,
                                        BoundaryColumnGeometry.WaterCap cap) {
        BoundaryColumnGeometry.Voxel voxel = voxels.get(index);
        return voxel.phase() == BoundaryColumnGeometry.Phase.SOLID && !voxel.protectedContent()
                && (index < cap.bottom() || index > cap.top());
    }

    private static BoundaryColumnGeometry column(BoundaryColumnGeometry.Voxel... voxels) {
        return BoundaryColumnGeometry.fromVoxels(-64, List.of(voxels));
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
