package com.volmit.iris.scaffold.lighting;

import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.collections.BlockFaceSet;
import com.bergerkiller.bukkit.common.utils.ChunkUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.ChunkSection;
import com.bergerkiller.bukkit.common.wrappers.HeightMap;
import com.bergerkiller.bukkit.common.wrappers.IntHashMap;
import com.bergerkiller.generated.net.minecraft.server.ChunkHandle;

import com.volmit.iris.Iris;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a single chunk full with lighting-relevant information.
 * Initialization and use of this chunk in the process is as follows:<br>
 * - New lighting chunks are created for all chunks to be processed<br>
 * - notifyAccessible is called for all chunks, passing in all chunks<br>
 * - fill/fillSection is called for all chunks, after which initLight is called<br>
 * - spread is called on all chunks until all spreading is finished<br>
 * - data from all LightingChunks/Sections is gathered and saved to chunks or region files<br>
 * - possible chunk resends are performed
 */
public class LightingChunk {
    public static final int OB = ~0xf; // Outside blocks
    public static final int OC = ~0xff; // Outside chunk
    public IntHashMap<LightingCube> sections;
    public final LightingChunkNeighboring neighbors = new LightingChunkNeighboring();
    public final int[] heightmap = new int[256];
    public final World world;
    public final int chunkX, chunkZ;
    public boolean hasSkyLight = true;
    public boolean isSkyLightDirty = true;
    public boolean isBlockLightDirty = true;
    public boolean isFilled = false;
    public boolean isApplied = false;
    public IntVector2 start = new IntVector2(1, 1);
    public IntVector2 end = new IntVector2(14, 14);
    public int minY = 0;
    public int maxY = 0;
    public final ForcedChunk forcedChunk = ForcedChunk.none();
    public volatile boolean loadingStarted = false;

    public LightingChunk(World world, int x, int z) {
        this.world = world;
        this.chunkX = x;
        this.chunkZ = z;
    }

    /**
     * Gets all the sections inside this chunk.
     * Elements are never null.
     * 
     * @return sections
     */
    public Collection<LightingCube> getSections() {
        return this.sections.values();
    }

    /**
     * Efficiently iterates the vertical cubes of a chunk, only
     * querying the lookup table every 16 blocks
     * 
     * @param previous The previous cube we iterated
     * @param y Block y-coordinate
     * @return the cube at the Block y-coordinate, or null if this cube does not exist
     */
    public LightingCube nextCube(LightingCube previous, int y) {
        int cy = y >> 4;
        if (previous != null && previous.cy == cy) {
            return previous;
        } else {
            return this.sections.get(cy);
        }
    }

