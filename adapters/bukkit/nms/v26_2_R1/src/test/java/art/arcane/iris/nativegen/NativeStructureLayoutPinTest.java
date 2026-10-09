package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.NativeStructureOwnershipRecord;
import art.arcane.iris.engine.object.structure.IrisStructureYBand;
import com.google.gson.Gson;
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
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;

/**
 * Pins where the platform's own starts end up vertically for each adjustment the dimension can give them (shift,
 * band, keeping the platform's height, burial under the terrain), and which chunks a start reaches for each terrain
 * fit, so both can move without changing what a world generates.
 */
public class NativeStructureLayoutPinTest {
    private static final Gson GSON = new Gson();
    private static final int SEA_LEVEL = 61;
    private static final int WORLD_MIN_Y = -64;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;
    private static final IntBinaryOperator SURFACE = (x, z) -> 62 + Math.floorMod(x * 7 + z * 3, 11) - 5;
    private static final int[][] CHUNKS = {{0, 0}, {3, -5}, {-11, 7}};

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

    @Test
    public void platformStartsMoveWhereTheirAdjustmentSays() {
        List<String> actual = new ArrayList<>();
        for (Adjustment adjustment : adjustments()) {
            for (int[] chunkXZ : CHUNKS) {
                ChunkPos chunk = new ChunkPos(chunkXZ[0], chunkXZ[1]);
                for (String structureId : List.of("minecraft:stronghold", "minecraft:desert_pyramid")) {
                    StructureStart start = start(chunk, structureId.equals("minecraft:stronghold") ? 50 : 64,
                            TerrainAdjustment.NONE);
                    int offset = NativeStructureVerticalPlacer.applyVerticalPlacement(start, structureId,
                            adjustment.yShift(), SEA_LEVEL, WORLD_MIN_Y, WORLD_MAX_Y_EXCLUSIVE, adjustment.underground(),
                            adjustment.keepsSourceY(), band(adjustment.bandJson()), SURFACE);
                    actual.add(adjustment.name() + " " + structureId + " " + chunk.x() + "," + chunk.z()
                            + " offset " + offset + " minY " + start.getBoundingBox().minY());
                }
            }
        }
        assertEquals("Pinned shifts changed:\n" + String.join("\n", quoted(actual)), PINNED_SHIFTS, actual);
    }

    @Test
    public void startsReachTheChunksTheirTerrainFitSays() {
        List<String> actual = new ArrayList<>();
        for (String terrainJson : List.of("{}", "{\"mode\":\"PRESERVE\"}", "{\"mode\":\"VACUUM\"}",
                "{\"mode\":\"ENCASE\",\"horizontalPadding\":4}", "{\"mode\":\"BORE\",\"horizontalPadding\":20}",
                "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":112}", "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":0}")) {
            for (TerrainAdjustment adjustment : List.of(TerrainAdjustment.NONE, TerrainAdjustment.BEARD_THIN, TerrainAdjustment.BURY)) {
                StructureStart start = start(new ChunkPos(2, -3), 40, adjustment);
                BoundingBox bounds = NativeStructureReferenceEnvelope.referenceBounds(start, start.getStructure(),
                        NativeStructureTerrainIntegrator.resolveNativeTerrain(start,
                                new NativeStructureOwnershipRecord.DecisionSnapshot("null", terrainJson).restore().terrain()),
                        "minecraft:village_plains");
                actual.add(terrainJson + " " + adjustment + " " + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ()
                        + " " + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ());
            }
        }
        assertEquals("Pinned reaches changed:\n" + String.join("\n", quoted(actual)), PINNED_REACHES, actual);
    }

    private static List<String> quoted(List<String> lines) {
        return lines.stream().map(line -> "            \"" + line.replace("\"", "\\\"") + "\",").toList();
    }

    private static List<Adjustment> adjustments() {
        return List.of(
                new Adjustment("none", 0, null, false, false),
                new Adjustment("shift", 5, null, false, false),
                new Adjustment("burial", -7, null, false, true),
                new Adjustment("burial-unshifted", 0, null, false, true),
                new Adjustment("kept", 3, null, true, true),
                new Adjustment("band", 0, "{\"min\":-50,\"max\":20}", false, true),
                new Adjustment("narrow-band", 4, "{\"min\":10,\"max\":12}", false, false),
                new Adjustment("kept-over-band", 2, "{\"min\":-40,\"max\":80}", true, false));
    }

    private static IrisStructureYBand band(String json) {
        return json == null ? null : GSON.fromJson(json, IrisStructureYBand.class);
    }

    private static StructureStart start(ChunkPos chunk, int minY, TerrainAdjustment adjustment) {
        Structure structure = new DesertPyramidStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, adjustment));
        StructurePiece piece = new DesertPyramidPiece(RandomSource.create(7L), chunk.getMinBlockX(), chunk.getMinBlockZ());
        piece.move(0, minY - piece.getBoundingBox().minY(), 0);
        return new StructureStart(structure, chunk, 0, new PiecesContainer(List.of(piece)));
    }

    private record Adjustment(String name, int yShift, String bandJson, boolean keepsSourceY, boolean underground) {
    }
}
