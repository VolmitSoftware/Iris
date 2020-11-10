package com.volmit.iris.scaffold.lighting;

import java.util.concurrent.CompletableFuture;

import com.bergerkiller.bukkit.common.collections.BlockFaceSet;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.ChunkSection;
import com.bergerkiller.generated.net.minecraft.server.NibbleArrayHandle;

/**
 * A single 16x16x16 cube of stored block information
 */
public class LightingCube {
    public static final int OOC = ~0xf; // Outside Of Cube
    public final LightingChunk owner;
    public final LightingCubeNeighboring neighbors = new LightingCubeNeighboring();
    public final int cy;
    public final NibbleArrayHandle skyLight;
    public final NibbleArrayHandle blockLight;
    public final NibbleArrayHandle emittedLight;
    public final NibbleArrayHandle opacity;
    private final BlockFaceSetSection opaqueFaces;

    public LightingCube(LightingChunk owner, ChunkSection chunkSection, boolean hasSkyLight) {
        this.owner = owner;
        this.cy = chunkSection.getY();

        if (owner.neighbors.hasAll()) {
            // Block light data (is re-initialized in the fill operation below, no need to read)
            this.blockLight = NibbleArrayHandle.createNew();

            // Sky light data (is re-initialized using heightmap operation later, no need to read)
            if (hasSkyLight) {
                this.skyLight = NibbleArrayHandle.createNew();
            } else {
                this.skyLight = null;
            }
        } else {
            // We need to load the original light data, because we have a border that we do not update

            // Block light data
            byte[] blockLightData = WorldUtil.getSectionBlockLight(owner.world,
                    owner.chunkX, this.cy, owner.chunkZ);
            if (blockLightData != null) {
                this.blockLight = NibbleArrayHandle.createNew(blockLightData);
            } else {
                this.blockLight = NibbleArrayHandle.createNew();
            }

            // Sky light data
            if (hasSkyLight) {
                byte[] skyLightData = WorldUtil.getSectionSkyLight(owner.world,
                        owner.chunkX, this.cy, owner.chunkZ);
                if (skyLightData != null) {
                    this.skyLight = NibbleArrayHandle.createNew(skyLightData);
                } else {
                    this.skyLight = NibbleArrayHandle.createNew();
                }
            } else {
                this.skyLight = null;
            }
        }

        // World coordinates
        int worldX = owner.chunkX << 4;
        int worldY = chunkSection.getYPosition();
        int worldZ = owner.chunkZ << 4;

        // Fill opacity and initial block lighting values
        this.opacity = NibbleArrayHandle.createNew();
        this.emittedLight = NibbleArrayHandle.createNew();
        this.opaqueFaces = new BlockFaceSetSection();
        int x, y, z, opacity, blockEmission;
        BlockFaceSet opaqueFaces;
        BlockData info;
        for (z = owner.start.z; z <= owner.end.z; z++) {
            for (x = owner.start.x; x <= owner.end.x; x++) {
                for (y = 0; y < 16; y++) {
                    info = chunkSection.getBlockData(x, y, z);
                    blockEmission = info.getEmission();
                    opacity = info.getOpacity(owner.world, worldX+x, worldY+y, worldZ+z);
                    if (opacity >= 0xf) {
                        opacity = 0xf;
                        opaqueFaces = BlockFaceSet.ALL;
                    } else {
                        if (opacity < 0) {
                            opacity = 0;
                        }
                        opaqueFaces = info.getOpaqueFaces(owner.world, worldX+x, worldY+y, worldZ+z);
                    }

                    this.opacity.set(x, y, z, opacity);
                    this.emittedLight.set(x, y, z, blockEmission);
                    this.blockLight.set(x, y, z, blockEmission);
                    this.opaqueFaces.set(x, y, z, opaqueFaces);
                }
            }
        }
    }

    /**
     * Gets the opaque faces of a block
     * 
     * @param x        - coordinate
     * @param y        - coordinate
     * @param z        - coordinate
     * @return opaque face set
     */
    public BlockFaceSet getOpaqueFaces(int x, int y, int z) {
        return this.opaqueFaces.get(x, y, z);
    }

