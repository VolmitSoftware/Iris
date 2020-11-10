package com.volmit.iris.scaffold.lighting;

import com.bergerkiller.bukkit.common.AsyncTask;
import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet.LongIterator;
import com.volmit.iris.Iris;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class LightingService extends AsyncTask {
    private static final Set<RecipientWhenDone> recipientsForDone = new HashSet<RecipientWhenDone>();
    private static final LinkedList<LightingTask> tasks = new LinkedList<LightingTask>();
    private static final int PENDING_WRITE_INTERVAL = 10;
    private static AsyncTask fixThread = null;
    private static int taskChunkCount = 0;
    private static int taskCounter = 0;
    private static boolean pendingFileInUse = false;
    private static LightingTask currentTask;
    private static boolean paused = false;
    private static boolean lowOnMemory = false;

    /**
     * Gets whether this service is currently processing something
     *
     * @return True if processing, False if not
     */
    public static boolean isProcessing() {
        return fixThread != null;
    }

    /**
     * Starts or stops the processing service.
     * Stopping the service does not instantly abort, the current task is continued.
     *
     * @param process to abort
     */
    public static void setProcessing(boolean process) {
        if (process == isProcessing()) {
            return;
        }
        if (process) {
            fixThread = new LightingService().start(true);
        } else {
            // Fix thread is running, abort
            AsyncTask.stop(fixThread);
            fixThread = null;
        }
    }

    /**
     * Gets whether execution is paused, and pending tasks are not being processed
     * 
     * @return True if paused
     */
    public static boolean isPaused() {
        return paused;
    }

    /**
     * Sets whether execution is paused.
     * 
     * @param pause state to set to
     */
    public static void setPaused(boolean pause) {
        if (paused != pause) {
            paused = pause;
        }
    }

    /**
     * Gets the status of the currently processed task
     * 
     * @return current task status
     */
    public static String getCurrentStatus() {
        final LightingTask current = currentTask;
        if (lowOnMemory) {
            return ChatColor.RED + "Too low on available memory (paused)";
        } else if (current == null) {
            return "Finished.";
        } else {
            return current.getStatus();
        }
    }

    /**
     * Gets the time the currently processing task was started. If no task is being processed,
     * an empty result is returned. If processing didn't start yet, the value will be 0.
     * 
     * @return time when the current task was started
     */
    public static java.util.OptionalLong getCurrentStartTime() {
        final LightingTask current = currentTask;
        return (current == null) ? java.util.OptionalLong.empty() : OptionalLong.of(current.getTimeStarted());
    }

    public static void addRecipient(CommandSender sender) {
        synchronized (recipientsForDone) {
            recipientsForDone.add(new RecipientWhenDone(sender));
        }
    }

    public static void scheduleWorld(final World world) {
        ScheduleArguments args = new ScheduleArguments();
        args.setWorld(world);
        args.setEntireWorld();
        schedule(args);
    }

    /**
     * Schedules a square chunk area for lighting fixing
     *
     * @param world   the chunks are in
     * @param middleX
     * @param middleZ
     * @param radius
     */
    public static void scheduleArea(World world, int middleX, int middleZ, int radius) {
        ScheduleArguments args = new ScheduleArguments();
        args.setWorld(world);
        args.setChunksAround(middleX, middleZ, radius);
        schedule(args);
    }

    @Deprecated
    public static void schedule(World world, Collection<IntVector2> chunks) {
        ScheduleArguments args = new ScheduleArguments();
        args.setWorld(world);
        args.setChunks(chunks);
        schedule(args);
    }

    public static void schedule(World world, LongHashSet chunks) {
        ScheduleArguments args = new ScheduleArguments();
        args.setWorld(world);
        args.setChunks(chunks);
        schedule(args);
    }

    public static void schedule(ScheduleArguments args) {
        // World not allowed to be null
        if (args.getWorld() == null) {
            throw new IllegalArgumentException("Schedule arguments 'world' is null");
        }

        // If no chunks specified, entire world
        if (args.isEntireWorld()) {
            LightingTaskWorld task = new LightingTaskWorld(args.getWorld());
            task.applyOptions(args);
            schedule(task);
            return;
        }

        // If less than 34x34 chunks are requested, schedule as one task
        // In that case, be sure to only schedule chunks that actually exist
        // This prevents generating new chunks as part of this command
        LongHashSet chunks = args.getChunks();
        if (chunks.size() <= (34*34)) {

            LongHashSet chunks_filtered = new LongHashSet(chunks.size());
            Set<IntVector2> region_coords_filtered = new HashSet<IntVector2>();
            LongIterator iter = chunks.longIterator();

            if (args.getLoadedChunksOnly()) {
                // Remove coordinates of chunks that aren't loaded
                while (iter.hasNext()) {
                    long chunk = iter.next();
                    int cx = MathUtil.longHashMsw(chunk);
                    int cz = MathUtil.longHashLsw(chunk);
                    if (WorldUtil.isLoaded(args.getWorld(), cx, cz)) {
                        chunks_filtered.add(chunk);
                        region_coords_filtered.add(new IntVector2(
                                WorldUtil.chunkToRegionIndex(cx),
                                WorldUtil.chunkToRegionIndex(cz)));
                    }
                }
            } else if (true) {
                // Remove coordinates of chunks that don't actually exist (avoid generating new chunks)
                // isChunkAvailable isn't very fast, but fast enough below this threshold of chunks
                // To check for border chunks, we check that all 9 chunks are are available
                Map<IntVector2, Boolean> tmp = new HashMap<>();
                while (iter.hasNext()) {
                    long chunk = iter.next();
                    int cx = MathUtil.longHashMsw(chunk);
                    int cz = MathUtil.longHashLsw(chunk);

                    boolean fully_loaded = true;
                    for (int dx = -2; dx <= 2 && fully_loaded; dx++) {
                        for (int dz = -2; dz <= 2 && fully_loaded; dz++) {
                            IntVector2 pos = new IntVector2(cx + dx, cz + dz);
                            fully_loaded &= tmp.computeIfAbsent(pos, p -> WorldUtil.isChunkAvailable(args.getWorld(), p.x, p.z)).booleanValue();
                        }
                    }

                    if (fully_loaded) {
                        chunks_filtered.add(chunk);
                        region_coords_filtered.add(new IntVector2(
                                WorldUtil.chunkToRegionIndex(cx),
                                WorldUtil.chunkToRegionIndex(cz)));
                    }
                }
            } else {
                // Remove coordinates of chunks that don't actually exist (avoid generating new chunks)
                // isChunkAvailable isn't very fast, but fast enough below this threshold of chunks
                while (iter.hasNext()) {
                    long chunk = iter.next();
                    int cx = MathUtil.longHashMsw(chunk);
                    int cz = MathUtil.longHashLsw(chunk);
                    if (WorldUtil.isChunkAvailable(args.getWorld(), cx, cz)) {
                        chunks_filtered.add(chunk);
                        region_coords_filtered.add(new IntVector2(
                                WorldUtil.chunkToRegionIndex(cx),
                                WorldUtil.chunkToRegionIndex(cz)));
                    }
                }
            }

            // For all filtered chunk coordinates, compute regions
            int[] regionYCoordinates;
            {
                Set<IntVector3> regions = WorldUtil.getWorldRegions3ForXZ(args.getWorld(), region_coords_filtered);

                // Simplify to just the unique Y-coordinates
                regionYCoordinates = regions.stream().mapToInt(r -> r.y).sorted().distinct().toArray();
            }

            // Schedule it
            if (!chunks_filtered.isEmpty()) {
                LightingTaskBatch task = new LightingTaskBatch(args.getWorld(), regionYCoordinates, chunks_filtered);
                task.applyOptions(args);
                schedule(task);
            }
            return;
        }

        // Too many chunks requested. Separate the operations per region file with small overlap.
        FlatRegionInfoMap regions;
        if (args.getLoadedChunksOnly()) {
            regions = FlatRegionInfoMap.createLoaded(args.getWorld());
        } else {
            regions = FlatRegionInfoMap.create(args.getWorld());
        }

        LongIterator iter = chunks.longIterator();
        LongHashSet scheduledRegions = new LongHashSet();
        while (iter.hasNext()) {
            long first_chunk = iter.next();
            int first_chunk_x = MathUtil.longHashMsw(first_chunk);
            int first_chunk_z = MathUtil.longHashLsw(first_chunk);
            FlatRegionInfo region = regions.getRegionAtChunk(first_chunk_x, first_chunk_z);
            if (region == null || scheduledRegions.contains(region.rx, region.rz)) {
                continue; // Does not exist or already scheduled
            }
            if (!region.containsChunk(first_chunk_x, first_chunk_z)) {
                continue; // Chunk does not exist in world (not generated yet) or isn't loaded (loaded chunks only option)
            }

            // Collect all the region Y coordinates used for this region and the neighbouring regions
            // This makes sure we find all chunk slices we might need on an infinite height world
            int[] region_y_coordinates = regions.getRegionYCoordinatesSelfAndNeighbours(region);

            // Collect all chunks to process for this region.
            // This is an union of the 34x34 area of chunks and the region file data set
            LongHashSet buffer = new LongHashSet();
            int rdx, rdz;
            for (rdx = -1; rdx < 33; rdx++) {
                for (rdz = -1; rdz < 33; rdz++) {
                    int cx = region.cx + rdx;
                    int cz = region.cz + rdz;
                    long chunk_key = MathUtil.longHashToLong(cx, cz);
                    if (!chunks.contains(chunk_key)) {
                        continue;
                    }

                    if (true) {
                        // Check the chunk and the surrounding chunks are all present
                        if (!regions.containsChunkAndNeighbours(cx, cz)) {
                            continue;
                        }
                    } else {
                        // Only check chunk
                        if (!regions.containsChunk(cx, cz)) {
                            continue;
                        }
                    }
                    buffer.add(chunk_key);
                }
            }

            // Schedule the region
            if (!buffer.isEmpty()) {
                scheduledRegions.add(region.rx, region.rz);
                LightingTaskBatch task = new LightingTaskBatch(args.getWorld(), region_y_coordinates, buffer);
                task.applyOptions(args);
                schedule(task);
            }
        }
    }

    public static void schedule(LightingTask task) {
        synchronized (tasks) {
            tasks.offer(task);
            taskChunkCount += task.getChunkCount();
        }
        setProcessing(true);
    }

    /**
     * Loads the pending chunk batch operations from a save file.
     * If it is there, it will start processing these again.
     */
    public static void loadPendingBatches() {
        pendingFileInUse = false;
    }

    /**
     * Saves all pending chunk batch operations to a save file.
     * If the server, for whatever reason, crashes, it can restore using this file.
     */
    public static void savePendingBatches() {
        if (pendingFileInUse) {
            return;
        }
    }

    /**
     * Clears all pending tasks, does continue with the current tasks
     */
    public static void clearTasks() {
        synchronized (tasks) {
            tasks.clear();
        }
        final LightingTask current = currentTask;
        if (current != null) {
            current.abort();
        }
        synchronized (tasks) {
            tasks.clear();
        }
        currentTask = null;
        taskChunkCount = 0;
        LightingForcedChunkCache.reset();
    }

    /**
     * Orders this service to abort all tasks, finishing the current task in an orderly fashion.
     * This method can only be called from the main Thread.
     */
    public static void abort() {
        // Finish the current lighting task if available
        final LightingTask current = currentTask;
        final AsyncTask service = fixThread;
        if (service != null && current != null) {
            setProcessing(false);
            current.abort();
        }
        // Clear lighting tasks
        synchronized (tasks) {
            if (current != null) {
                tasks.addFirst(current);
            }
            if (!tasks.isEmpty()) {
            }
            savePendingBatches();
            clearTasks();
        }
    }

    /**
     * Gets the amount of chunks that are still faulty
     *
     * @return faulty chunk count
     */
    public static int getChunkFaults() {
        final LightingTask current = currentTask;
        return taskChunkCount + (current == null ? 0 : current.getChunkCount());
    }

    @Override
    public void run() {
        // While paused, do nothing
        while (paused) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            synchronized (tasks) {
                if (tasks.isEmpty()) {
                    break; // Stop processing.
                }
            }
            if (fixThread.isStopRequested()) {
                return;
            }
        }

        synchronized (tasks) {
            currentTask = tasks.poll();
        }
        if (currentTask == null) {
            // No more tasks, end this thread
            // Messages
            final String message = ChatColor.GREEN + "All lighting operations are completed.";
            synchronized (recipientsForDone) {
                for (RecipientWhenDone recipient : recipientsForDone) {
                    CommandSender recip = recipient.player_name == null ?
                            Bukkit.getConsoleSender() : Bukkit.getPlayer(recipient.player_name);
                    if (recip != null) {
                        String timeStr = LightingUtil.formatDuration(System.currentTimeMillis() - recipient.timeStarted);
                        recip.sendMessage(message + ChatColor.WHITE + " (Took " + timeStr + ")");
                    }
                }
                recipientsForDone.clear();
            }
            // Stop task and abort
            taskCounter = 0;
            setProcessing(false);
            LightingForcedChunkCache.reset();
            savePendingBatches();
            return;
        } else {
            // Write to file?
            if (taskCounter++ >= PENDING_WRITE_INTERVAL) {
                taskCounter = 0;
                // Start saving on another thread (IO access is slow...)
                new AsyncTask() {
                    public void run() {
                        savePendingBatches();
                    }
                }.start();

                // Save the world of the current task being processed

            }
            // Subtract task from the task count
            taskChunkCount -= currentTask.getChunkCount();
            // Process the task
            try {
                currentTask.process();
            } catch (Throwable t) {
                t.printStackTrace();
                Iris.error("Failed to process task: " + currentTask.getStatus());
            }
        }
    }

    private static long calcAvailableMemory(Runtime runtime) {
        long max = runtime.maxMemory();
        if (max == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        } else {
            long used = (runtime.totalMemory() - runtime.freeMemory());
            return (max - used);
        }
    }

    public static class ScheduleArguments {
        private World world;
        private String worldName;
        private LongHashSet chunks;
        private boolean debugMakeCorrupted = false;
        private boolean loadedChunksOnly = false;
        private int radius = Bukkit.getServer().getViewDistance();

        public boolean getDebugMakeCorrupted() {
            return this.debugMakeCorrupted;
        }

        public boolean getLoadedChunksOnly() {
            return this.loadedChunksOnly;
        }

        public int getRadius() {
            return this.radius;
        }

        public boolean isEntireWorld() {
            return this.chunks == null;
        }

        public World getWorld() {
            return this.world;
        }

        public String getWorldName() {
            return this.worldName;
        }

        public LongHashSet getChunks() {
            return this.chunks;
        }

        /**
         * Sets the world itself. Automatically updates the world name.
         * 
         * @param world
         * @return these arguments
         */
        public ScheduleArguments setWorld(World world) {
            this.world = world;
            this.worldName = world.getName();
            return this;
        }

        /**
         * Sets the world name to perform operations on.
         * If the world by this name does not exist, the world is null.
         * 
         * @param worldName
         * @return these arguments
         */
        public ScheduleArguments setWorldName(String worldName) {
            this.world = Bukkit.getWorld(worldName);
            this.worldName = worldName;
            return this;
        }

        public ScheduleArguments setEntireWorld() {
            this.chunks = null;
            return this;
        }

        public ScheduleArguments setDebugMakeCorrupted(boolean debug) {
            this.debugMakeCorrupted = debug;
            return this;
        }

        public ScheduleArguments setLoadedChunksOnly(boolean loadedChunksOnly) {
            this.loadedChunksOnly = loadedChunksOnly;
            return this;
        }

        public ScheduleArguments setRadius(int radius) {
            this.radius = radius;
            return this;
        }

        public ScheduleArguments setChunksAround(Location location, int radius) {
            this.setWorld(location.getWorld());
            return this.setChunksAround(location.getBlockX()>>4, location.getBlockZ()>>4, radius);
        }

        public ScheduleArguments setChunksAround(int middleX, int middleZ, int radius) {
            this.setRadius(radius);

            LongHashSet chunks_hashset = new LongHashSet((2*radius)*(2*radius));
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    int cx = middleX + a;
                    int cz = middleZ + b;
                    chunks_hashset.add(cx, cz);
                }
            }
            return this.setChunks(chunks_hashset);
        }

        /**
         * Sets the chunks to a cuboid area of chunks.
         * Make sure the minimum chunk coordinates are less or equal to
         * the maximum chunk coordinates.
         * 
         * @param minChunkX Minimum chunk x-coordinate (inclusive)
         * @param minChunkZ Minimum chunk z-coordinate (inclusive)
         * @param maxChunkX Maximum chunk x-coordinate (inclusive)
         * @param maxChunkZ Maximum chunk z-coordinate (inclusive)
         * @return this
         */
        public ScheduleArguments setChunkFromTo(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
            int num_dx = (maxChunkX - minChunkX) + 1;
            int num_dz = (maxChunkZ - minChunkZ) + 1;
            if (num_dx <= 0 || num_dz <= 0) {
                return this.setChunks(new LongHashSet()); // nothing
            }

            LongHashSet chunks_hashset = new LongHashSet(num_dx * num_dz);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks_hashset.add(chunkX, chunkZ);
                }
            }
            return this.setChunks(chunks_hashset);
        }

        public ScheduleArguments setChunks(Collection<IntVector2> chunks) {
            LongHashSet chunks_hashset = new LongHashSet(chunks.size());
            for (IntVector2 coord : chunks) {
                chunks_hashset.add(coord.x, coord.z);
            }
            return this.setChunks(chunks_hashset);
        }

        public ScheduleArguments setChunks(LongHashSet chunks) {
            this.chunks = chunks;
            return this;
        }

        private boolean checkRadiusPermission(CommandSender sender, int radius) throws NoPermissionException {
            return false;
        }

        /**
         * Parses the arguments specified in a command
         * 
         * @param sender
         * @return false if the input is incorrect and operations may not proceed
         * @throws NoPermissionException
         */
        public boolean handleCommandInput(CommandSender sender, String[] args) throws NoPermissionException {
            return true;
        }

        /**
         * Creates a new ScheduleArguments instance ready to be configured
         * 
         * @return args
         */
        public static ScheduleArguments create()
        {
            return new ScheduleArguments();
        }
    }

    private static class RecipientWhenDone {
        public final String player_name;
        public final long timeStarted;

        public RecipientWhenDone(CommandSender sender) {
            this.player_name = (sender instanceof Player) ? sender.getName() : null;
            this.timeStarted = System.currentTimeMillis();
        }
    }
}
