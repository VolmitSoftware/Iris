package art.arcane.iris.nativegen.v26_3_R1;

import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntBinaryOperator;

/**
 * Writes WORLD_SURFACE_WG and OCEAN_FLOOR_WG from Iris engine heights.
 *
 * Iris writes its terrain straight into LevelChunkSection, so neither vanilla's noise fill nor
 * ProtoChunk.setBlockState ever updates the two worldgen heightmaps. Without these writes
 * ChunkAccess.getHeight lazily primes the map from finished blocks - Iris trees, leaves and snow
 * included - and vanilla pieces that self-snap against WORLD_SURFACE_WG (igloo) or OCEAN_FLOOR_WG
 * (ocean ruins, buried treasure, mineshaft interiors) anchor to the canopy instead of the terrain.
 *
 * Both resolvers take world block coordinates and return the absolute first free Y, matching
 * Heightmap's internal "first available" semantics: WORLD_SURFACE_WG counts fluid, OCEAN_FLOOR_WG
 * does not.
 */
public final class WorldgenTerrainHeightmaps {
    private static final int COLUMNS = 256;
    private static final int PLACEMENT_CHUNK_MARGIN = 1;

    private WorldgenTerrainHeightmaps() {
    }

    public static void primeTerrain(ChunkAccess chunk, IntBinaryOperator surfaceFirstFreeY,
                                    IntBinaryOperator floorFirstFreeY) {
        Objects.requireNonNull(chunk, "Iris worldgen heightmap priming requires a chunk");
        Objects.requireNonNull(surfaceFirstFreeY,
                "Iris worldgen heightmap priming requires a surface height resolver");
        Objects.requireNonNull(floorFirstFreeY,
                "Iris worldgen heightmap priming requires an ocean floor height resolver");
        write(chunk, Heightmap.Types.WORLD_SURFACE_WG, surfaceFirstFreeY);
        write(chunk, Heightmap.Types.OCEAN_FLOOR_WG, floorFirstFreeY);
    }

    public static void primeStructurePlacement(WorldGenLevel world, ChunkPos generationCenter,
                                               List<StructureStart> starts,
                                               IntBinaryOperator surfaceFirstFreeY,
                                               IntBinaryOperator floorFirstFreeY) {
        Objects.requireNonNull(world,
                "Iris worldgen heightmap priming requires a generation level");
        Objects.requireNonNull(generationCenter,
                "Iris worldgen heightmap priming requires a generation center");
        if (starts == null || starts.isEmpty()) {
            return;
        }
        Set<Long> primed = new HashSet<>();
        for (StructureStart start : starts) {
            if (start == null || !start.isValid()) {
                continue;
            }
            BoundingBox bounds = start.getBoundingBox();
            int minChunkX = SectionPos.blockToSectionCoord(bounds.minX()) - PLACEMENT_CHUNK_MARGIN;
            int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX()) + PLACEMENT_CHUNK_MARGIN;
            int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ()) - PLACEMENT_CHUNK_MARGIN;
            int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ()) + PLACEMENT_CHUNK_MARGIN;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (Math.abs(chunkX - generationCenter.x()) > PLACEMENT_CHUNK_MARGIN
                            || Math.abs(chunkZ - generationCenter.z()) > PLACEMENT_CHUNK_MARGIN) {
                        continue;
                    }
                    if (!primed.add(ChunkPos.pack(chunkX, chunkZ))) {
                        continue;
                    }
                    if (!world.hasChunk(chunkX, chunkZ)) {
                        continue;
                    }
                    primeTerrain(world.getChunk(chunkX, chunkZ), surfaceFirstFreeY, floorFirstFreeY);
                }
            }
        }
    }

    private static void write(ChunkAccess chunk, Heightmap.Types type, IntBinaryOperator firstFreeY) {
        ChunkPos pos = chunk.getPos();
        int minY = chunk.getMinY();
        int height = chunk.getHeight();
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        SimpleBitStorage storage = new SimpleBitStorage(Mth.ceillog2(height + 1), COLUMNS);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int firstFree = firstFreeY.applyAsInt(baseX + x, baseZ + z) - minY;
                storage.set(x + z * 16, Mth.clamp(firstFree, 0, height));
            }
        }
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
        long[] packed = storage.getRaw();
        int expected = heightmap.getRawData().length;
        if (expected != packed.length) {
            throw new IllegalStateException("Iris cannot prime " + type + " for chunk "
                    + pos.x() + "," + pos.z() + ": packed " + packed.length
                    + " words for a heightmap storing " + expected
                    + "; Heightmap.setRawData would silently fall back to block scanning");
        }
        heightmap.setRawData(chunk, type, packed);
    }
}
