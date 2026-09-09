package art.arcane.iris.engine.history;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TransitionGeometryCursorTest {
    private static final BoundaryColumnGeometry.Voxel AIR = voxel("air", BoundaryColumnGeometry.Phase.AIR, false);
    private static final BoundaryColumnGeometry.Voxel STONE = voxel("stone", BoundaryColumnGeometry.Phase.SOLID, false);
    private static final BoundaryColumnGeometry.Voxel DIRT = voxel("dirt", BoundaryColumnGeometry.Phase.SOLID, false);
    private static final BoundaryColumnGeometry.Voxel WATER = voxel("water[level=0]", BoundaryColumnGeometry.Phase.FLUID, false);
    private static final List<BoundaryColumnGeometry.Voxel> STATES = List.of(AIR, STONE, DIRT, WATER,
            voxel("cave_air", BoundaryColumnGeometry.Phase.AIR, false),
            voxel("water[level=7]", BoundaryColumnGeometry.Phase.FLUID, false),
            voxel("lava[level=0]", BoundaryColumnGeometry.Phase.FLUID, false),
            voxel("stone", BoundaryColumnGeometry.Phase.SOLID, true),
            voxel("water[level=0]", BoundaryColumnGeometry.Phase.FLUID, true),
            voxel("light", BoundaryColumnGeometry.Phase.AIR, true));

    @Test
    public void matchesDenseBlendForRandomMaterialsProtectionWeightsAndRunLengths() {
        Random random = new Random(0x72AB490EL);
        int[] heights = {1, 2, 63, 64, 65, 128, 768, 4096};
        for (int sample = 0; sample < 320; sample++) {
            int height = heights[sample % heights.length];
            int minimumY = switch (sample % 3) {
                case 0 -> Integer.MIN_VALUE;
                case 1 -> Integer.MAX_VALUE - height + 1;
                default -> -256;
            };
            BoundaryColumnGeometry current = randomGeometry(random, minimumY, height, sample % 2 == 0);
            ArrayList<BoundaryColumnGeometry> historical = new ArrayList<>();
            for (int index = 0; index < 1 + sample % 4; index++) {
                historical.add(randomGeometry(random, minimumY, height, sample % 2 == 0));
            }
            assertMatches(current, historical, random.nextDouble(), random.nextDouble(), random);
        }
    }

    @Test
    public void matchesUniformColumnsAtBothHeightLimitsAndCoordinateLimits() {
        Random random = new Random(44);
        for (int height : new int[]{1, BoundaryColumnGeometry.MAXIMUM_HEIGHT}) {
            for (BoundaryColumnGeometry.Voxel current : STATES) {
                ArrayList<BoundaryColumnGeometry> historical = new ArrayList<>();
                for (BoundaryColumnGeometry.Voxel old : List.of(AIR, STONE, WATER)) {
                    historical.add(geometry(Integer.MAX_VALUE - height + 1, Collections.nCopies(height, old)));
                }
                assertMatches(geometry(Integer.MAX_VALUE - height + 1, Collections.nCopies(height, current)),
                        historical, 0.5D, 0.125D, random);
            }
        }
    }

    @Test
    public void matchesOpeningCutoffAndProtectedVoxelsInsideOpenings() {
        Random random = new Random(1234);
        for (int opening : new int[]{1, 63, 64, 65, 66}) {
            for (boolean protectedMiddle : new boolean[]{false, true}) {
                ArrayList<BoundaryColumnGeometry.Voxel> old = new ArrayList<>();
                old.add(STONE);
                old.addAll(Collections.nCopies(opening, AIR));
                old.add(DIRT);
                if (protectedMiddle) {
                    old.set(opening / 2 + 1, STATES.get(7));
                }
                BoundaryColumnGeometry current = geometry(-256, Collections.nCopies(old.size(), STONE));
                for (double weight : new double[]{0D, 0.25D, 0.5D, 0.75D, 1D}) {
                    assertMatches(current, List.of(geometry(-256, old)), weight, 0D, random);
                }
            }
        }
    }

    @Test
    public void keepsLowerMaterialOnEquidistantTiesAndPreservesSingleVoxelPhases() {
        Random random = new Random(77);
        BoundaryColumnGeometry current = geometry(0, List.of(STONE, STONE, STONE, STONE, STONE));
        BoundaryColumnGeometry old = geometry(0, List.of(STONE, AIR, AIR, AIR, DIRT));
        BoundaryGeometryInfluence influence = influence(List.of(old), 0.25D, 1D);

        assertEquals(STONE, TransitionGeometryBlender.blendColumn(influence, 0, 0, current).voxelAt(2));
        assertMatches(current, List.of(old), 0.25D, 1D, random);
        for (BoundaryColumnGeometry.Voxel middle : STATES) {
            assertMatches(geometry(0, List.of(AIR, WATER, STONE, WATER, AIR)),
                    List.of(geometry(0, List.of(STONE, AIR, middle, AIR, DIRT))), 0.5D, 0.125D, random);
        }
    }

    @Test
    public void retainsIdentityForUnchangedAndEmptyColumns() {
        BoundaryColumnGeometry empty = BoundaryColumnGeometry.empty();
        assertSame(empty, TransitionGeometryBlender.blendColumn(BoundaryGeometryInfluence.none(), 0, 0, empty));
        BoundaryColumnGeometry current = geometry(0, List.of(STONE, AIR, WATER));
        assertSame(current, TransitionGeometryBlender.blendColumn(influence(List.of(current), 0.5D, 0.5D),
                0, 0, current));
    }

    private static BoundaryColumnGeometry randomGeometry(Random random, int minimumY, int height, boolean singleVoxelRuns) {
        ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(height);
        while (voxels.size() < height) {
            BoundaryColumnGeometry.Voxel voxel = STATES.get(random.nextInt(STATES.size()));
            int end = Math.min(height, voxels.size() + (singleVoxelRuns ? 1 : random.nextInt(128) + 1));
            while (voxels.size() < end) {
                voxels.add(voxel);
            }
        }
        return geometry(minimumY, voxels);
    }

    private static void assertMatches(BoundaryColumnGeometry current, List<BoundaryColumnGeometry> historical,
                                      double currentWeight, double openingWeight, Random random) {
        BoundaryGeometryInfluence influence = influence(historical, currentWeight, openingWeight);
        int x = random.nextInt();
        int z = random.nextInt();
        BoundaryColumnGeometry expected = DenseTransitionGeometryReference.blendColumn(influence, x, z, current);
        BoundaryColumnGeometry actual = TransitionGeometryBlender.blendColumn(influence, x, z, current);
        assertEquals(expected, actual);
        if (expected == current) {
            assertSame(current, actual);
        }
    }

    private static BoundaryGeometryInfluence influence(List<BoundaryColumnGeometry> historical,
                                                      double currentWeight, double openingWeight) {
        ArrayList<BoundaryGeometryInfluence.Contribution> contributions = new ArrayList<>();
        double total = historical.size() * (historical.size() + 1) / 2D;
        for (int index = 0; index < historical.size(); index++) {
            contributions.add(new BoundaryGeometryInfluence.Contribution(historical.get(index), (index + 1) / total));
        }
        return new BoundaryGeometryInfluence(currentWeight, openingWeight, contributions);
    }

    private static BoundaryColumnGeometry geometry(int minimumY, List<BoundaryColumnGeometry.Voxel> voxels) {
        return BoundaryColumnGeometry.fromVoxels(minimumY, voxels);
    }

    private static BoundaryColumnGeometry.Voxel voxel(String key, BoundaryColumnGeometry.Phase phase, boolean protection) {
        return new BoundaryColumnGeometry.Voxel("minecraft:" + key, phase,
                phase == BoundaryColumnGeometry.Phase.FLUID ? "minecraft:" + key : "", protection);
    }
}
