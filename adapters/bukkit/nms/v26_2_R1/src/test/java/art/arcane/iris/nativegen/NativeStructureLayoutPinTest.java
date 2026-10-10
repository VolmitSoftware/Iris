package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.NativeStructureReach;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;

/**
 * Pins where the platform's own starts end up vertically for each adjustment the dimension can give them (shift,
 * band, keeping the platform's height, burial under the terrain), and which chunks a start reaches for each terrain
 * fit. The values were taken before the fit moved into the structure module; since then the module answers the shift
 * and the reach ({@code StartLayoutsPinTest} in core pins those answers for the same starts) and the platform side,
 * pinned here, aligns, moves and references the starts with them.
 */
public class NativeStructureLayoutPinTest {
    private static final int SEA_LEVEL = 61;
    private static final int WORLD_MIN_Y = -64;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;
    private static final IntBinaryOperator SURFACE = (x, z) -> 62 + Math.floorMod(x * 7 + z * 3, 11) - 5;
    private static final int[][] CHUNKS = {{0, 0}, {3, -5}, {-11, 7}};
    private static final Map<String, Integer> ADJUSTMENT_SHIFTS = adjustmentShifts();
    private static final Map<String, NativeStructureReach> TERRAIN_REACHES = terrainReaches();

    private static final List<String> PINNED_SHIFTS = List.of(
            "none minecraft:stronghold 0,0 offset 0 minY 50",
            "none minecraft:desert_pyramid 0,0 offset -6 minY 58",
            "none minecraft:stronghold 3,-5 offset 0 minY 50",
            "none minecraft:desert_pyramid 3,-5 offset -6 minY 58",
            "none minecraft:stronghold -11,7 offset 0 minY 50",
            "none minecraft:desert_pyramid -11,7 offset -6 minY 58",
            "shift minecraft:stronghold 0,0 offset 5 minY 55",
            "shift minecraft:desert_pyramid 0,0 offset -1 minY 63",
            "shift minecraft:stronghold 3,-5 offset 5 minY 55",
            "shift minecraft:desert_pyramid 3,-5 offset -1 minY 63",
            "shift minecraft:stronghold -11,7 offset 5 minY 55",
            "shift minecraft:desert_pyramid -11,7 offset -1 minY 63",
            "burial minecraft:stronghold 0,0 offset -8 minY 42",
            "burial minecraft:desert_pyramid 0,0 offset -13 minY 51",
            "burial minecraft:stronghold 3,-5 offset -8 minY 42",
            "burial minecraft:desert_pyramid 3,-5 offset -13 minY 51",
            "burial minecraft:stronghold -11,7 offset -8 minY 42",
            "burial minecraft:desert_pyramid -11,7 offset -13 minY 51",
            "burial-unshifted minecraft:stronghold 0,0 offset -8 minY 42",
            "burial-unshifted minecraft:desert_pyramid 0,0 offset -6 minY 58",
            "burial-unshifted minecraft:stronghold 3,-5 offset -8 minY 42",
            "burial-unshifted minecraft:desert_pyramid 3,-5 offset -6 minY 58",
            "burial-unshifted minecraft:stronghold -11,7 offset -8 minY 42",
            "burial-unshifted minecraft:desert_pyramid -11,7 offset -6 minY 58",
            "kept minecraft:stronghold 0,0 offset 3 minY 53",
            "kept minecraft:desert_pyramid 0,0 offset -3 minY 61",
            "kept minecraft:stronghold 3,-5 offset 3 minY 53",
            "kept minecraft:desert_pyramid 3,-5 offset -3 minY 61",
            "kept minecraft:stronghold -11,7 offset 3 minY 53",
            "kept minecraft:desert_pyramid -11,7 offset -3 minY 61",
            "band minecraft:stronghold 0,0 offset -94 minY -44",
            "band minecraft:desert_pyramid 0,0 offset -6 minY 58",
            "band minecraft:stronghold 3,-5 offset -58 minY -8",
            "band minecraft:desert_pyramid 3,-5 offset -6 minY 58",
            "band minecraft:stronghold -11,7 offset -96 minY -46",
            "band minecraft:desert_pyramid -11,7 offset -6 minY 58",
            "narrow-band minecraft:stronghold 0,0 offset -46 minY 4",
            "narrow-band minecraft:desert_pyramid 0,0 offset -2 minY 62",
            "narrow-band minecraft:stronghold 3,-5 offset -46 minY 4",
            "narrow-band minecraft:desert_pyramid 3,-5 offset -2 minY 62",
            "narrow-band minecraft:stronghold -11,7 offset -46 minY 4",
            "narrow-band minecraft:desert_pyramid -11,7 offset -2 minY 62",
            "kept-over-band minecraft:stronghold 0,0 offset 2 minY 52",
            "kept-over-band minecraft:desert_pyramid 0,0 offset -4 minY 60",
            "kept-over-band minecraft:stronghold 3,-5 offset 2 minY 52",
            "kept-over-band minecraft:desert_pyramid 3,-5 offset -4 minY 60",
            "kept-over-band minecraft:stronghold -11,7 offset 2 minY 52",
            "kept-over-band minecraft:desert_pyramid -11,7 offset -4 minY 60");
    private static final List<String> PINNED_REACHES = List.of(
            "{} NONE 32,40,-48 52,54,-28",
            "{} BEARD_THIN 20,28,-60 64,66,-16",
            "{} BURY 20,28,-60 64,66,-16",
            "{\"mode\":\"PRESERVE\"} NONE 32,40,-48 52,54,-28",
            "{\"mode\":\"PRESERVE\"} BEARD_THIN 32,40,-48 52,54,-28",
            "{\"mode\":\"PRESERVE\"} BURY 32,40,-48 52,54,-28",
            "{\"mode\":\"VACUUM\"} NONE 20,40,-60 64,54,-16",
            "{\"mode\":\"VACUUM\"} BEARD_THIN 20,40,-60 64,54,-16",
            "{\"mode\":\"VACUUM\"} BURY 20,40,-60 64,54,-16",
            "{\"mode\":\"ENCASE\",\"horizontalPadding\":4} NONE 28,40,-52 56,54,-24",
            "{\"mode\":\"ENCASE\",\"horizontalPadding\":4} BEARD_THIN 28,40,-52 56,54,-24",
            "{\"mode\":\"ENCASE\",\"horizontalPadding\":4} BURY 28,40,-52 56,54,-24",
            "{\"mode\":\"BORE\",\"horizontalPadding\":20} NONE 12,40,-68 72,54,-8",
            "{\"mode\":\"BORE\",\"horizontalPadding\":20} BEARD_THIN 12,40,-68 72,54,-8",
            "{\"mode\":\"BORE\",\"horizontalPadding\":20} BURY 12,40,-68 72,54,-8",
            "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":112} NONE -80,40,-160 164,54,84",
            "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":112} BEARD_THIN -80,40,-160 164,54,84",
            "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":112} BURY -80,40,-160 164,54,84",
            "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":0} NONE 32,40,-48 52,54,-28",
            "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":0} BEARD_THIN 32,40,-48 52,54,-28",
            "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":0} BURY 32,40,-48 52,54,-28");

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The module's shift for a stronghold is the move it makes; for a desert temple, which the platform aligns to the
     * terrain itself, it is the adjustment's own shift on top of that alignment.
     */
    @Test
    public void platformStartsMoveWhereTheirAdjustmentSays() {
        List<String> actual = new ArrayList<>();
        for (String pinned : PINNED_SHIFTS) {
            String[] parts = pinned.split(" ");
            String structureId = parts[1];
            String[] chunkXZ = parts[2].split(",");
            ChunkPos chunk = new ChunkPos(Integer.parseInt(chunkXZ[0]), Integer.parseInt(chunkXZ[1]));
            boolean stronghold = structureId.equals("minecraft:stronghold");
            int moduleShift = stronghold ? Integer.parseInt(parts[4]) : ADJUSTMENT_SHIFTS.get(parts[0]);
            StructureStart start = start(chunk, stronghold ? 50 : 64, TerrainAdjustment.NONE);
            int offset = NativeStructureVerticalPlacer.applyVerticalPlacement(start, structureId, moduleShift,
                    SEA_LEVEL, WORLD_MIN_Y, WORLD_MAX_Y_EXCLUSIVE, SURFACE);
            actual.add(parts[0] + " " + structureId + " " + parts[2] + " offset " + offset
                    + " minY " + start.getBoundingBox().minY());
        }
        assertEquals(PINNED_SHIFTS, actual);
    }

    @Test
    public void startsReachTheChunksTheirTerrainFitSays() {
        List<String> actual = new ArrayList<>();
        for (Map.Entry<String, NativeStructureReach> fit : TERRAIN_REACHES.entrySet()) {
            for (TerrainAdjustment adjustment : List.of(TerrainAdjustment.NONE, TerrainAdjustment.BEARD_THIN, TerrainAdjustment.BURY)) {
                StructureStart start = start(new ChunkPos(2, -3), 40, adjustment);
                BoundingBox bounds = NativeStructureReferenceEnvelope.referenceBounds(start, start.getStructure(),
                        List.of(fit.getValue()), "minecraft:village_plains");
                actual.add(fit.getKey() + " " + adjustment + " " + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ()
                        + " " + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ());
            }
        }
        assertEquals(PINNED_REACHES, actual);
    }

    private static Map<String, Integer> adjustmentShifts() {
        Map<String, Integer> shifts = new LinkedHashMap<>();
        shifts.put("none", 0);
        shifts.put("shift", 5);
        shifts.put("burial", -7);
        shifts.put("burial-unshifted", 0);
        shifts.put("kept", 3);
        shifts.put("band", 0);
        shifts.put("narrow-band", 4);
        shifts.put("kept-over-band", 2);
        return shifts;
    }

    /** What the structure module answers for each terrain fit (pinned in core by {@code StartLayoutsPinTest}). */
    private static Map<String, NativeStructureReach> terrainReaches() {
        Map<String, NativeStructureReach> reaches = new LinkedHashMap<>();
        reaches.put("{}", NativeStructureReach.PLATFORM);
        reaches.put("{\"mode\":\"PRESERVE\"}", new NativeStructureReach(false, 0));
        reaches.put("{\"mode\":\"VACUUM\"}", new NativeStructureReach(false, 12));
        reaches.put("{\"mode\":\"ENCASE\",\"horizontalPadding\":4}", new NativeStructureReach(false, 4));
        reaches.put("{\"mode\":\"BORE\",\"horizontalPadding\":20}", new NativeStructureReach(false, 20));
        reaches.put("{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":112}", new NativeStructureReach(false, 112));
        reaches.put("{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":0}", new NativeStructureReach(false, 0));
        return reaches;
    }

    private static StructureStart start(ChunkPos chunk, int minY, TerrainAdjustment adjustment) {
        Structure structure = new DesertPyramidStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, adjustment));
        StructurePiece piece = new DesertPyramidPiece(RandomSource.create(7L), chunk.getMinBlockX(), chunk.getMinBlockZ());
        piece.move(0, minY - piece.getBoundingBox().minY(), 0);
        return new StructureStart(structure, chunk, 0, new PiecesContainer(List.of(piece)));
    }
}
