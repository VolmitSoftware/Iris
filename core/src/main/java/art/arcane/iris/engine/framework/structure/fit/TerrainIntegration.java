/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.framework.structure.fit;

import art.arcane.iris.builtin.structure.StructureCarveShape;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.engine.mantle.components.StructureCarveEnvelope;
import art.arcane.iris.engine.mantle.components.StructureCarvingFootprint;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSpec;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.TerrainAdaptation;
import com.volmit.geometry.BlockPosition;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * The terrain fitting that runs before a start's pieces are placed, for every mode but VACUUM: SOURCE replays the
 * platform's BURY and ENCAPSULATE adaptations by filling the envelope around rigid pieces with the nearby natural
 * material, ENCASE fills the padded piece boxes, BORE clears them, FORCE_CARVE clears a box or a rounded, optionally
 * eroded shape around the pieces' silhouette. Legacy templates then clear their authored air over the terrain.
 */
final class TerrainIntegration {
    private static final long CARVE_CEILING_ROLL_SIGNATURE = 0x2A17L;
    private static final long CARVE_FLOOR_ROLL_SIGNATURE = 0x5B3DL;
    private static final long CARVE_LOBE_SIGNATURE = 0x7C41L;
    private static final long CARVE_IDENTITY_X = 341873128712L;
    private static final long CARVE_IDENTITY_Z = 132897987541L;
    private static final int SOURCE_BURY_HORIZONTAL_RADIUS = 6;
    private static final int SOURCE_BURY_VERTICAL_RADIUS = 12;
    private static final int SOURCE_ENCAPSULATE_RADIUS = 12;
    private static final int SOURCE_JUNCTION_RADIUS = 12;
    private static final int SOURCE_MATERIAL_SAMPLE_RADIUS = 8;
    private static final Set<String> VANILLA_SOURCE_TERRAIN_BLOCKS = Set.of(
            "minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
            "minecraft:tuff", "minecraft:deepslate", "minecraft:netherrack", "minecraft:basalt",
            "minecraft:blackstone", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium", "minecraft:mud",
            "minecraft:moss_block", "minecraft:sand", "minecraft:red_sand", "minecraft:terracotta",
            "minecraft:crimson_nylium", "minecraft:warped_nylium", "minecraft:snow_block",
            "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:soul_sand",
            "minecraft:soul_soil", "minecraft:end_stone", "minecraft:gravel", "minecraft:clay",
            "minecraft:calcite", "minecraft:dripstone_block", "minecraft:sandstone",
            "minecraft:red_sandstone", "minecraft:sculk");
    private static final List<ResourceId> SOURCE_TERRAIN_TAGS = List.of(
            ResourceId.parse("minecraft:base_stone_overworld"), ResourceId.parse("minecraft:base_stone_nether"),
            ResourceId.parse("minecraft:substrate_overworld"), ResourceId.parse("minecraft:dirt"),
            ResourceId.parse("minecraft:sand"), ResourceId.parse("minecraft:terracotta"),
            ResourceId.parse("minecraft:mud"), ResourceId.parse("minecraft:moss_blocks"),
            ResourceId.parse("minecraft:grass_blocks"), ResourceId.parse("minecraft:nylium"),
            ResourceId.parse("minecraft:snow"), ResourceId.parse("minecraft:ice"),
            ResourceId.parse("minecraft:coral_blocks"), ResourceId.parse("minecraft:soul_fire_base_blocks"));

    private TerrainIntegration() {
    }

    /**
     * Integrates the terrain of every start, then clears the legacy template air of those that replay their own
     * adaptation or are encased. The natural material SOURCE fills with is read once for all starts, before any fill.
     */
    static void prepareTerrain(FitBlocks blocks, Box area, List<FitStart> starts, long seed,
                               IntFunction<BlockState> naturalBlock, FitPalettes palettes) {
        if (starts.isEmpty()) {
            return;
        }
        SourceTerrainSnapshot sourceTerrain = captureSourceTerrain(blocks, area, starts);
        for (FitStart start : starts) {
            integrateTerrain(blocks, area, start, seed, naturalBlock, palettes, sourceTerrain);
        }
        for (FitStart start : starts) {
            if (clearsLegacyTemplateAir(start)) {
                clearLegacyTemplateAir(blocks, area, start);
            }
        }
    }

