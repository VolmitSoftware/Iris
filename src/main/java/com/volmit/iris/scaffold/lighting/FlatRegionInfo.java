package com.volmit.iris.scaffold.lighting;

import java.util.Arrays;
import java.util.BitSet;
import java.util.stream.IntStream;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.utils.WorldUtil;

/**
 * Loads region information, storing whether or not
 * the 32x32 (1024) chunks are available.
 */
public class FlatRegionInfo {
    private static final int[] DEFAULT_RY_0 = new int[] {0}; // Optimization
    public final World world;
    public final int rx, rz;
    public final int[] ry;
    public final int cx, cz;
    private final BitSet _chunks;
    private boolean _loadedFromDisk;

    public FlatRegionInfo(World world, int rx, int ry, int rz) {
        this(world, rx, (ry==0) ? DEFAULT_RY_0 : new int[] {ry}, rz);
    }

    public FlatRegionInfo(World world, int rx, int[] ry, int rz) {
        this.world = world;
        this.rx = rx;
        this.rz = rz;
        this.ry = ry;
        this.cx = (rx << 5);
        this.cz = (rz << 5);
        this._chunks = new BitSet(1024);
        this._loadedFromDisk = false;
    }

    private FlatRegionInfo(FlatRegionInfo copy, int[] new_ry) {
        this.world = copy.world;
        this.rx = copy.rx;
        this.ry = new_ry;
        this.rz = copy.rz;
        this.cx = copy.cx;
        this.cz = copy.cz;
        this._chunks = copy._chunks;
        this._loadedFromDisk = copy._loadedFromDisk;
    }

    public void addChunk(int cx, int cz) {
        cx -= this.cx;
        cz -= this.cz;
        if (cx < 0 || cx >= 32 || cz < 0 || cz >= 32) {
            return;
        }
        this._chunks.set((cz << 5) | cx);
    }

    /**
     * Gets the number of chunks in this region.
     * If not loaded yet, the default 1024 is returned.
     * 
     * @return chunk count
     */
    public int getChunkCount() {
        return this._chunks.cardinality();
    }

    /**
     * Gets the region Y-coordinates as a sorted, immutable distinct stream
     * 
     * @return ry int stream
     */
    public IntStream getRYStream() {
        return IntStream.of(this.ry);
    }

    /**
     * Loads the region information, now telling what chunks are contained
     */
    public void load() {
        if (!this._loadedFromDisk) {
            this._loadedFromDisk = true;
            for (int ry : this.ry) {
                this._chunks.or(WorldUtil.getWorldSavedRegionChunks3(this.world, this.rx, ry, this.rz));
            }
        }
    }

    /**
     * Ignores loading region chunk information from chunks that aren't loaded
     */
    public void ignoreLoad() {
        this._loadedFromDisk = true;
    }

    /**
     * Gets whether the chunk coordinates specified are within the range
     * of coordinates of this region
     * 
     * @param cx - chunk coordinates (world coordinates)
     * @param cz - chunk coordinates (world coordinates)
     * @return True if in range
     */
    public boolean isInRange(int cx, int cz) {
        cx -= this.cx;
        cz -= this.cz;
        return cx >= 0 && cz >= 0 && cx < 32 && cz < 32;
    }

    /**
     * Gets whether a chunk is contained and exists inside this region
     * 
     * @param cx - chunk coordinates (world coordinates)
     * @param cz - chunk coordinates (world coordinates)
     * @return True if the chunk is contained
     */
    public boolean containsChunk(int cx, int cz) {
        cx -= this.cx;
        cz -= this.cz;
        if (cx < 0 || cx >= 32 || cz < 0 || cz >= 32) {
            return false;
        }

        // Load region file information the first time this is accessed
        this.load();

        // Check in bitset
        return this._chunks.get((cz << 5) | cx);
    }

    /**
     * Adds another Region Y-coordinate to the list.
     * The set of chunks and other properties are copied.
     * 
     * @param ry
     * @return new flat region info object with updated ry
     */
    public FlatRegionInfo addRegionYCoordinate(int ry) {
        int index = Arrays.binarySearch(this.ry, ry);
        if (index >= 0) {
            return this; // Already contained
        }

        // Insert at this index (undo insertion point - 1)
        index = -index - 1;
        int[] new_y_coordinates = new int[this.ry.length + 1];
        System.arraycopy(this.ry, 0, new_y_coordinates, 0, index);
        new_y_coordinates[index] = ry;
        System.arraycopy(this.ry, index, new_y_coordinates, index+1, this.ry.length - index);
        return new FlatRegionInfo(this, new_y_coordinates);
    }
}