    /**
     * Notifies that a new chunk is accessible.
     *
     * @param chunk that is accessible
     */
    public void notifyAccessible(LightingChunk chunk) {
        final int dx = chunk.chunkX - this.chunkX;
        final int dz = chunk.chunkZ - this.chunkZ;
        // Only check neighbours, ignoring the corners and self
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || (dx != 0) == (dz != 0)) {
            return;
        }
        // Results in -16, 16 or 0 for the x/z coordinates
        neighbors.set(dx, dz, chunk);
        // Update start/end coordinates
        if (dx == 1) {
            end = new IntVector2(15, end.z);
        } else if (dx == -1) {
            start = new IntVector2(0, start.z);
        } else if (dz == 1) {
            end = new IntVector2(end.x, 15);
        } else if (dz == -1) {
            start = new IntVector2(start.x, 0);
        }
    }

    /**
     * Initializes the neighboring cubes of all the cubes of this
     * lighting chunk. This initializes the neighbors both within
     * the same chunk (vertical) and for neighboring chunks (horizontal).
     */
    public void detectCubeNeighbors() {
        for (LightingCube cube : this.sections.values()) {
            // Neighbors above and below
            cube.neighbors.set(0,  1, 0, this.sections.get(cube.cy + 1));
            cube.neighbors.set(0, -1, 0, this.sections.get(cube.cy - 1));
            // Neighbors in neighboring chunks
            cube.neighbors.set(-1, 0,  0, this.neighbors.getCube(-1,  0, cube.cy));
            cube.neighbors.set( 1, 0,  0, this.neighbors.getCube( 1,  0, cube.cy));
            cube.neighbors.set( 0, 0, -1, this.neighbors.getCube( 0, -1, cube.cy));
            cube.neighbors.set( 0, 0,  1, this.neighbors.getCube( 0,  1, cube.cy));
        }
    }

    public void fill(Chunk chunk, int[] region_y_coordinates) {
        // Fill using chunk sections
        hasSkyLight = WorldUtil.getDimensionType(chunk.getWorld()).hasSkyLight();

        List<LightingCube> lightingChunkSectionList;
        {
            // First create a list of ChunkSection objects storing the data
            // We must do this sequentially, because asynchronous access is not permitted
            List<ChunkSection> chunkSectionList = IntStream.of(region_y_coordinates)
                    .map(WorldUtil::regionToChunkIndex)
                    .flatMap(base_cy -> IntStream.range(base_cy, base_cy + WorldUtil.CHUNKS_PER_REGION_AXIS))
                    .mapToObj(cy -> WorldUtil.getSection(chunk, cy))
                    .filter(section -> section != null)
                    .collect(Collectors.toList());

            // Then process all the gathered chunk sections into a LightingChunkSection in parallel
            lightingChunkSectionList = chunkSectionList.stream()
                    .parallel()
                    .map(section -> new LightingCube(this, section, hasSkyLight))
                    .collect(Collectors.toList());
        }

        // Add to mapping
        this.sections = new IntHashMap<LightingCube>();
        for (LightingCube lightingChunkSection : lightingChunkSectionList) {
            this.sections.put(lightingChunkSection.cy, lightingChunkSection);
        }

        // Compute min/max y using sections that are available
        // Make use of the fact that they are pre-sorted by y-coordinate
        this.minY = 0;
        this.maxY = 0;
        if (!lightingChunkSectionList.isEmpty()) {
            this.minY = lightingChunkSectionList.get(0).cy << 4;
            this.maxY = (lightingChunkSectionList.get(lightingChunkSectionList.size()-1).cy << 4) + 15;
        }

        // Initialize and then load sky light heightmap information
        if (this.hasSkyLight) {
            HeightMap heightmap = ChunkUtil.getLightHeightMap(chunk, true);
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    this.heightmap[this.getHeightKey(x, z)] = Math.max(this.minY, heightmap.getHeight(x, z));
                }
            }
        } else {
            Arrays.fill(this.heightmap, this.maxY);
        }

        this.isFilled = true;
    }

    private int getHeightKey(int x, int z) {
        return x | (z << 4);
    }

    /**
     * Gets the height level (the top block that does not block light)
     *
     * @param x - coordinate
     * @param z - coordinate
     * @return height
     */
    public int getHeight(int x, int z) {
        return this.heightmap[getHeightKey(x, z)];
    }

    private final int getMaxLightLevel(LightingCube section, LightingCategory category, int lightLevel, int x, int y, int z) {
        BlockFaceSet selfOpaqueFaces = section.getOpaqueFaces(x, y, z);
        if (x >= 1 && z >= 1 && x <= 14 && z <= 14) {
            // All within this chunk - simplified calculation
            if (!selfOpaqueFaces.west()) {
                lightLevel = section.getLightIfHigher(category, lightLevel,
                        BlockFaceSet.MASK_EAST, x - 1, y, z);
            }
            if (!selfOpaqueFaces.east()) {
                lightLevel = section.getLightIfHigher(category, lightLevel,
                        BlockFaceSet.MASK_WEST, x + 1, y, z);
            }
            if (!selfOpaqueFaces.north()) {
                lightLevel = section.getLightIfHigher(category, lightLevel,
                        BlockFaceSet.MASK_SOUTH, x, y, z - 1);
            }
            if (!selfOpaqueFaces.south()) {
                lightLevel = section.getLightIfHigher(category, lightLevel,
                        BlockFaceSet.MASK_NORTH, x, y, z + 1);
            }

            // If dy is also within this section, we can simplify it
            if (y >= 1 && y <= 14) {
                if (!selfOpaqueFaces.down()) {
                    lightLevel = section.getLightIfHigher(category, lightLevel,
                            BlockFaceSet.MASK_UP, x, y - 1, z);
                }
                if (!selfOpaqueFaces.up()) {
                    lightLevel = section.getLightIfHigher(category, lightLevel,
                            BlockFaceSet.MASK_DOWN, x, y + 1, z);
                }
                return lightLevel;
            }
        } else {
            // Crossing chunk boundaries - requires neighbor checks
            if (!selfOpaqueFaces.west()) {
                lightLevel = section.getLightIfHigherNeighbor(category, lightLevel,
                        BlockFaceSet.MASK_EAST, x - 1, y, z);
            }
            if (!selfOpaqueFaces.east()) {
                lightLevel = section.getLightIfHigherNeighbor(category, lightLevel,
                        BlockFaceSet.MASK_WEST, x + 1, y, z);
            }
            if (!selfOpaqueFaces.north()) {
                lightLevel = section.getLightIfHigherNeighbor(category, lightLevel,
                        BlockFaceSet.MASK_SOUTH, x, y, z - 1);
            }
            if (!selfOpaqueFaces.south()) {
                lightLevel = section.getLightIfHigherNeighbor(category, lightLevel,
                        BlockFaceSet.MASK_NORTH, x, y, z + 1);
            }
        }

        // Above and below, may need to check cube boundaries
        // Below
        if (!selfOpaqueFaces.down()) {
            lightLevel = section.getLightIfHigherNeighbor(category, lightLevel,
                    BlockFaceSet.MASK_UP, x, y - 1, z);
        }

        // Above
        if (!selfOpaqueFaces.up()) {
            lightLevel = section.getLightIfHigherNeighbor(category, lightLevel,
                    BlockFaceSet.MASK_DOWN, x, y + 1, z);
        }

        return lightLevel;
    }

    /**
     * Gets whether this lighting chunk has faults that need to be fixed
     *
     * @return True if there are faults, False if not
     */
    public boolean hasFaults() {
        return isSkyLightDirty || isBlockLightDirty;
    }

    public void forceSpreadBlocks()
    {
        spread(LightingCategory.BLOCK);
    }

    /**
     * Spreads the light from sources to 'zero' light level blocks
     *
     * @return Number of processing loops executed. 0 indicates no faults were found.
     */
    public int spread() {
        if (hasFaults()) {
            int count = 0;
            if (isSkyLightDirty) {
                count += spread(LightingCategory.SKY);
            }
            if (isBlockLightDirty) {
                count += spread(LightingCategory.BLOCK);
            }
            return count;
        } else {
            return 0;
        }
    }

    private int spread(LightingCategory category) {
        if ((category == LightingCategory.SKY) && !hasSkyLight) {
            this.isSkyLightDirty = false;
            return 0;
        }

        int x, y, z, light, factor, startY, newlight;
        int loops = 0;
        int lasterrx = 0, lasterry = 0, lasterrz = 0;
        boolean haserror;

        boolean err_neigh_nx = false;
        boolean err_neigh_px = false;
        boolean err_neigh_nz = false;
        boolean err_neigh_pz = false;

        LightingCube cube = null;
        // Keep spreading the light in this chunk until it is done
        boolean mode = false;
        IntVector2 loop_start, loop_end;
        int loop_increment;
        while (true) {
            haserror = false;

            // Alternate iterating positive and negative
            // This allows proper optimized spreading in all directions
            mode = !mode;
            if (mode) {
                loop_start = start;
                loop_end = end.add(1, 1);
                loop_increment = 1;
            } else {
                loop_start = end;
                loop_end = start.subtract(1, 1);
                loop_increment = -1;
            }

            // Go through all blocks, using the heightmap for sky light to skip a few
            for (x = loop_start.x; x != loop_end.x; x += loop_increment) {
                for (z = loop_start.z; z != loop_end.z; z += loop_increment) {
                    startY = category.getStartY(this, x, z);
                    for (y = startY; y >= this.minY; y--) {
                        if ((cube = nextCube(cube, y)) == null) {
                            // Skip this section entirely by setting y to the bottom of the section
                            y &= ~0xf;
                            continue;
                        }

                        // Take block opacity into account, skip if fully solid
                        factor = Math.max(1, cube.opacity.get(x, y & 0xf, z));
                        if (factor == 15) {
                            continue;
                        }

                        // Read the old light level and try to find a light level around it that exceeds
                        light = category.get(cube, x, y & 0xf, z);
                        newlight = light + factor;
                        if (newlight < 15) {
                            newlight = getMaxLightLevel(cube, category, newlight, x, y & 0xf, z);
                        }
                        newlight -= factor;

                        // pick the highest value
                        if (newlight > light) {
                            category.set(cube, x, y & 0xf, z, newlight);
                            lasterrx = x;
                            lasterry = y;
                            lasterrz = z;
                            err_neigh_nx |= (x == 0);
                            err_neigh_nz |= (z == 0);
                            err_neigh_px |= (x == 15);
                            err_neigh_pz |= (z == 15);
                            haserror = true;
                        }
                    }
                }
            }

            if (!haserror) {
                break;
            } else if (++loops > 100) {
                lasterrx += this.chunkX << 4;
                lasterrz += this.chunkZ << 4;
                StringBuilder msg = new StringBuilder();
                msg.append("Failed to fix all " + category.getName() + " lighting at [");
                msg.append(lasterrx).append('/').append(lasterry);
                msg.append('/').append(lasterrz).append(']');
                Iris.warn(msg.toString());
                break;
            }
        }

        // Set self as no longer dirty, all light is good
        category.setDirty(this, false);

        // When we change blocks at our chunk borders, neighbours have to do another spread cycle
        if (err_neigh_nx) setNeighbourDirty(-1, 0, category);
        if (err_neigh_px) setNeighbourDirty(1, 0, category);
        if (err_neigh_nz) setNeighbourDirty(0, -1, category);
        if (err_neigh_pz) setNeighbourDirty(0, 1, category);

        return loops;
    }

    private void setNeighbourDirty(int dx, int dz, LightingCategory category) {
        LightingChunk n = neighbors.get(dx, dz);
        if (n != null) {
            category.setDirty(n, true);
        }
    }

    /**
     * Applies the lighting information to a chunk. The returned completable future is called
     * on the main thread when saving finishes.
     *
     * @param chunk to save to
     * @return completable future completed when the chunk is saved,
     *         with value True passed when saving occurred, False otherwise
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> saveToChunk(Chunk chunk) {
        // Create futures for saving to all the chunk sections in parallel
        List<LightingCube> sectionsToSave = this.sections.values();
        final CompletableFuture<Boolean>[] futures = new CompletableFuture[sectionsToSave.size()];
        {
            int futureIndex = 0;
            for (LightingCube sectionToSave : sectionsToSave) {
                ChunkSection sectionToWriteTo = WorldUtil.getSection(chunk, sectionToSave.cy);
                if (sectionToWriteTo == null) {
                    futures[futureIndex++] = CompletableFuture.completedFuture(Boolean.FALSE);
                } else {
                    futures[futureIndex++] = sectionToSave.saveToChunk(sectionToWriteTo);
                }
            }
        }

        // When all of them complete, combine them into a single future
        // If any changes were made to the chunk, return True as completed value
        return CompletableFuture.allOf(futures).thenApply((o) -> {
            isApplied = true;

            try {
                for (CompletableFuture<Boolean> future : futures) {
                    if (future.get().booleanValue()) {
                        ChunkHandle.fromBukkit(chunk).markDirty();
                        return Boolean.TRUE;
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

            // None of the futures completed true
            return Boolean.FALSE;
        });
    }
}