    /**
     * Read light level of a neighboring block.
     * If possibly more, also check opaque faces, and then return the
     * higher light value if all these tests pass.
     * The x/y/z coordinates are allowed to check neighboring cubes.
     * 
     * @param category
     * @param old_light
     * @param faceMask
     * @param x The X-coordinate of the block (-1 to 16)
     * @param y The Y-coordinate of the block (-1 to 16)
     * @param z The Z-coordinate of the block (-1 to 16)
     * @return higher light level if propagated, otherwise the old light value
     */
    public int getLightIfHigherNeighbor(LightingCategory category, int old_light, int faceMask, int x, int y, int z) {
        if ((x & OOC | y & OOC | z & OOC) == 0) {
            return this.getLightIfHigher(category, old_light, faceMask, x, y, z);
        } else {
            LightingCube neigh = this.neighbors.get(x>>4, y>>4, z>>4);
            if (neigh != null) {
                return neigh.getLightIfHigher(category, old_light, faceMask, x & 0xf, y & 0xf, z & 0xf);
            } else {
                return old_light;
            }
        }
    }

    /**
     * Read light level of a neighboring block.
     * If possibly more, also check opaque faces, and then return the
     * higher light value if all these tests pass.
     * Requires the x/y/z coordinates to lay within this cube.
     * 
     * @param category Category of light to check
     * @param old_light Previous light value
     * @param faceMask The BlockFaceSet mask indicating the light-traveling direction
     * @param x The X-coordinate of the block (0 to 15)
     * @param y The Y-coordinate of the block (0 to 15)
     * @param z The Z-coordinate of the block (0 to 15)
     * @return higher light level if propagated, otherwise the old light value
     */
    public int getLightIfHigher(LightingCategory category, int old_light, int faceMask, int x, int y, int z) {
        int new_light_level = category.get(this, x, y, z);
        return (new_light_level > old_light && !this.getOpaqueFaces(x, y, z).get(faceMask))
                ? new_light_level : old_light;
    }

    /**
     * Called during initialization of block light to spread the light emitted by a block
     * to all neighboring blocks.
     * 
     * @param x The X-coordinate of the block (0 to 15)
     * @param y The Y-coordinate of the block (0 to 15)
     * @param z The Z-coordinate of the block (0 to 15)
     */
    public void spreadBlockLight(int x, int y, int z) {
        int emitted = this.emittedLight.get(x, y, z);
        if (emitted <= 1) {
            return; // Skip if neighbouring blocks won't receive light from it
        }
        if (x >= 1 && z >= 1 && x <= 14 && z <= 14) {
            trySpreadBlockLightWithin(emitted, BlockFaceSet.MASK_EAST,  x-1, y, z);
            trySpreadBlockLightWithin(emitted, BlockFaceSet.MASK_WEST,  x+1, y, z);
            trySpreadBlockLightWithin(emitted, BlockFaceSet.MASK_SOUTH, x, y, z-1);
            trySpreadBlockLightWithin(emitted, BlockFaceSet.MASK_NORTH, x, y, z+1);
        } else {
            trySpreadBlockLight(emitted, BlockFaceSet.MASK_EAST,  x-1, y, z);
            trySpreadBlockLight(emitted, BlockFaceSet.MASK_WEST,  x+1, y, z);
            trySpreadBlockLight(emitted, BlockFaceSet.MASK_SOUTH, x, y, z-1);
            trySpreadBlockLight(emitted, BlockFaceSet.MASK_NORTH, x, y, z+1);
        }
        if (y >= 1 && y <= 14) {
            trySpreadBlockLightWithin(emitted, BlockFaceSet.MASK_UP,    x, y-1, z);
            trySpreadBlockLightWithin(emitted, BlockFaceSet.MASK_DOWN,  x, y+1, z);
        } else {
            trySpreadBlockLight(emitted, BlockFaceSet.MASK_UP,   x, y-1, z);
            trySpreadBlockLight(emitted, BlockFaceSet.MASK_DOWN, x, y+1, z);
        }
    }

    /**
     * Tries to spread block light from an emitting block to one of the 6 sites.
     * The block being spread to is allowed to be outside of the bounds of this cube,
     * in which case neighboring cubes are spread to instead.
     * 
     * @param emitted The light that is emitted by the block
     * @param faceMask The BlockFaceSet mask indicating the light-traveling direction
     * @param x The X-coordinate of the block to spread to (-1 to 16)
     * @param y The Y-coordinate of the block to spread to (-1 to 16)
     * @param z The Z-coordinate of the block to spread to (-1 to 16)
     */
    public void trySpreadBlockLight(int emitted, int faceMask, int x, int y, int z) {
        if ((x & OOC | y & OOC | z & OOC) == 0) {
            this.trySpreadBlockLightWithin(emitted, faceMask, x, y, z);
        } else {
            LightingCube neigh = this.neighbors.get(x>>4, y>>4, z>>4);
            if (neigh != null) {
                neigh.trySpreadBlockLightWithin(emitted, faceMask, x & 0xf, y & 0xf, z & 0xf);
            }
        }
    }

