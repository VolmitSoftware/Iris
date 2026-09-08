package art.arcane.iris.engine.history;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class TransitionBoundarySampler {
    private static final int NEIGHBOUR_COUNT = 4;
    private static final int MAXIMUM_CACHED_CHUNKS = 512;
    private static final int MAXIMUM_CACHED_CANDIDATES = 32_768;

    private final int terrainWidth;
    private final int searchWidth;
    private final TerrainBoundarySignatureStore.Snapshot signatures;
    private final LinkedHashMap<Long, CandidateIndex> chunkCandidates;
    private final Map<Long, CompletableFuture<CandidateIndex>> pendingCandidates = new HashMap<>();
    private final ThreadLocal<ChunkCache> cache;
    private int cachedCandidates;
    private long candidateBuildCount;

    TransitionBoundarySampler(
            int terrainWidth,
            TerrainBoundarySignatureStore.Snapshot signatures
    ) {
        if (terrainWidth <= 0) {
            throw new IllegalArgumentException("Terrain transition width must be positive");
        }
        this.terrainWidth = terrainWidth;
        this.searchWidth = terrainWidth;
        this.signatures = Objects.requireNonNull(signatures, "Terrain boundary signatures");
        this.chunkCandidates = new LinkedHashMap<>(MAXIMUM_CACHED_CHUNKS, 0.75F, true);
        this.cache = ThreadLocal.withInitial(ChunkCache::new);
    }

    TransitionGenerationPlan.TerrainSample sample(int blockX, int blockZ) {
        ChunkCache chunkCache = chunkCache(blockX, blockZ);
        int localX = Math.floorMod(blockX, GenerationBoundary.CHUNK_SIZE);
        int localZ = Math.floorMod(blockZ, GenerationBoundary.CHUNK_SIZE);
        int index = localX * GenerationBoundary.CHUNK_SIZE + localZ;
        TransitionGenerationPlan.TerrainSample existing = chunkCache.samples[index];
        if (existing != null) {
            return existing;
        }
        TransitionGenerationPlan.TerrainSample sampled = sampleUncached(
                blockX,
                blockZ,
                chunkCache.candidates
        );
        chunkCache.samples[index] = sampled;
        return sampled;
    }

    BoundaryGeometryInfluence geometryAt(int blockX, int blockZ) {
        CandidateIndex candidates = chunkCache(blockX, blockZ).candidates;
        Nearest nearest = candidates.nearest(blockX, blockZ, square(terrainWidth));
        if (nearest.count == 0 || nearest.distancesSquared[0] >= square(terrainWidth)) {
            return BoundaryGeometryInfluence.none();
        }
        double distance = Math.sqrt(nearest.distancesSquared[0]);
        double weight = GenerationBlend.newEpochWeight(Math.max(0D, distance - 1D),
                Math.max(1, terrainWidth - 1));
        int count = nearest.distancesSquared[0] <= 1D ? 1 : nearest.count;
        double total = 0D;
        for (int index = 0; index < count; index++) {
            total += 1D / Math.max(1D, nearest.distancesSquared[index]);
        }
        ArrayList<BoundaryGeometryInfluence.Contribution> contributions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            contributions.add(new BoundaryGeometryInfluence.Contribution(
                    nearest.signatures[index].geometry(),
                    (1D / Math.max(1D, nearest.distancesSquared[index])) / total));
        }
        int openingDepth = Math.min(8, Math.max(1, terrainWidth / 4));
        double openingWeight = GenerationBlend.newEpochWeight(Math.max(0D, distance - openingDepth),
                Math.max(1, terrainWidth - openingDepth));
        return new BoundaryGeometryInfluence(weight, openingWeight, contributions);
    }

    boolean intersectsTerrainBand(int minimumX, int minimumZ, int maximumX, int maximumZ) {
        return signatures.intersectsTerrainBand(
                new TerrainBoundarySignatureStore.BlockBounds(minimumX, minimumZ, maximumX, maximumZ),
                terrainWidth
        );
    }

    long catalogProbeCount() {
        return signatures.catalogProbeCount();
    }

    long shardLoadCount() {
        return signatures.shardLoadCount();
    }

    synchronized long candidateBuildCount() {
        return candidateBuildCount;
    }

    private TransitionGenerationPlan.TerrainSample sampleUncached(
            int blockX,
            int blockZ,
            CandidateIndex candidates
    ) {
        Nearest nearest = candidates.nearest(blockX, blockZ, square(searchWidth));
        if (nearest.count == 0) {
            return TransitionGenerationPlan.TerrainSample.newTerrain(searchWidth);
        }
        double distance = Math.sqrt(nearest.distancesSquared[0]);
        double newEpochWeight = GenerationBlend.newEpochWeight(
                Math.min(distance, terrainWidth),
                terrainWidth
        );
        double hydrologyWeight = newEpochWeight;
        return new TransitionGenerationPlan.TerrainSample(
                Math.min(distance, searchWidth),
                newEpochWeight,
                hydrologyWeight,
                nearest.signatures[0],
                weightedHeight(nearest, true),
                weightedHeight(nearest, false),
                weightedUpperCeilingDepth(nearest)
        );
    }

    private CandidateIndex candidatesForChunk(int chunkX, int chunkZ) {
        long chunkKey = pack(chunkX, chunkZ);
        CompletableFuture<CandidateIndex> pending;
        boolean builder;
        synchronized (this) {
            CandidateIndex existing = chunkCandidates.get(chunkKey);
            if (existing != null) {
                return existing;
            }
            pending = pendingCandidates.get(chunkKey);
            builder = pending == null;
            if (builder) {
                pending = new CompletableFuture<>();
                pendingCandidates.put(chunkKey, pending);
            }
        }
        if (!builder) {
            return pending.join();
        }
        try {
            CandidateIndex built = CandidateIndex.build(signatures.nearestCandidatesForChunk(
                    chunkX, chunkZ, searchWidth));
            synchronized (this) {
                chunkCandidates.put(chunkKey, built);
                cachedCandidates += built.size;
                candidateBuildCount++;
                while (chunkCandidates.size() > MAXIMUM_CACHED_CHUNKS
                        || cachedCandidates > MAXIMUM_CACHED_CANDIDATES) {
                    Iterator<Map.Entry<Long, CandidateIndex>> entries = chunkCandidates.entrySet().iterator();
                    cachedCandidates -= entries.next().getValue().size;
                    entries.remove();
                }
            }
            pending.complete(built);
            return built;
        } catch (RuntimeException | Error failure) {
            pending.completeExceptionally(failure);
            throw failure;
        } finally {
            synchronized (this) {
                pendingCandidates.remove(chunkKey, pending);
            }
        }
    }

    private ChunkCache chunkCache(int blockX, int blockZ) {
        ChunkCache chunkCache = cache.get();
        int chunkX = Math.floorDiv(blockX, GenerationBoundary.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(blockZ, GenerationBoundary.CHUNK_SIZE);
        if (chunkCache.chunkX != chunkX || chunkCache.chunkZ != chunkZ) {
            chunkCache.reset(chunkX, chunkZ, candidatesForChunk(chunkX, chunkZ));
        }
        return chunkCache;
    }

    private static double weightedHeight(Nearest nearest, boolean surface) {
        if (nearest.distancesSquared[0] <= 1D) {
            return surface
                    ? nearest.signatures[0].surfaceHeight()
                    : nearest.signatures[0].oceanFloorHeight();
        }
        double weighted = 0D;
        double totalWeight = 0D;
        for (int index = 0; index < nearest.count; index++) {
            double weight = 1D / nearest.distancesSquared[index];
            int height = surface
                    ? nearest.signatures[index].surfaceHeight()
                    : nearest.signatures[index].oceanFloorHeight();
            weighted += height * weight;
            totalWeight += weight;
        }
        return weighted / totalWeight;
    }

    private static double weightedUpperCeilingDepth(Nearest nearest) {
        if (nearest.distancesSquared[0] <= 1D) {
            return nearest.signatures[0].upperCeilingDepth().orElse(0);
        }
        double weighted = 0D;
        double totalWeight = 0D;
        for (int index = 0; index < nearest.count; index++) {
            double weight = 1D / nearest.distancesSquared[index];
            weighted += nearest.signatures[index].upperCeilingDepth().orElse(0) * weight;
            totalWeight += weight;
        }
        return weighted / totalWeight;
    }

    private static long distanceToRange(long value, long minimum, long maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return value > maximum ? value - maximum : 0L;
    }

    private static double square(long value) {
        return (double) value * value;
    }

    private static long pack(int x, int z) {
        return ((long) x << Integer.SIZE) | (z & 0xffffffffL);
    }

    private static final class CandidateIndex {
        private static final CandidateIndex EMPTY = new CandidateIndex(null, 0);

        private final Node root;
        private final int size;

        private CandidateIndex(Node root, int size) {
            this.root = root;
            this.size = size;
        }

        private static CandidateIndex build(List<TerrainBoundarySignature> candidates) {
            if (candidates.isEmpty()) {
                return EMPTY;
            }
            TerrainBoundarySignature[] values = candidates.toArray(new TerrainBoundarySignature[0]);
            return new CandidateIndex(build(values, 0, values.length, 0), values.length);
        }

        private Nearest nearest(int blockX, int blockZ, double maximumDistanceSquared) {
            Nearest nearest = new Nearest(maximumDistanceSquared);
            search(root, blockX, blockZ, nearest);
            return nearest;
        }

        private static Node build(
                TerrainBoundarySignature[] values,
                int from,
                int to,
                int depth
        ) {
            if (from >= to) {
                return null;
            }
            Comparator<TerrainBoundarySignature> comparator = depth % 2 == 0
                    ? Comparator.comparingInt(TerrainBoundarySignature::blockX)
                    .thenComparingInt(TerrainBoundarySignature::blockZ)
                    : Comparator.comparingInt(TerrainBoundarySignature::blockZ)
                    .thenComparingInt(TerrainBoundarySignature::blockX);
            Arrays.sort(values, from, to, comparator);
            int middle = from + (to - from) / 2;
            Node left = build(values, from, middle, depth + 1);
            Node right = build(values, middle + 1, to, depth + 1);
            return new Node(values[middle], left, right);
        }

        private static void search(Node node, int blockX, int blockZ, Nearest nearest) {
            if (node == null || node.distanceSquaredToBounds(blockX, blockZ) > nearest.limitSquared()) {
                return;
            }
            long deltaX = (long) node.signature.blockX() - blockX;
            long deltaZ = (long) node.signature.blockZ() - blockZ;
            nearest.offer(node.signature, square(deltaX) + square(deltaZ));

            Node first = node.left;
            Node second = node.right;
            if (first == null || second != null
                    && second.distanceSquaredToBounds(blockX, blockZ)
                    < first.distanceSquaredToBounds(blockX, blockZ)) {
                first = node.right;
                second = node.left;
            }
            search(first, blockX, blockZ, nearest);
            search(second, blockX, blockZ, nearest);
        }
    }

    private static final class Node {
        private final TerrainBoundarySignature signature;
        private final Node left;
        private final Node right;
        private final int minimumX;
        private final int maximumX;
        private final int minimumZ;
        private final int maximumZ;

        private Node(TerrainBoundarySignature signature, Node left, Node right) {
            this.signature = signature;
            this.left = left;
            this.right = right;
            minimumX = Math.min(signature.blockX(), Math.min(minimumX(left), minimumX(right)));
            maximumX = Math.max(signature.blockX(), Math.max(maximumX(left), maximumX(right)));
            minimumZ = Math.min(signature.blockZ(), Math.min(minimumZ(left), minimumZ(right)));
            maximumZ = Math.max(signature.blockZ(), Math.max(maximumZ(left), maximumZ(right)));
        }

        private double distanceSquaredToBounds(int blockX, int blockZ) {
            long distanceX = distanceToRange(blockX, minimumX, maximumX);
            long distanceZ = distanceToRange(blockZ, minimumZ, maximumZ);
            return square(distanceX) + square(distanceZ);
        }

        private static int minimumX(Node node) {
            return node == null ? Integer.MAX_VALUE : node.minimumX;
        }

        private static int maximumX(Node node) {
            return node == null ? Integer.MIN_VALUE : node.maximumX;
        }

        private static int minimumZ(Node node) {
            return node == null ? Integer.MAX_VALUE : node.minimumZ;
        }

        private static int maximumZ(Node node) {
            return node == null ? Integer.MIN_VALUE : node.maximumZ;
        }
    }

    private static final class Nearest {
        private final TerrainBoundarySignature[] signatures = new TerrainBoundarySignature[NEIGHBOUR_COUNT];
        private final double[] distancesSquared = new double[NEIGHBOUR_COUNT];
        private final double maximumDistanceSquared;
        private int count;

        private Nearest(double maximumDistanceSquared) {
            this.maximumDistanceSquared = maximumDistanceSquared;
        }

        private void offer(TerrainBoundarySignature signature, double distanceSquared) {
            if (distanceSquared > maximumDistanceSquared) {
                return;
            }
            int insertion = 0;
            while (insertion < count && compare(
                    distancesSquared[insertion],
                    signatures[insertion],
                    distanceSquared,
                    signature
            ) <= 0) {
                insertion++;
            }
            if (insertion >= NEIGHBOUR_COUNT) {
                return;
            }
            int copyLength = Math.min(count, NEIGHBOUR_COUNT - 1) - insertion;
            if (copyLength > 0) {
                System.arraycopy(signatures, insertion, signatures, insertion + 1, copyLength);
                System.arraycopy(distancesSquared, insertion, distancesSquared, insertion + 1, copyLength);
            }
            signatures[insertion] = signature;
            distancesSquared[insertion] = distanceSquared;
            count = Math.min(NEIGHBOUR_COUNT, count + 1);
        }

        private double limitSquared() {
            return count < NEIGHBOUR_COUNT
                    ? maximumDistanceSquared
                    : Math.min(maximumDistanceSquared, distancesSquared[NEIGHBOUR_COUNT - 1]);
        }

        private static int compare(
                double firstDistance,
                TerrainBoundarySignature first,
                double secondDistance,
                TerrainBoundarySignature second
        ) {
            int distanceComparison = Double.compare(firstDistance, secondDistance);
            if (distanceComparison != 0) {
                return distanceComparison;
            }
            int xComparison = Integer.compare(first.blockX(), second.blockX());
            return xComparison != 0
                    ? xComparison
                    : Integer.compare(first.blockZ(), second.blockZ());
        }
    }

    private static final class ChunkCache {
        private final TransitionGenerationPlan.TerrainSample[] samples =
                new TransitionGenerationPlan.TerrainSample[
                        GenerationBoundary.CHUNK_SIZE * GenerationBoundary.CHUNK_SIZE
                ];
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;
        private CandidateIndex candidates = CandidateIndex.EMPTY;

        private void reset(int nextChunkX, int nextChunkZ, CandidateIndex nextCandidates) {
            Arrays.fill(samples, null);
            chunkX = nextChunkX;
            chunkZ = nextChunkZ;
            candidates = nextCandidates;
        }
    }
}
