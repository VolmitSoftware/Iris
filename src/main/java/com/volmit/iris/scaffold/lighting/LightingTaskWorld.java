/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.scaffold.lighting;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.volmit.iris.Iris;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LightingTaskWorld implements LightingTask {
    private static final int ASSUMED_CHUNKS_PER_REGION = 34 * 34;
    private final World world;
    private volatile FlatRegionInfoMap regions = null;
    private volatile int regionCountLoaded;
    private volatile int chunkCount;
    private volatile long timeStarted;
    private volatile boolean aborted;
    private LightingService.ScheduleArguments options = new LightingService.ScheduleArguments();

    public LightingTaskWorld(World world) {
        this.world = world;
        this.regionCountLoaded = 0;
        this.aborted = false;
        this.chunkCount = 0;
        this.timeStarted = 0;
    }

    @Override
    public World getWorld() {
        return this.world;
    }

    @Override
    public int getChunkCount() {
        return chunkCount;
    }

    @Override
    public long getTimeStarted() {
        return this.timeStarted;
    }

    @Override
    public String getStatus() {
        if (regions == null) {
            return "Reading available regions from world " + getWorld().getName();
        } else {
            return "Reading available chunks from world " + getWorld().getName() + " (region " + (regionCountLoaded + 1) + "/" + regions.getRegionCount() + ")";
        }
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    @Override
    public void process() {
        // Load regions on the main thread
        // TODO: Can use main thread executor instead
        this.timeStarted = System.currentTimeMillis();
        final CompletableFuture<Void> regionsLoadedFuture = new CompletableFuture<>();
        CommonUtil.nextTick(() -> {
            try {
                if (this.options.getLoadedChunksOnly()) {
                    this.regions = FlatRegionInfoMap.createLoaded(this.getWorld());
                    this.regionCountLoaded = this.regions.getRegionCount();
                    this.chunkCount = 0;
                    for (FlatRegionInfo region : this.regions.getRegions()) {
                        this.chunkCount += region.getChunkCount();
                    }
                } else {
                    this.regions = FlatRegionInfoMap.create(this.getWorld());
                    this.regionCountLoaded = 0;
                    this.chunkCount = this.regions.getRegionCount() * ASSUMED_CHUNKS_PER_REGION;
                }
                regionsLoadedFuture.complete(null);
            } catch (Throwable ex) {
                Iris.reportError(ex);
                regionsLoadedFuture.completeExceptionally(ex);
            }
        });

        // Wait until region list is loaded synchronously
        try {
            regionsLoadedFuture.get();
        } catch (InterruptedException ex) {Iris.reportError(ex);
            // Ignore
        } catch (ExecutionException ex) {Iris.reportError(ex);
            throw new RuntimeException("Failed to load regions", ex.getCause());
        }

        // Check aborted
        if (this.aborted) {
            return;
        }

        // Start loading all chunks contained in the regions
        if (!this.options.getLoadedChunksOnly()) {
            for (FlatRegionInfo region : this.regions.getRegions()) {
                // Abort handling
                if (this.aborted) {
                    return;
                }

                // Load and update stats
                region.load();
                this.chunkCount -= ASSUMED_CHUNKS_PER_REGION - region.getChunkCount();
                this.regionCountLoaded++;
            }
        }

        // We now know of all the regions to be processed, convert all of them into tasks
        // Use a slightly larger area to avoid cross-region errors
        for (FlatRegionInfo region : regions.getRegions()) {
            // Abort handling
            if (this.aborted) {
                return;
            }

            // If empty, skip
            if (region.getChunkCount() == 0) {
                continue;
            }

            // Find region Y-coordinates for this 34x34 section of chunks
            int[] region_y_coordinates = regions.getRegionYCoordinatesSelfAndNeighbours(region);

            // Reduce count, schedule and clear the buffer
            // Put the coordinates that are available
            final LongHashSet buffer = new LongHashSet(34 * 34);
            if (true) {
                int dx, dz;
                for (dx = -1; dx < 33; dx++) {
                    for (dz = -1; dz < 33; dz++) {
                        int cx = region.cx + dx;
                        int cz = region.cz + dz;
                        if (this.regions.containsChunkAndNeighbours(cx, cz)) {
                            buffer.add(cx, cz);
                        }
                    }
                }
            } else {
                int dx, dz;
                for (dx = -1; dx < 33; dx++) {
                    for (dz = -1; dz < 33; dz++) {
                        int cx = region.cx + dx;
                        int cz = region.cz + dz;
                        if (this.regions.containsChunk(cx, cz)) {
                            buffer.add(cx, cz);
                        }
                    }
                }
            }

            // Schedule and return amount of chunks
            this.chunkCount -= buffer.size();
            LightingTaskBatch batch_task = new LightingTaskBatch(this.getWorld(), region_y_coordinates, buffer);
            batch_task.applyOptions(this.options);
            LightingService.schedule(batch_task);
        }
    }

    @Override
    public void abort() {
        this.aborted = true;
    }

    @Override
    public void applyOptions(LightingService.ScheduleArguments args) {
        this.options = args;
    }

    @Override
    public boolean canSave() {
        return false;
    }
}
