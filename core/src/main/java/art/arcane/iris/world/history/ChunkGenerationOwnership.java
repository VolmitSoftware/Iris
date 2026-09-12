package art.arcane.iris.world.history;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ChunkGenerationOwnership {
    private static final int MAXIMUM_CACHED_REGIONS = 64;

    private final Path directory;
    private final LinkedHashMap<Long, RegionGenerationOwnership> regions;
    private final Long2IntOpenHashMap regionAssignmentCounts;
    private final ReentrantReadWriteLock lock;
    private int explicitChunkCount;

    private ChunkGenerationOwnership(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        regions = new LinkedHashMap<>(MAXIMUM_CACHED_REGIONS, 0.75F, true);
        regionAssignmentCounts = new Long2IntOpenHashMap();
        regionAssignmentCounts.defaultReturnValue(-1);
        lock = new ReentrantReadWriteLock();
    }

    public static ChunkGenerationOwnership load(Path directory) throws IOException {
        ChunkGenerationOwnership ownership = new ChunkGenerationOwnership(directory);
        ownership.loadRegionCatalog();
        return ownership;
    }

    public long resolve(int chunkX, int chunkZ, long currentActivation) {
        requireActivation(currentActivation);
        lock.writeLock().lock();
        try {
            RegionGenerationOwnership region = loadExistingRegion(chunkX >> 5, chunkZ >> 5);
            return region == null ? currentActivation : region.resolve(chunkX, chunkZ, currentActivation);
        } catch (IOException error) {
            throw lookupFailure(chunkX, chunkZ, error);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isExplicitlyAssigned(int chunkX, int chunkZ) {
        lock.writeLock().lock();
        try {
            RegionGenerationOwnership region = loadExistingRegion(chunkX >> 5, chunkZ >> 5);
            return region != null && region.hasAssignment(chunkX, chunkZ);
        } catch (IOException error) {
            throw lookupFailure(chunkX, chunkZ, error);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean anyMatchingInSquare(int centerX, int centerZ, int radius, AssignmentPredicate predicate) {
        if (radius < 0) {
            throw new IllegalArgumentException("Ownership search radius cannot be negative.");
        }
        Objects.requireNonNull(predicate, "predicate");
        int minimumX = (int) Math.max(Integer.MIN_VALUE, (long) centerX - radius);
        int maximumX = (int) Math.min(Integer.MAX_VALUE, (long) centerX + radius);
        int minimumZ = (int) Math.max(Integer.MIN_VALUE, (long) centerZ - radius);
        int maximumZ = (int) Math.min(Integer.MAX_VALUE, (long) centerZ + radius);
        int minimumRegionX = minimumX >> 5;
        int maximumRegionX = maximumX >> 5;
        int minimumRegionZ = minimumZ >> 5;
        int maximumRegionZ = maximumZ >> 5;
        long regionCount = ((long) maximumRegionX - minimumRegionX + 1L)
                * ((long) maximumRegionZ - minimumRegionZ + 1L);
        lock.writeLock().lock();
        try {
            if (regionCount > regionAssignmentCounts.size()) {
                for (long key : regionAssignmentCounts.keySet()) {
                    int x = regionX(key);
                    int z = regionZ(key);
                    if (x >= minimumRegionX && x <= maximumRegionX
                            && z >= minimumRegionZ && z <= maximumRegionZ
                            && matchesRegion(x, z, minimumX, minimumZ, maximumX, maximumZ, predicate)) {
                        return true;
                    }
                }
                return false;
            }
            for (int x = minimumRegionX; x <= maximumRegionX; x++) {
                for (int z = minimumRegionZ; z <= maximumRegionZ; z++) {
                    if (matchesRegion(x, z, minimumX, minimumZ, maximumX, maximumZ, predicate)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to search generation ownership near chunk "
                    + centerX + "," + centerZ + ".", error);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long explicitActivation(int chunkX, int chunkZ) {
        lock.writeLock().lock();
        try {
            RegionGenerationOwnership region = loadExistingRegion(chunkX >> 5, chunkZ >> 5);
            if (region == null) {
                throw new IllegalStateException(
                        "Chunk " + chunkX + "," + chunkZ + " has no explicit generation activation"
                );
            }
            return region.explicitActivation(chunkX, chunkZ);
        } catch (IOException error) {
            throw lookupFailure(chunkX, chunkZ, error);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean assign(int chunkX, int chunkZ, long activation) throws IOException {
        requireActivation(activation);
        lock.writeLock().lock();
        try {
            RegionGenerationOwnership region = regionForAssignment(chunkX, chunkZ);
            boolean assigned = region.assign(chunkX, chunkZ, activation);
            if (assigned) {
                recordAssignment(region);
            }
            return assigned;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int assignAll(WorldChunkInventory inventory, long activation) throws IOException {
        requireActivation(activation);
        lock.writeLock().lock();
        try {
            validateAssignments(inventory, activation);
            int[] assigned = new int[1];
            inventory.forEach((chunkX, chunkZ) -> {
                RegionGenerationOwnership region = regionForAssignment(chunkX, chunkZ);
                if (region.assign(chunkX, chunkZ, activation)) {
                    recordAssignment(region);
                    assigned[0]++;
                }
            });
            return assigned[0];
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int assignUnassigned(WorldChunkInventory inventory, long activation) throws IOException {
        requireActivation(activation);
        lock.writeLock().lock();
        try {
            int[] assigned = new int[1];
            inventory.forEach((chunkX, chunkZ) -> {
                RegionGenerationOwnership existing = loadExistingRegion(chunkX >> 5, chunkZ >> 5);
                if (existing != null && existing.hasAssignment(chunkX, chunkZ)) {
                    return;
                }
                RegionGenerationOwnership region = regionForAssignment(chunkX, chunkZ);
                if (region.assign(chunkX, chunkZ, activation)) {
                    recordAssignment(region);
                    assigned[0]++;
                }
            });
            return assigned[0];
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int discardUnstoredClaims(WorldChunkInventory stored, Set<Long> activationIds) throws IOException {
        Objects.requireNonNull(stored, "stored chunks");
        Set<Long> selected = Set.copyOf(activationIds);
        for (long activation : selected) {
            requireActivation(activation);
        }
        lock.writeLock().lock();
        try {
            int removed = 0;
            for (long regionKey : orderedRegionKeys()) {
                RegionGenerationOwnership region = loadExistingRegion(regionX(regionKey), regionZ(regionKey));
                if (region == null) {
                    continue;
                }
                int count = region.discardUnstoredClaims(stored, selected);
                if (count > 0) {
                    region.persist(directory);
                    explicitChunkCount -= count;
                    regionAssignmentCounts.put(regionKey, region.assignmentCount());
                    removed += count;
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int persist() throws IOException {
        lock.writeLock().lock();
        try {
            requireSafeParent();
            int persisted = 0;
            for (RegionGenerationOwnership region : regions.values()) {
                if (region.persist(directory)) {
                    persisted++;
                }
            }
            return persisted;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int explicitChunkCount() {
        lock.readLock().lock();
        try {
            return explicitChunkCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void forEachAssignment(AssignmentConsumer consumer) throws IOException {
        lock.writeLock().lock();
        try {
            for (long regionKey : orderedRegionKeys()) {
                RegionGenerationOwnership region = loadExistingRegion(regionX(regionKey), regionZ(regionKey));
                if (region != null) {
                    region.forEachAssignment(consumer::accept);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long[] snapshotExplicitChunkKeys() {
        lock.writeLock().lock();
        try {
            long[] chunks = new long[explicitChunkCount];
            int[] cursor = new int[1];
            try {
                forEachAssignment((chunkX, chunkZ, activationId) -> chunks[cursor[0]++] = packChunk(chunkX, chunkZ));
            } catch (IOException error) {
                throw new IllegalStateException("Unable to materialize generation ownership", error);
            }
            if (cursor[0] != chunks.length) {
                throw new IllegalStateException(
                        "Generation ownership changed while creating a snapshot: expected "
                                + chunks.length + " chunks but found " + cursor[0]
                );
            }
            Arrays.sort(chunks);
            return chunks;
        } finally {
            lock.writeLock().unlock();
        }
    }

    int cachedRegionCount() {
        lock.readLock().lock();
        try {
            return regions.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    int regionCount() {
        lock.readLock().lock();
        try {
            return regionAssignmentCounts.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << Integer.SIZE) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int chunkX(long packedChunk) {
        return (int) (packedChunk >> Integer.SIZE);
    }

    public static int chunkZ(long packedChunk) {
        return (int) packedChunk;
    }

    private void loadRegionCatalog() throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(directory)) {
            Path parent = directory.getParent();
            if (parent != null
                    && !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(parent)) {
                return;
            }
        }
        requireSafeParent();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation ownership path is not a directory: " + directory);
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                directory,
                "*" + RegionGenerationOwnership.FILE_SUFFIX
        )) {
            for (Path file : stream) {
                files.add(file);
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (Path file : files) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Generation ownership shard is not a regular file: " + file);
            }
            RegionGenerationOwnership.Metadata metadata = RegionGenerationOwnership.readMetadata(file);
            String expectedName = RegionGenerationOwnership.fileName(metadata.regionX(), metadata.regionZ());
            if (!file.getFileName().toString().equals(expectedName)) {
                throw new IOException("Generation ownership shard name does not match its coordinates: " + file);
            }
            long regionKey = packRegion(metadata.regionX(), metadata.regionZ());
            if (regionAssignmentCounts.putIfAbsent(regionKey, metadata.assignmentCount()) != -1) {
                throw new IOException("Duplicate generation ownership shard for region "
                        + metadata.regionX() + "," + metadata.regionZ());
            }
            try {
                explicitChunkCount = Math.addExact(explicitChunkCount, metadata.assignmentCount());
            } catch (ArithmeticException error) {
                throw new IOException("Generation ownership contains too many assigned chunks", error);
            }
        }
    }

    private void requireSafeParent() throws IOException {
        Path parent = directory.getParent();
        if (parent == null || !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation ownership parent directory is missing: " + parent);
        }
        BasicFileAttributes attributes = Files.readAttributes(
                parent,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Generation ownership parent path is not a safe directory: " + parent);
        }
    }

    private void validateAssignments(WorldChunkInventory inventory, long activation) throws IOException {
        inventory.forEach((chunkX, chunkZ) -> {
            RegionGenerationOwnership region = loadExistingRegion(chunkX >> 5, chunkZ >> 5);
            if (region == null || !region.hasAssignment(chunkX, chunkZ)) {
                return;
            }
            long existing = region.explicitActivation(chunkX, chunkZ);
            if (existing != activation) {
                throw new IllegalStateException(
                        "Chunk " + chunkX + "," + chunkZ + " already belongs to activation " + existing
                                + " and cannot be reassigned to " + activation
                );
            }
        });
    }

    private RegionGenerationOwnership regionForAssignment(int chunkX, int chunkZ) throws IOException {
        int regionX = chunkX >> 5;
        int regionZ = chunkZ >> 5;
        long regionKey = packRegion(regionX, regionZ);
        RegionGenerationOwnership region = regions.get(regionKey);
        if (region != null) {
            return region;
        }
        region = regionAssignmentCounts.containsKey(regionKey)
                ? readRegion(regionX, regionZ)
                : new RegionGenerationOwnership(regionX, regionZ);
        cacheRegion(regionKey, region);
        return region;
    }

    private RegionGenerationOwnership loadExistingRegion(int regionX, int regionZ) throws IOException {
        long regionKey = packRegion(regionX, regionZ);
        RegionGenerationOwnership region = regions.get(regionKey);
        if (region != null) {
            return region;
        }
        if (!regionAssignmentCounts.containsKey(regionKey)) {
            return null;
        }
        region = readRegion(regionX, regionZ);
        cacheRegion(regionKey, region);
        return region;
    }

    private RegionGenerationOwnership readRegion(int regionX, int regionZ) throws IOException {
        Path file = directory.resolve(RegionGenerationOwnership.fileName(regionX, regionZ));
        RegionGenerationOwnership region = RegionGenerationOwnership.read(file);
        if (region.regionX() != regionX || region.regionZ() != regionZ) {
            throw new IOException("Generation ownership shard name does not match its coordinates: " + file);
        }
        return region;
    }

    private boolean matchesRegion(int regionX, int regionZ, int minimumX, int minimumZ,
                                  int maximumX, int maximumZ, AssignmentPredicate predicate) throws IOException {
        RegionGenerationOwnership region = loadExistingRegion(regionX, regionZ);
        return region != null && region.anyMatchingInBounds(minimumX, minimumZ, maximumX, maximumZ, predicate);
    }

    private void cacheRegion(long regionKey, RegionGenerationOwnership region) throws IOException {
        regions.put(regionKey, region);
        while (regions.size() > MAXIMUM_CACHED_REGIONS) {
            Iterator<Map.Entry<Long, RegionGenerationOwnership>> entries = regions.entrySet().iterator();
            Map.Entry<Long, RegionGenerationOwnership> eldest = entries.next();
            RegionGenerationOwnership evicted = eldest.getValue();
            if (evicted.persist(directory)) {
                regionAssignmentCounts.put(eldest.getKey().longValue(), evicted.assignmentCount());
            }
            entries.remove();
        }
    }

    private void recordAssignment(RegionGenerationOwnership region) {
        explicitChunkCount++;
        regionAssignmentCounts.put(packRegion(region.regionX(), region.regionZ()), region.assignmentCount());
    }

    private List<Long> orderedRegionKeys() {
        ArrayList<Long> ordered = new ArrayList<>(regionAssignmentCounts.keySet());
        ordered.sort(ChunkGenerationOwnership::compareRegionKeys);
        return ordered;
    }

    private static IllegalStateException lookupFailure(int chunkX, int chunkZ, IOException error) {
        return new IllegalStateException(
                "Unable to load generation ownership for chunk " + chunkX + "," + chunkZ,
                error
        );
    }

    private static long packRegion(int regionX, int regionZ) {
        return ((long) regionX << Integer.SIZE) | (regionZ & 0xFFFFFFFFL);
    }

    private static int regionX(long regionKey) {
        return (int) (regionKey >> Integer.SIZE);
    }

    private static int regionZ(long regionKey) {
        return (int) regionKey;
    }

    private static int compareRegionKeys(long first, long second) {
        int xComparison = Integer.compare(regionX(first), regionX(second));
        return xComparison != 0 ? xComparison : Integer.compare(regionZ(first), regionZ(second));
    }

    private static void requireActivation(long activation) {
        if (activation <= 0L) {
            throw new IllegalArgumentException("Generation activation IDs must be positive: " + activation);
        }
    }

    @FunctionalInterface
    public interface AssignmentPredicate {
        boolean test(int chunkX, int chunkZ, long activationId);
    }

    @FunctionalInterface
    public interface AssignmentConsumer {
        void accept(int chunkX, int chunkZ, long activationId) throws IOException;
    }
}
