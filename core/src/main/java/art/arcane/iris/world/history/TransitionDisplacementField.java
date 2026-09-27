package art.arcane.iris.world.history;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class TransitionDisplacementField {
    private static final int MAXIMUM_CACHED_CHUNKS = 256;
    private static final int LOCAL_SUPPORT = 8;
    private static final Sample NONE = new Sample(0D, Double.MAX_VALUE, 1D, 0D, 0D, 0D, null);

    private final TransitionGenerationPlan plan;
    private final Sources sources;
    private final LinkedHashMap<Long, ChunkField> completed = new LinkedHashMap<>(64, 0.75F, true);
    private final ConcurrentHashMap<Long, CompletableFuture<ChunkField>> pending = new ConcurrentHashMap<>();
    private final ThreadLocal<ChunkField> local = new ThreadLocal<>();

    public TransitionDisplacementField(TransitionGenerationPlan plan, Sources sources) {
        this.plan = Objects.requireNonNull(plan);
        this.sources = Objects.requireNonNull(sources);
    }

    public double height(int x, int z, double nativeHeight) {
        double displacement = sample(x, z).displacement();
        return displacement == 0D ? nativeHeight : Math.clamp(nativeHeight + displacement, 0D, sources.height() - 1D);
    }

    public double fluidHeight(int x, int z, double nativeHead) {
        Sample sample = sample(x, z);
        if (sample.newTerrainWeight() == 1D) {
            return nativeHead;
        }
        double historicalHead = sample.wetWeight() * sample.wetHeight()
                + (1D - sample.wetWeight()) * Math.min(sample.dryHeight(), nativeHead);
        return GenerationBlend.interpolate(historicalHead, nativeHead, sample.newTerrainWeight());
    }

    public String fluidStateKey(int x, int z) {
        Sample sample = sample(x, z);
        return sample.newTerrainWeight() < 0.5D ? sample.fluidStateKey() : null;
    }

    public double seamWeight(int x, int z) {
        Sample sample = sample(x, z);
        return sample.newTerrainWeight() == 1D ? 1D
                : GenerationBlend.newEpochWeight(Math.max(0D, sample.distance() - 1D), 16);
    }

    public Sample sample(int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!plan.hasTransitionAtChunk(chunkX, chunkZ)) {
            return NONE;
        }
        long key = ChunkGenerationOwnership.packChunk(chunkX, chunkZ);
        ChunkField field = local.get();
        if (field == null || field.key() != key) {
            field = chunk(chunkX, chunkZ, key);
            local.set(field);
        }
        return field.samples()[(x & 15) * 16 + (z & 15)];
    }

    public void clear() {
        synchronized (completed) {
            completed.clear();
        }
        local.remove();
    }

    private ChunkField chunk(int chunkX, int chunkZ, long key) {
        synchronized (completed) {
            ChunkField cached = completed.get(key);
            if (cached != null) {
                return cached;
            }
        }
        CompletableFuture<ChunkField> result = new CompletableFuture<>();
        CompletableFuture<ChunkField> existing = pending.putIfAbsent(key, result);
        if (existing != null) {
            return existing.join();
        }
        try {
            synchronized (completed) {
                ChunkField cached = completed.get(key);
                if (cached != null) {
                    result.complete(cached);
                    return cached;
                }
            }
            ChunkField field = build(chunkX, chunkZ, key);
            synchronized (completed) {
                completed.put(key, field);
                while (completed.size() > MAXIMUM_CACHED_CHUNKS) {
                    completed.pollFirstEntry();
                }
            }
            result.complete(field);
            return field;
        } catch (RuntimeException | Error failure) {
            result.completeExceptionally(failure);
            throw failure;
        } finally {
            pending.remove(key, result);
        }
    }

    private ChunkField build(int chunkX, int chunkZ, long key) {
        int minimumX = Math.multiplyExact(chunkX, 16);
        int minimumZ = Math.multiplyExact(chunkZ, 16);
        Accumulator[] columns = new Accumulator[256];
        double maximumSupport = 0D;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                TransitionGenerationPlan.TerrainSample terrain = plan.terrainSampleAt(minimumX + x, minimumZ + z);
                double distance = terrain.distanceToHistoricalTerrain();
                double support = Math.min(plan.widthBlocks(), distance + LOCAL_SUPPORT);
                double weight = terrain.hasHistoricalSignature()
                        ? GenerationBlend.newEpochWeight(Math.max(0D, distance - 1D), Math.max(1, plan.widthBlocks() - 1))
                        : 1D;
                columns[x * 16 + z] = new Accumulator(minimumX + x, minimumZ + z, distance, support, weight);
                if (weight < 1D) {
                    maximumSupport = Math.max(maximumSupport, support);
                }
            }
        }
        long radius = (long) Math.ceil(maximumSupport);
        plan.terrainSignatures().forEachSignature(new TerrainBoundarySignatureStore.BlockBounds(
                minimumX - radius, minimumZ - radius, minimumX + 15L + radius, minimumZ + 15L + radius),
                signature -> accumulate(columns, signature));
        Sample[] samples = new Sample[256];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = columns[index].finish();
        }
        return new ChunkField(key, samples);
    }

    private void accumulate(Accumulator[] columns, TerrainBoundarySignature signature) {
        double residual = Double.NaN;
        String fluidKey = null;
        boolean wet = signature.fluidHeight().isPresent();
        for (Accumulator column : columns) {
            double weight = column.weight(signature.blockX(), signature.blockZ());
            if (weight == 0D) {
                continue;
            }
            if (Double.isNaN(residual)) {
                double nativeHeight = sources.heights().height(signature.blockX(), signature.blockZ());
                if (!Double.isFinite(nativeHeight)) {
                    throw new IllegalStateException("Transition anchor has a nonfinite native height");
                }
                residual = signature.oceanFloorHeight() - nativeHeight;
                if (wet) {
                    int y = signature.geometry().minimumY() + signature.fluidHeight().getAsInt();
                    fluidKey = signature.geometry().voxelAt(y).fluidStateKey();
                }
            }
            column.total += weight;
            column.displacement += weight * residual;
            if (wet) {
                column.wetTotal += weight;
                column.wetHeight += weight * signature.fluidHeight().getAsInt();
                column.minimumWetHeight = Math.min(column.minimumWetHeight, signature.fluidHeight().getAsInt());
                column.maximumWetHeight = Math.max(column.maximumWetHeight, signature.fluidHeight().getAsInt());
                if (weight > column.fluidKeyWeight) {
                    column.fluidKeyWeight = weight;
                    column.fluidKey = fluidKey;
                }
            } else {
                column.dryHeight += weight * signature.oceanFloorHeight();
                column.minimumDryHeight = Math.min(column.minimumDryHeight, signature.oceanFloorHeight());
                column.maximumDryHeight = Math.max(column.maximumDryHeight, signature.oceanFloorHeight());
            }
        }
    }

    public record Sources(HeightSource heights, int height) {
        public Sources {
            Objects.requireNonNull(heights);
            if (height < 1) {
                throw new IllegalArgumentException("Transition world height must be positive");
            }
        }
    }

    @FunctionalInterface
    public interface HeightSource {
        double height(int x, int z);
    }

    public record Sample(double displacement, double distance, double newTerrainWeight, double wetWeight,
                         double wetHeight, double dryHeight, String fluidStateKey) {
    }

    private record ChunkField(long key, Sample[] samples) {
    }

    private static final class Accumulator {
        private final int x;
        private final int z;
        private final double distance;
        private final double support;
        private final double newWeight;
        private double total;
        private double displacement;
        private double wetTotal;
        private double wetHeight;
        private double dryHeight;
        private double minimumWetHeight = Double.POSITIVE_INFINITY;
        private double maximumWetHeight = Double.NEGATIVE_INFINITY;
        private double minimumDryHeight = Double.POSITIVE_INFINITY;
        private double maximumDryHeight = Double.NEGATIVE_INFINITY;
        private double fluidKeyWeight;
        private String fluidKey;

        private Accumulator(int x, int z, double distance, double support, double newWeight) {
            this.x = x;
            this.z = z;
            this.distance = distance;
            this.support = support;
            this.newWeight = newWeight;
        }

        private double weight(int anchorX, int anchorZ) {
            if (newWeight == 1D) {
                return 0D;
            }
            double dx = (double) anchorX - x;
            double dz = (double) anchorZ - z;
            double squared = dx * dx + dz * dz;
            if (squared >= support * support) {
                return 0D;
            }
            double q = Math.sqrt(squared) / support;
            double remainder = 1D - q;
            return remainder * remainder * remainder * remainder * (1D + 4D * q)
                    / Math.max(1D, squared * squared);
        }

        private Sample finish() {
            if (total == 0D) {
                return NONE;
            }
            double dryTotal = total - wetTotal;
            return new Sample((1D - newWeight) * displacement / total, distance, newWeight,
                    Math.clamp(wetTotal / total, 0D, 1D), wetTotal == 0D ? 0D
                    : Math.clamp(wetHeight / wetTotal, minimumWetHeight, maximumWetHeight),
                    dryTotal <= 0D ? 0D : Math.clamp(dryHeight / dryTotal, minimumDryHeight, maximumDryHeight), fluidKey);
        }
    }
}