    /**
     * Tries to spread block light from an emitting block to one of the 6 sides.
     * Assumes that the block being spread to is within this cube.
     * 
     * @param emitted The light that is emitted by the block
     * @param faceMask The BlockFaceSet mask indicating the light-traveling direction
     * @param x The X-coordinate of the block to spread to (0 to 15)
     * @param y The Y-coordinate of the block to spread to (0 to 15)
     * @param z The Z-coordinate of the block to spread to (0 to 15)
     */
    public void trySpreadBlockLightWithin(int emitted, int faceMask, int x, int y, int z) {
        if (!this.getOpaqueFaces(x, y, z).get(faceMask)) {
            int new_level = emitted - Math.max(1,  this.opacity.get(x, y, z));
            if (new_level > this.blockLight.get(x, y, z)) {
                this.blockLight.set(x, y, z, new_level);
            }
        }
    }

    /**
     * Applies the lighting information to a chunk section
     *
     * @param chunkSection to save to
     * @return future completed when saving is finished. Future resolves to False if no changes occurred, True otherwise.
     */
    public CompletableFuture<Boolean> saveToChunk(ChunkSection chunkSection) {
        CompletableFuture<Void> blockLightFuture = null;
        CompletableFuture<Void> skyLightFuture = null;

        try {
            if (this.blockLight != null) {
                byte[] newBlockLight = this.blockLight.getData();
                byte[] oldBlockLight = WorldUtil.getSectionBlockLight(owner.world,
                        owner.chunkX, this.cy, owner.chunkZ);
                boolean blockLightChanged = false;
                if (oldBlockLight == null || newBlockLight.length != oldBlockLight.length) {
                    blockLightChanged = true;
                } else {
                    for (int i = 0; i < oldBlockLight.length; i++) {
                        if (oldBlockLight[i] != newBlockLight[i]) {
                            blockLightChanged = true;
                            break;
                        }
                    }
                }

                //TODO: Maybe do blockLightChanged check inside BKCommonLib?
                if (blockLightChanged) {
                    blockLightFuture = WorldUtil.setSectionBlockLightAsync(owner.world,
                            owner.chunkX, this.cy, owner.chunkZ,
                            newBlockLight);
                }
            }
            if (this.skyLight != null) {
                byte[] newSkyLight = this.skyLight.getData();
                byte[] oldSkyLight = WorldUtil.getSectionSkyLight(owner.world,
                        owner.chunkX, this.cy, owner.chunkZ);
                boolean skyLightChanged = false;
                if (oldSkyLight == null || newSkyLight.length != oldSkyLight.length) {
                    skyLightChanged = true;
                } else {
                    for (int i = 0; i < oldSkyLight.length; i++) {
                        if (oldSkyLight[i] != newSkyLight[i]) {
                            skyLightChanged = true;
                            break;
                        }
                    }
                }

                //TODO: Maybe do skyLightChanged check inside BKCommonLib?
                if (skyLightChanged) {
                    skyLightFuture = WorldUtil.setSectionSkyLightAsync(owner.world,
                            owner.chunkX, this.cy, owner.chunkZ,
                            newSkyLight);
                }
            }
        } catch (Throwable t) {
            CompletableFuture<Boolean> exceptionally = new CompletableFuture<Boolean>();
            exceptionally.completeExceptionally(t);
            return exceptionally;
        }

        // No updates performed
        if (blockLightFuture == null && skyLightFuture == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        // Join both completable futures as one, if needed
        CompletableFuture<Void> combined;
        if (blockLightFuture == null) {
            combined = skyLightFuture;
        } else if (skyLightFuture == null) {
            combined = blockLightFuture;
        } else {
            combined = CompletableFuture.allOf(blockLightFuture, skyLightFuture);
        }

        // When combined resolves, return one that returns True
        return combined.thenApply((c) -> Boolean.TRUE);
    }

}
