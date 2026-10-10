package art.arcane.iris.engine.framework.structure.fit;

import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureYBand;
import art.arcane.iris.engine.framework.structure.NativeStructureFit;
import com.google.gson.Gson;
import com.volmit.mantle.BlockBox;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.PlatformStart;
import com.volmit.vanilla.StartReach;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the vertical shift {@code iris:structure} asks for a platform start under every adjustment a dimension can
 * give it, and how far each terrain fit reaches. The host half (alignment, clamping, moving) is pinned on the platform.
 */
public class StartLayoutsTest {
    private static final Gson JSON = new Gson();
    private static final int BOTTOM_Y = -64;
    private static final int PYRAMID_WIDTH = 21;
    private static final int PYRAMID_HEIGHT = 15;
    private static final IntBinaryOperator SURFACE = (x, z) -> 62 + Math.floorMod(x * 7 + z * 3, 11) - 5;
    private static final int[][] CHUNKS = {{0, 0}, {3, -5}, {-11, 7}};

    private static final List<String> PINNED_SHIFTS = List.of(
            "none minecraft:stronghold 0,0 shift 0",
            "none minecraft:desert_pyramid 0,0 shift 0",
            "none minecraft:stronghold 3,-5 shift 0",
            "none minecraft:desert_pyramid 3,-5 shift 0",
            "none minecraft:stronghold -11,7 shift 0",
            "none minecraft:desert_pyramid -11,7 shift 0",
            "shift minecraft:stronghold 0,0 shift 5",
            "shift minecraft:desert_pyramid 0,0 shift 5",
            "shift minecraft:stronghold 3,-5 shift 5",
            "shift minecraft:desert_pyramid 3,-5 shift 5",
            "shift minecraft:stronghold -11,7 shift 5",
            "shift minecraft:desert_pyramid -11,7 shift 5",
            "burial minecraft:stronghold 0,0 shift -8",
            "burial minecraft:desert_pyramid 0,0 shift -7",
            "burial minecraft:stronghold 3,-5 shift -8",
            "burial minecraft:desert_pyramid 3,-5 shift -7",
            "burial minecraft:stronghold -11,7 shift -8",
            "burial minecraft:desert_pyramid -11,7 shift -7",
            "burial-unshifted minecraft:stronghold 0,0 shift -8",
            "burial-unshifted minecraft:desert_pyramid 0,0 shift 0",
            "burial-unshifted minecraft:stronghold 3,-5 shift -8",
            "burial-unshifted minecraft:desert_pyramid 3,-5 shift 0",
            "burial-unshifted minecraft:stronghold -11,7 shift -8",
            "burial-unshifted minecraft:desert_pyramid -11,7 shift 0",
            "kept minecraft:stronghold 0,0 shift 3",
            "kept minecraft:desert_pyramid 0,0 shift 3",
            "kept minecraft:stronghold 3,-5 shift 3",
            "kept minecraft:desert_pyramid 3,-5 shift 3",
            "kept minecraft:stronghold -11,7 shift 3",
            "kept minecraft:desert_pyramid -11,7 shift 3",
            "band minecraft:stronghold 0,0 shift -94",
            "band minecraft:desert_pyramid 0,0 shift 0",
            "band minecraft:stronghold 3,-5 shift -58",
            "band minecraft:desert_pyramid 3,-5 shift 0",
            "band minecraft:stronghold -11,7 shift -96",
            "band minecraft:desert_pyramid -11,7 shift 0",
            "narrow-band minecraft:stronghold 0,0 shift -46",
            "narrow-band minecraft:desert_pyramid 0,0 shift 4",
            "narrow-band minecraft:stronghold 3,-5 shift -46",
            "narrow-band minecraft:desert_pyramid 3,-5 shift 4",
            "narrow-band minecraft:stronghold -11,7 shift -46",
            "narrow-band minecraft:desert_pyramid -11,7 shift 4",
            "kept-over-band minecraft:stronghold 0,0 shift 2",
            "kept-over-band minecraft:desert_pyramid 0,0 shift 2",
            "kept-over-band minecraft:stronghold 3,-5 shift 2",
            "kept-over-band minecraft:desert_pyramid 3,-5 shift 2",
            "kept-over-band minecraft:stronghold -11,7 shift 2",
            "kept-over-band minecraft:desert_pyramid -11,7 shift 2"
    );

    @Test
    public void platformStartsShiftWhereTheirAdjustmentSays() {
        List<String> actual = new ArrayList<>();
        for (Adjustment adjustment : adjustments()) {
            for (int[] chunk : CHUNKS) {
                for (String structureId : List.of("minecraft:stronghold", "minecraft:desert_pyramid")) {
                    boolean aligned = structureId.equals("minecraft:desert_pyramid");
                    int minY = aligned ? 64 : 50;
                    BlockBox bounds = new BlockBox(chunk[0] * 16, minY - BOTTOM_Y, chunk[1] * 16,
                            chunk[0] * 16 + PYRAMID_WIDTH, minY - BOTTOM_Y + PYRAMID_HEIGHT, chunk[1] * 16 + PYRAMID_WIDTH);
                    PlatformStart start = start(structureId, chunk, bounds, adjustment.underground(), aligned, SURFACE);
                    actual.add(adjustment.name() + " " + structureId + " " + chunk[0] + "," + chunk[1]
                            + " shift " + StartLayouts.shift(start, adjustment.fit()));
                }
            }
        }
        assertEquals(PINNED_SHIFTS, actual);
    }