    static void integrateTerrain(FitBlocks blocks, Box area, FitStart start, long seed,
                                 IntFunction<BlockState> naturalBlock, FitPalettes palettes,
                                 SourceTerrainSnapshot sourceTerrain) {
        StructureTerrain terrain = start.terrain();
        StructureTerrainMode mode = terrain.resolvedMode();
        switch (mode) {
            case SOURCE -> integrateSourceTerrain(blocks, area, start, naturalBlock, sourceTerrain);
            case PRESERVE, VACUUM -> {
            }
            case ENCASE -> encasePieces(blocks, area, start, terrain, seed, naturalBlock, palettes);
            case BORE, FORCE_CARVE -> carve(blocks, area, start, terrain, seed);
        }
    }

    private static void carve(FitBlocks blocks, Box area, FitStart start, StructureTerrain terrain, long seed) {
        StructureCarveShape shape = terrain.resolvedMode() == StructureTerrainMode.BORE
                ? StructureCarveShape.BOX : terrain.resolvedShape();
        if (shape == StructureCarveShape.BOX) {
            carvePieceBoxes(blocks, area, start, terrain);
            return;
        }
        carveOrganicColumns(blocks, area, organicCarve(
                CarveFootprints.of(start, Math.max(0, terrain.horizontalPadding())),
                terrain, shape, carveNoiseIdentity(seed, start)));
    }

    static SourceTerrainSnapshot captureSourceTerrain(FitBlocks blocks, Box area, List<FitStart> starts) {
        BitSet requiredLayers = new BitSet(area.ySpan());
        boolean requiresSnapshot = false;
        for (FitStart start : starts) {
            if (requiresSourceTerrainFill(start)) {
                requiresSnapshot = true;
                markSourceTerrainLayers(area, start, requiredLayers);
            }
        }
        return requiresSnapshot ? SourceTerrainSnapshot.capture(blocks, area, requiredLayers) : null;
    }

    private static void markSourceTerrainLayers(Box area, FitStart start, BitSet requiredLayers) {
        TerrainAdaptation adaptation = start.adaptation();
        for (FitPiece piece : start.pieces()) {
            if (!piece.isRigidOrFixed()) {
                continue;
            }
            Box bounds = piece.box();
            long horizontalDistanceSquared = minimumHorizontalDistanceSquared(area, bounds);
            if (adaptation == TerrainAdaptation.BURY) {
                long remainingDistanceSquared = (long) SOURCE_BURY_VERTICAL_RADIUS * SOURCE_BURY_VERTICAL_RADIUS
                        - 1L - horizontalDistanceSquared * 4L;
                if (remainingDistanceSquared < 0L) {
                    continue;
                }
                int verticalRadius = (int) Math.floor(Math.sqrt(remainingDistanceSquared));
                long groundY = piece.groundY();
                markLayers(area, requiredLayers, groundY - verticalRadius, groundY + verticalRadius);
            } else {
                long remainingDistanceSquared = (long) SOURCE_ENCAPSULATE_RADIUS * SOURCE_ENCAPSULATE_RADIUS
                        - 1L - horizontalDistanceSquared;
                if (remainingDistanceSquared < 0L) {
                    continue;
                }
                int verticalRadius = (int) Math.floor(Math.sqrt(remainingDistanceSquared));
                markLayers(area, requiredLayers, (long) bounds.minY() - verticalRadius,
                        (long) bounds.maxY() + verticalRadius);
            }
        }
        markSourceJunctionLayers(area, start, requiredLayers);
    }

    private static void markSourceJunctionLayers(Box area, FitStart start, BitSet requiredLayers) {
        for (BlockPosition anchor : sourceJunctionAnchors(start)) {
            long deltaX = intervalDistance(area.minX(), area.maxX(), anchor.x(), anchor.x());
            long deltaZ = intervalDistance(area.minZ(), area.maxZ(), anchor.z(), anchor.z());
            long horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            long remainingDistanceSquared = (long) SOURCE_JUNCTION_RADIUS * SOURCE_JUNCTION_RADIUS
                    - 1L - horizontalDistanceSquared;
            if (remainingDistanceSquared < 1L) {
                continue;
            }
            int verticalRadius = (int) Math.floor(Math.sqrt(remainingDistanceSquared));
            markLayers(area, requiredLayers, (long) anchor.y() - verticalRadius, (long) anchor.y() - 1L);
        }
    }

