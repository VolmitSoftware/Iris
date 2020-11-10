package com.volmit.iris.scaffold.lighting;

import java.util.Collection;
import java.util.Set;
import java.util.stream.IntStream;

import org.bukkit.Chunk;
import org.bukkit.World;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;

/**
 * A map of region information
 */
public class FlatRegionInfoMap {
    private final World _world;
    private final LongHashMap<FlatRegionInfo> _regions;

    private FlatRegionInfoMap(World world, LongHashMap<FlatRegionInfo> regions) {
        this._world = world;
        this._regions = regions;
    }

    public World getWorld() {
        return this._world;
    }

    public int getRegionCount() {
        return this._regions.size();
    }

    public Collection<FlatRegionInfo> getRegions() {
        return this._regions.getValues();
    }

    public FlatRegionInfo getRegion(int rx, int rz) {
        return this._regions.get(rx, rz);
    }

    public FlatRegionInfo getRegionAtChunk(int cx, int cz) {
        return this._regions.get(cx >> 5, cz >> 5);
    }

    /**
     * Gets whether a chunk exists
     * 
     * @param cx
     * @param cz
     * @return True if the chunk exists
     */
    public boolean containsChunk(int cx, int cz) {
        FlatRegionInfo region = getRegionAtChunk(cx, cz);
        return region != null && region.containsChunk(cx, cz);
    }

    /**
     * Gets whether a chunk, and all its 8 neighbours, exist
     * 
     * @param cx
     * @param cz
     * @return True if the chunk and all its neighbours exist
     */
    public boolean containsChunkAndNeighbours(int cx, int cz) {
        FlatRegionInfo region = getRegionAtChunk(cx, cz);
        if (region == null) {
            return false;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int mx = cx + dx;
                int mz = cz + dz;
                if (region.isInRange(mx, mz)) {
                    if (!region.containsChunk(mx, mz)) {
                        return false;
                    }
                } else {
                    if (!this.containsChunk(mx, mz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Computes all the region Y-coordinates used by a region and its neighbouring 8 regions.
     * The returned array is sorted in increasing order and is distinct (no duplicate values).
     * 
     * @param region
     * @return region and neighbouring regions' Y-coordinates
     */
    public int[] getRegionYCoordinatesSelfAndNeighbours(FlatRegionInfo region) {
        IntStream region_y_coord_stream = region.getRYStream();
        for (int drx = -1; drx <= 1; drx++) {
            for (int drz = -1; drz <= 1; drz++) {
                if (drx == 0 && drz == 0) {
                    continue;
                }

                FlatRegionInfo neigh_region = this.getRegion(region.rx + drx, region.rz + drz);
                if (neigh_region != null) {
                    region_y_coord_stream = IntStream.concat(region_y_coord_stream, neigh_region.getRYStream());
                }
            }
        }

        //TODO: There's technically a way to significantly speed up sorting two concatenated sorted streams
        //      Sadly, the java 8 SDK doesn't appear to do any optimizations here :(
        return region_y_coord_stream.sorted().distinct().toArray();
    }

    /**
     * Creates a region information mapping of all existing chunks of a world
     * that are currently loaded. No further loading is required.
     * 
     * @param world
     * @return region info map
     */
    public static FlatRegionInfoMap createLoaded(World world) {
        LongHashMap<FlatRegionInfo> regions = new LongHashMap<FlatRegionInfo>();
        for (Chunk chunk : world.getLoadedChunks()) {
            int rx = WorldUtil.chunkToRegionIndex(chunk.getX());
            int rz = WorldUtil.chunkToRegionIndex(chunk.getZ());
            FlatRegionInfo prev_info = regions.get(rx, rz);
            FlatRegionInfo new_info = prev_info;
            if (new_info == null) {
                new_info = new FlatRegionInfo(world, rx, 0, rz);
                new_info.ignoreLoad();
            }

            // Refresh y-coordinates
            for (Integer y_coord : WorldUtil.getLoadedSectionCoordinates(chunk)) {
                new_info = new_info.addRegionYCoordinate(WorldUtil.chunkToRegionIndex(y_coord.intValue()));
            }

            // Add chunk to region bitset
            new_info.addChunk(chunk.getX(), chunk.getZ());

            // Store if new or changed
            if (new_info != prev_info) {
                regions.put(rx, rz, new_info);
            }
        }

        return new FlatRegionInfoMap(world, regions);
    }

    /**
     * Creates a region information mapping of all existing chunks of a world
     * 
     * @param world
     * @return region info map
     */
    public static FlatRegionInfoMap create(World world) {
        LongHashMap<FlatRegionInfo> regions = new LongHashMap<FlatRegionInfo>();

        // Obtain the region coordinates in 3d space (vertical too!)
        Set<IntVector3> regionCoordinates = WorldUtil.getWorldRegions3(world);

        // For each region, create a RegionInfo entry
        for (IntVector3 region : regionCoordinates) {
            long key = MathUtil.longHashToLong(region.x, region.z);
            FlatRegionInfo prev = regions.get(key);
            if (prev != null) {
                regions.put(key, prev.addRegionYCoordinate(region.y));
            } else {
                regions.put(key, new FlatRegionInfo(world, region.x, region.y, region.z));
            }
        }

        // For all loaded chunks, add those chunks to their region up-front
        // They may not yet have been saved to the region file
        for (Chunk chunk : world.getLoadedChunks()) {
            int rx = WorldUtil.chunkToRegionIndex(chunk.getX());
            int rz = WorldUtil.chunkToRegionIndex(chunk.getZ());
            FlatRegionInfo info = regions.get(rx, rz);
            if (info != null) {
                info.addChunk(chunk.getX(), chunk.getZ());
            }
        }

        return new FlatRegionInfoMap(world, regions);
    }
}