    @Test
    public void burialKeepsTheTopABlockUnderTheLowestTerrainColumn() {
        BlockBox bounds = new BlockBox(0, 60 - BOTTOM_Y, 0, 2, 81 - BOTTOM_Y, 1);
        PlatformStart start = start("minecraft:stronghold", new int[]{0, 0}, bounds, true, false,
                (x, z) -> x == 1 ? 76 : 100);

        assertEquals(-5, StartLayouts.shift(start, NativeStructureFit.NONE));
    }

    @Test
    public void startsReachAsFarAsTheirTerrainFitChangesTerrain() {
        assertEquals(StartReach.PLATFORM, StartLayouts.reach(terrain("{}")));
        assertEquals(StartReach.blocks(0), StartLayouts.reach(terrain("{\"mode\":\"PRESERVE\"}")));
        assertEquals(StartReach.blocks(12), StartLayouts.reach(terrain("{\"mode\":\"VACUUM\"}")));
        assertEquals(StartReach.blocks(4), StartLayouts.reach(terrain("{\"mode\":\"ENCASE\",\"horizontalPadding\":4}")));
        assertEquals(StartReach.blocks(20), StartLayouts.reach(terrain("{\"mode\":\"BORE\",\"horizontalPadding\":20}")));
        assertEquals(StartReach.blocks(112),
                StartLayouts.reach(terrain("{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":112}")));
        assertEquals(StartReach.blocks(0), StartLayouts.reach(terrain("{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":0}")));
    }

    @Test
    public void aBandMovesTheMiddleIntoItTheSameWayForTheSameChunk() {
        NativeStructureFit fit = fit(-64, "{\"min\":-120,\"max\":-20}", false);
        PlatformStart first = pyramidAt(64, false);
        PlatformStart second = pyramidAt(64, false);

        int shift = StartLayouts.shift(first, fit);

        assertEquals(shift, StartLayouts.shift(second, fit));
        int middle = 64 + PYRAMID_HEIGHT / 2 + shift;
        assertTrue(middle >= -120 && middle <= -20);
    }

    @Test
    public void aBandTooNarrowForTheStructureTakesItsMiddle() {
        PlatformStart start = pyramidAt(64, false);

        int shift = StartLayouts.shift(start, fit(0, "{\"min\":-50,\"max\":-45}", false));

        assertEquals(Math.floorDiv(-50 + -45, 2), 64 + (PYRAMID_HEIGHT - 1) / 2 + shift);
    }

    @Test
    public void aBandReplacesBothTheShiftAndTheBurial() {
        PlatformStart start = pyramidAt(64, true);

        int shift = StartLayouts.shift(start, fit(-200, "{\"min\":-64,\"max\":-64}", false));

        assertEquals(-64 - (PYRAMID_HEIGHT - 1) / 2, 64 + shift);
    }

    private static StructureTerrain terrain(String json) {
        return JSON.fromJson(json, StructureTerrain.class);
    }

    private static List<Adjustment> adjustments() {
        return List.of(
                new Adjustment("none", fit(0, null, false), false),
                new Adjustment("shift", fit(5, null, false), false),
                new Adjustment("burial", fit(-7, null, false), true),
                new Adjustment("burial-unshifted", fit(0, null, false), true),
                new Adjustment("kept", fit(3, null, true), true),
                new Adjustment("band", fit(0, "{\"min\":-50,\"max\":20}", false), true),
                new Adjustment("narrow-band", fit(4, "{\"min\":10,\"max\":12}", false), false),
                new Adjustment("kept-over-band", fit(2, "{\"min\":-40,\"max\":80}", true), false));
    }

    private static NativeStructureFit fit(int yShift, String bandJson, boolean keepsSourceY) {
        StructureYBand band = bandJson == null ? null : JSON.fromJson(bandJson, StructureYBand.class);
        return new NativeStructureFit(yShift, band, keepsSourceY, null, null);
    }

    private static PlatformStart pyramidAt(int minY, boolean underground) {
        BlockBox bounds = new BlockBox(0, minY - BOTTOM_Y, 0, PYRAMID_WIDTH, minY - BOTTOM_Y + PYRAMID_HEIGHT, PYRAMID_WIDTH);
        return start("minecraft:stronghold", new int[]{0, 0}, bounds, underground, false, (x, z) -> 40);
    }

    private static PlatformStart start(String structureId, int[] chunk, BlockBox bounds, boolean underground,
                                       boolean aligned, IntBinaryOperator worldSurface) {
        return new PlatformStart() {
            @Override
            public ResourceId structure() {
                return ResourceId.parse(structureId);
            }

            @Override
            public int chunkX() {
                return chunk[0];
            }

            @Override
            public int chunkZ() {
                return chunk[1];
            }

            @Override
            public BlockBox bounds() {
                return bounds;
            }

            @Override
            public boolean underground() {
                return underground;
            }

            @Override
            public boolean aligned() {
                return aligned;
            }

            @Override
            public int bottomY() {
                return BOTTOM_Y;
            }

            @Override
            public int surfaceY(int x, int z) {
                return worldSurface.applyAsInt(x, z) - BOTTOM_Y;
            }
        };
    }

    private record Adjustment(String name, NativeStructureFit fit, boolean underground) {
    }
}