    private static long minimumHorizontalDistanceSquared(Box area, Box bounds) {
        long deltaX = intervalDistance(area.minX(), area.maxX(), bounds.minX(), bounds.maxX());
        long deltaZ = intervalDistance(area.minZ(), area.maxZ(), bounds.minZ(), bounds.maxZ());
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static long intervalDistance(int firstMin, int firstMax, int secondMin, int secondMax) {
        if (firstMax < secondMin) {
            return (long) secondMin - firstMax;
        }
        if (firstMin > secondMax) {
            return (long) firstMin - secondMax;
        }
        return 0L;
    }

    private static void markLayers(Box area, BitSet requiredLayers, long minimumY, long maximumY) {
        long clippedMinimumY = Math.max(area.minY(), minimumY);
        long clippedMaximumY = Math.min(area.maxY(), maximumY);
        if (clippedMinimumY > clippedMaximumY) {
            return;
        }
        int fromIndex = Math.toIntExact(clippedMinimumY - area.minY());
        int toIndex = Math.toIntExact(clippedMaximumY - area.minY() + 1L);
        requiredLayers.set(fromIndex, toIndex);
    }

    /** Whether SOURCE fills terrain for the start: it buries or encapsulates its rigid pieces. */
    private static boolean requiresSourceTerrainFill(FitStart start) {
        return start.terrain().resolvedMode() == StructureTerrainMode.SOURCE
                && (start.adaptation() == TerrainAdaptation.BURY || start.adaptation() == TerrainAdaptation.ENCAPSULATE);
    }

    static boolean clearsLegacyTemplateAir(FitStart start) {
        StructureTerrainMode mode = start.terrain().resolvedMode();
        return mode == StructureTerrainMode.ENCASE
                || mode == StructureTerrainMode.SOURCE && start.adaptation() != TerrainAdaptation.NONE;
    }

    private static void integrateSourceTerrain(FitBlocks blocks, Box area, FitStart start,
                                               IntFunction<BlockState> naturalBlock, SourceTerrainSnapshot sourceTerrain) {
        if (start.adaptation() == TerrainAdaptation.BURY) {
            fillBuriedTerrain(blocks, area, start, naturalBlock, requireSourceTerrain(blocks, area, start, sourceTerrain));
        } else if (start.adaptation() == TerrainAdaptation.ENCAPSULATE) {
            fillEncapsulatedTerrain(blocks, area, start, naturalBlock,
                    requireSourceTerrain(blocks, area, start, sourceTerrain));
        }
    }

    private static SourceTerrainSnapshot requireSourceTerrain(FitBlocks blocks, Box area, FitStart start,
                                                              SourceTerrainSnapshot sourceTerrain) {
        if (sourceTerrain != null) {
            return sourceTerrain;
        }
        BitSet requiredLayers = new BitSet(area.ySpan());
        markSourceTerrainLayers(area, start, requiredLayers);
        return SourceTerrainSnapshot.capture(blocks, area, requiredLayers);
    }

    private static void fillBuriedTerrain(FitBlocks blocks, Box area, FitStart start, IntFunction<BlockState> naturalBlock,
                                          SourceTerrainSnapshot sourceTerrain) {
        for (FitPiece piece : start.pieces()) {
            if (!piece.isRigidOrFixed()) {
                continue;
            }
            Box bounds = piece.box();
            int groundY = piece.groundY();
            int minX = Math.max(area.minX(), bounds.minX() - SOURCE_BURY_HORIZONTAL_RADIUS);
            int maxX = Math.min(area.maxX(), bounds.maxX() + SOURCE_BURY_HORIZONTAL_RADIUS);
            int minY = Math.max(area.minY(), groundY - SOURCE_BURY_VERTICAL_RADIUS);
            int maxY = Math.min(area.maxY(), groundY + SOURCE_BURY_VERTICAL_RADIUS);
            int minZ = Math.max(area.minZ(), bounds.minZ() - SOURCE_BURY_HORIZONTAL_RADIUS);
            int maxZ = Math.min(area.maxZ(), bounds.maxZ() + SOURCE_BURY_HORIZONTAL_RADIUS);
            for (int x = minX; x <= maxX; x++) {
                int outX = outset(x, bounds.minX(), bounds.maxX());
                for (int z = minZ; z <= maxZ; z++) {
                    int outZ = outset(z, bounds.minZ(), bounds.maxZ());
                    for (int y = minY; y <= maxY; y++) {
                        if (insideBurialEnvelope(outX, Math.abs(y - groundY), outZ)) {
                            fillEncaseable(blocks, x, y, z, naturalBlock, sourceTerrain);
                        }
                    }
                }
            }
        }
        fillSourceJunctionTerrain(blocks, area, start, naturalBlock, sourceTerrain);
    }

    private static void fillEncapsulatedTerrain(FitBlocks blocks, Box area, FitStart start,
                                                IntFunction<BlockState> naturalBlock, SourceTerrainSnapshot sourceTerrain) {
        for (FitPiece piece : start.pieces()) {
            if (!piece.isRigidOrFixed()) {
                continue;
            }
            Box bounds = piece.box();
            int minX = Math.max(area.minX(), bounds.minX() - SOURCE_ENCAPSULATE_RADIUS);
            int maxX = Math.min(area.maxX(), bounds.maxX() + SOURCE_ENCAPSULATE_RADIUS);
            int minY = Math.max(area.minY(), bounds.minY() - SOURCE_ENCAPSULATE_RADIUS);
            int maxY = Math.min(area.maxY(), bounds.maxY() + SOURCE_ENCAPSULATE_RADIUS);
            int minZ = Math.max(area.minZ(), bounds.minZ() - SOURCE_ENCAPSULATE_RADIUS);
            int maxZ = Math.min(area.maxZ(), bounds.maxZ() + SOURCE_ENCAPSULATE_RADIUS);
            for (int x = minX; x <= maxX; x++) {
                int outX = outset(x, bounds.minX(), bounds.maxX());
                for (int z = minZ; z <= maxZ; z++) {
                    int outZ = outset(z, bounds.minZ(), bounds.maxZ());
                    for (int y = minY; y <= maxY; y++) {
                        if (insideEncapsulationEnvelope(outX, outset(y, bounds.minY(), bounds.maxY()), outZ)) {
                            fillEncaseable(blocks, x, y, z, naturalBlock, sourceTerrain);
                        }
                    }
                }
            }
        }
        fillSourceJunctionTerrain(blocks, area, start, naturalBlock, sourceTerrain);
    }

    static boolean insideBurialEnvelope(int outX, int verticalDistance, int outZ) {
        long horizontalSquared = (long) outX * outX + (long) outZ * outZ;
        long verticalSquared = (long) verticalDistance * verticalDistance;
        return horizontalSquared * 4L + verticalSquared < (long) SOURCE_BURY_VERTICAL_RADIUS * SOURCE_BURY_VERTICAL_RADIUS;
    }

    static boolean insideEncapsulationEnvelope(int outX, int outY, int outZ) {
        return (long) outX * outX + (long) outY * outY + (long) outZ * outZ
                < (long) SOURCE_ENCAPSULATE_RADIUS * SOURCE_ENCAPSULATE_RADIUS;
    }

    static boolean insideSourceJunctionEnvelope(int deltaX, int deltaY, int deltaZ) {
        if (deltaY >= 0) {
            return false;
        }
        return (long) deltaX * deltaX + (long) deltaY * deltaY + (long) deltaZ * deltaZ
                < (long) SOURCE_JUNCTION_RADIUS * SOURCE_JUNCTION_RADIUS;
    }

    private static void fillSourceJunctionTerrain(FitBlocks blocks, Box area, FitStart start,
                                                  IntFunction<BlockState> naturalBlock, SourceTerrainSnapshot sourceTerrain) {
        for (BlockPosition anchor : sourceJunctionAnchors(start)) {
            int minX = Math.max(area.minX(), anchor.x() - SOURCE_JUNCTION_RADIUS + 1);
            int maxX = Math.min(area.maxX(), anchor.x() + SOURCE_JUNCTION_RADIUS - 1);
            int minY = Math.max(area.minY(), anchor.y() - SOURCE_JUNCTION_RADIUS + 1);
            int maxY = Math.min(area.maxY(), anchor.y() - 1);
            int minZ = Math.max(area.minZ(), anchor.z() - SOURCE_JUNCTION_RADIUS + 1);
            int maxZ = Math.min(area.maxZ(), anchor.z() + SOURCE_JUNCTION_RADIUS - 1);
            for (int x = minX; x <= maxX; x++) {
                int deltaX = x - anchor.x();
                for (int z = minZ; z <= maxZ; z++) {
                    int deltaZ = z - anchor.z();
                    for (int y = minY; y <= maxY; y++) {
                        if (insideSourceJunctionEnvelope(deltaX, y - anchor.y(), deltaZ)) {
                            fillEncaseable(blocks, x, y, z, naturalBlock, sourceTerrain);
                        }
                    }
                }
            }
        }
    }

    /** The jigsaw junctions of every piece, each place once. The order is a set's, as no fill depends on it. */
    private static Set<BlockPosition> sourceJunctionAnchors(FitStart start) {
        Set<BlockPosition> anchors = new HashSet<>();
        for (FitPiece piece : start.pieces()) {
            anchors.addAll(piece.junctions());
        }
        return anchors;
    }

    private static int outset(int value, int minimum, int maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return Math.max(0, value - maximum);
    }

    private static void fillEncaseable(FitBlocks blocks, int x, int y, int z, IntFunction<BlockState> naturalBlock,
                                       SourceTerrainSnapshot sourceTerrain) {
        if (blocks.isEncaseable(blocks.get(x, y, z))) {
            blocks.set(x, y, z, sourceEncaseBlock(x, y, z, naturalBlock, sourceTerrain));
        }
    }

    /** The nearest natural terrain block of the snapshot along the axes, within 8 blocks; else the dimension's own. */
    static BlockState sourceEncaseBlock(int x, int y, int z, IntFunction<BlockState> naturalBlock,
                                        SourceTerrainSnapshot sourceTerrain) {
        for (int distance = 1; distance <= SOURCE_MATERIAL_SAMPLE_RADIUS; distance++) {
            BlockState sampled = sourceTerrain.stateAt(x - distance, y, z);
            if (sampled != null) {
                return sampled;
            }
            sampled = sourceTerrain.stateAt(x + distance, y, z);
            if (sampled != null) {
                return sampled;
            }
            sampled = sourceTerrain.stateAt(x, y, z - distance);
            if (sampled != null) {
                return sampled;
            }
            sampled = sourceTerrain.stateAt(x, y, z + distance);
            if (sampled != null) {
                return sampled;
            }
        }
        return naturalBlock.apply(y);
    }

    private static BlockState sourceTerrainBlock(FitBlocks blocks, BlockState state) {
        if (!blocks.isSolid(state) || blocks.isTree(state)) {
            return null;
        }
        if (VANILLA_SOURCE_TERRAIN_BLOCKS.contains(state.material().toString())) {
            return state;
        }
        for (ResourceId tag : SOURCE_TERRAIN_TAGS) {
            if (blocks.inTag(state, tag)) {
                return state;
            }
        }
        return null;
    }

    static OrganicCarve organicCarve(StructureCarvingFootprint footprint, StructureTerrain terrain,
                                     StructureCarveShape shape, long identity) {
        double strength = terrain.resolvedErosionStrength();
        double lobeStrength = terrain.resolvedLobeStrength();
        RNG noiseRng = new RNG(identity);
        CNG blob = null;
        CNG ceilingRoll = null;
        CNG floorRoll = null;
        CNG lobe = null;
        if (shape == StructureCarveShape.ERODED && strength > 0D) {
            blob = CNG.signature(noiseRng);
            ceilingRoll = CNG.signature(noiseRng.nextParallelRNG(CARVE_CEILING_ROLL_SIGNATURE));
            floorRoll = CNG.signature(noiseRng.nextParallelRNG(CARVE_FLOOR_ROLL_SIGNATURE));
        }
        if (shape == StructureCarveShape.ERODED && lobeStrength > 0D) {
            // A plain single octave channel: the fractured signature noise has no usable low frequency band.
            lobe = new CNG(noiseRng.nextParallelRNG(CARVE_LOBE_SIGNATURE), 1D, 1);
        }
        return new OrganicCarve(footprint, shape,
                Math.max(0, terrain.horizontalPadding()),
                Math.max(0, terrain.ceilingPadding()),
                Math.max(0, terrain.floorPadding()),
                strength, terrain.resolvedErosionFrequency(), blob, ceilingRoll, floorRoll,
                lobe, terrain.resolvedLobeFrequency(), lobeStrength);
    }

    static void carveOrganicColumns(FitBlocks blocks, Box area, OrganicCarve carve) {
        StructureCarvingFootprint footprint = carve.footprint();
        int minX = Math.max(area.minX(), footprint.minX());
        int maxX = Math.min(area.maxX(), footprint.maxX());
        int minZ = Math.max(area.minZ(), footprint.minZ());
        int maxZ = Math.min(area.maxZ(), footprint.maxZ());
        if (minX > maxX || minZ > maxZ) {
            return;
        }
        boolean eroded = carve.blob() != null;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int index = footprint.indexAt(x, z);
                long horizontalDistanceSquared = footprint.distanceSquaredAt(index);
                double sideReach = StructureCarveEnvelope.lobedSideReach(carve.lobe(),
                        carve.lobeFrequency(), carve.lobeStrength(), x, z, carve.horizontalPadding());
                double sideReachSquared = sideReach * sideReach;
                if (horizontalDistanceSquared > sideReachSquared) {
                    continue;
                }
                double normalizedHorizontal = sideReachSquared == 0D ? 0D : horizontalDistanceSquared / sideReachSquared;
                int sourceMinY = footprint.sourceMinYAt(index);
                int sourceMaxY = footprint.sourceMaxYAt(index);
                double upReach = eroded
                        ? StructureCarveEnvelope.lobedUpReach(carve.lobe(), carve.lobeFrequency(),
                                carve.lobeStrength(), x, z,
                                StructureCarveEnvelope.erodedUpReach(carve.ceilingRoll(),
                                        carve.frequency(), carve.strength(), x, z, carve.ceilingPadding()))
                        : Math.max(1D, carve.ceilingPadding());
                double floorReach = eroded
                        ? StructureCarveEnvelope.erodedDownReach(carve.floorRoll(), carve.frequency(),
                                carve.strength(), x, z, carve.floorPadding())
                        : carve.floorPadding();
                int columnMinY = Math.max(area.minY(), sourceMinY - (int) Math.floor(floorReach));
                int columnMaxY = Math.min(area.maxY(),
                        sourceMaxY + (eroded ? (int) Math.ceil(upReach) : carve.ceilingPadding()));
                double downReach = Math.max(1D, floorReach);
                for (int y = columnMinY; y <= columnMaxY; y++) {
                    double normalizedVertical = StructureCarveEnvelope.normalizedVerticalDistance(
                            y, sourceMinY, sourceMaxY, upReach, downReach);
                    double distanceSquared = normalizedHorizontal + normalizedVertical * normalizedVertical;
                    if (distanceSquared > 1D) {
                        continue;
                    }
                    if (distanceSquared > 0D && eroded) {
                        double noise = carve.blob().fitDouble(0D, 1D,
                                x * carve.frequency(), y * carve.frequency(), z * carve.frequency());
                        if (!StructureCarveEnvelope.shouldCarveOverboreCell(
                                carve.shape(), distanceSquared, noise, carve.strength())) {
                            continue;
                        }
                    }
                    blocks.set(x, y, z, FitBlocks.AIR);
                }
            }
        }
    }

    private static long carveNoiseIdentity(long seed, FitStart start) {
        return seed
                ^ ((long) start.chunkX() * CARVE_IDENTITY_X)
                ^ ((long) start.chunkZ() * CARVE_IDENTITY_Z)
                ^ start.structureId().hashCode();
    }

    private static void encasePieces(FitBlocks blocks, Box area, FitStart start, StructureTerrain terrain, long seed,
                                     IntFunction<BlockState> naturalBlock, FitPalettes palettes) {
        Optional<PaletteSpec> palette = terrain.encasePalette();
        long paletteSeed = seed ^ start.structureId().hashCode();
        for (FitPiece piece : start.pieces()) {
            Box shell = paddedPieceArea(area, piece.box(), terrain);
            if (shell == null) {
                continue;
            }
            for (int x = shell.minX(); x <= shell.maxX(); x++) {
                for (int z = shell.minZ(); z <= shell.maxZ(); z++) {
                    for (int y = shell.minY(); y <= shell.maxY(); y++) {
                        if (!blocks.isEncaseable(blocks.get(x, y, z))) {
                            continue;
                        }
                        BlockState fill = palette.isEmpty()
                                ? naturalBlock.apply(y)
                                : Objects.requireNonNull(palettes.at(palette.get(), paletteSeed, x, y, z),
                                        "Encase palette returned no block for " + start.structureId() + " at "
                                                + x + "," + y + "," + z);
                        blocks.set(x, y, z, fill);
                    }
                }
            }
        }
    }

    private static void carvePieceBoxes(FitBlocks blocks, Box area, FitStart start, StructureTerrain terrain) {
        for (FitPiece piece : start.pieces()) {
            Box carve = paddedPieceArea(area, piece.box(), terrain);
            if (carve == null) {
                continue;
            }
            for (int x = carve.minX(); x <= carve.maxX(); x++) {
                for (int z = carve.minZ(); z <= carve.maxZ(); z++) {
                    for (int y = carve.minY(); y <= carve.maxY(); y++) {
                        blocks.set(x, y, z, FitBlocks.AIR);
                    }
                }
            }
        }
    }

    private static Box paddedPieceArea(Box area, Box bounds, StructureTerrain terrain) {
        int horizontalPadding = Math.max(0, terrain.horizontalPadding());
        int minX = Math.max(area.minX(), bounds.minX() - horizontalPadding);
        int minY = Math.max(area.minY(), bounds.minY() - Math.max(0, terrain.floorPadding()));
        int minZ = Math.max(area.minZ(), bounds.minZ() - horizontalPadding);
        int maxX = Math.min(area.maxX(), bounds.maxX() + horizontalPadding);
        int maxY = Math.min(area.maxY(), bounds.maxY() + Math.max(0, terrain.ceilingPadding()));
        int maxZ = Math.min(area.maxZ(), bounds.maxZ() + horizontalPadding);
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Clears the air of rigid legacy templates over the terrain, from their ground level up, sparing trees. */
    static void clearLegacyTemplateAir(FitBlocks blocks, Box area, FitStart start) {
        for (FitPiece piece : start.pieces()) {
            if (!piece.isRigid() || !piece.box().intersects(area)) {
                continue;
            }
            int groundY = piece.groundY();
            for (BlockPosition air : piece.legacyAir(area)) {
                BlockState existing = blocks.get(air.x(), air.y(), air.z());
                if (shouldClearLegacyAir(air.y(), groundY, blocks.isAir(existing)) && !blocks.isTree(existing)) {
                    blocks.set(air.x(), air.y(), air.z(), FitBlocks.AIR);
                }
            }
        }
    }

    static boolean shouldClearLegacyAir(int airY, int groundY, boolean existingAir) {
        return airY >= groundY && !existingAir;
    }

    /** The natural terrain blocks of some layers of the area, read before any fill, so fills never feed on fills. */
    static final class SourceTerrainSnapshot {
        private final Box area;
        private final int width;
        private final BlockState[][] statesByLayer;
        private final int sampledCells;

        private SourceTerrainSnapshot(Box area, int width, BlockState[][] statesByLayer, int sampledCells) {
            this.area = area;
            this.width = width;
            this.statesByLayer = statesByLayer;
            this.sampledCells = sampledCells;
        }

        static SourceTerrainSnapshot capture(FitBlocks blocks, Box area, BitSet requiredLayers) {
            Objects.requireNonNull(requiredLayers, "Source terrain snapshot requires sampled layers");
            int width = area.xSpan();
            int horizontalCells = Math.multiplyExact(width, area.zSpan());
            int height = area.ySpan();
            BlockState[][] statesByLayer = new BlockState[height][];
            int sampledCells = 0;
            for (int layer = requiredLayers.nextSetBit(0); layer >= 0; layer = requiredLayers.nextSetBit(layer + 1)) {
                if (layer >= height) {
                    throw new IllegalArgumentException("Source terrain snapshot layer exceeds writable bounds: " + layer);
                }
                BlockState[] states = new BlockState[horizontalCells];
                statesByLayer[layer] = states;
                sampledCells = Math.addExact(sampledCells, horizontalCells);
                int y = Math.addExact(area.minY(), layer);
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    for (int x = area.minX(); x <= area.maxX(); x++) {
                        states[area.columnIndex(x, z)] = sourceTerrainBlock(blocks, blocks.get(x, y, z));
                    }
                }
            }
            return new SourceTerrainSnapshot(area, width, statesByLayer, sampledCells);
        }

        BlockState stateAt(int x, int y, int z) {
            if (!area.contains(x, y, z)) {
                return null;
            }
            BlockState[] states = statesByLayer[y - area.minY()];
            if (states == null) {
                return null;
            }
            return states[(z - area.minZ()) * width + x - area.minX()];
        }

        int sampledCells() {
            return sampledCells;
        }
    }

    record OrganicCarve(StructureCarvingFootprint footprint, StructureCarveShape shape,
                        int horizontalPadding, int ceilingPadding, int floorPadding,
                        double strength, double frequency, CNG blob, CNG ceilingRoll, CNG floorRoll,
                        CNG lobe, double lobeFrequency, double lobeStrength) {
    }
}
