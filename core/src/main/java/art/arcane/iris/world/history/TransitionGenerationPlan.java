package art.arcane.iris.world.history;

import java.util.Objects;
import java.util.Optional;

public final class TransitionGenerationPlan {
    private final Specification specification;
    private final GenerationBoundary boundary;
    private final TerrainBoundarySignatureStore.Snapshot terrainSignatures;
    private final TransitionBoundarySampler terrainSampler;

    public TransitionGenerationPlan(
            Specification specification,
            GenerationBoundary boundary,
            TerrainBoundarySignatureStore.Snapshot terrainSignatures
    ) {
        this.specification = Objects.requireNonNull(specification, "Transition specification");
        this.boundary = Objects.requireNonNull(boundary, "Generation boundary");
        this.terrainSignatures = Objects.requireNonNull(terrainSignatures, "Terrain signatures");
        if (!specification.boundaryIdentity().equals(boundary.identity())) {
            throw new IllegalArgumentException("Transition boundary identity does not match the frozen boundary");
        }
        if (specification.activationId() != terrainSignatures.activationId()) {
            throw new IllegalArgumentException("Terrain signatures belong to a different activation");
        }
        if (!specification.terrainSignatureIdentity().equals(terrainSignatures.identity())) {
            throw new IllegalArgumentException("Transition terrain signature identity does not match the snapshot");
        }
        if (specification.algorithmVersion() != GenerationTransition.CURRENT_ALGORITHM_VERSION) {
            throw new IllegalArgumentException("Unsupported generation transition algorithm version "
                    + specification.algorithmVersion());
        }
        terrainSampler = new TransitionBoundarySampler(
                specification.widthBlocks(),
                terrainSignatures
        );
    }

    public long activationId() {
        return specification.activationId();
    }

    public String oldEpochId() {
        return specification.oldEpochId();
    }

    public String newEpochId() {
        return specification.newEpochId();
    }

    public int widthBlocks() {
        return specification.widthBlocks();
    }

    public int algorithmVersion() {
        return specification.algorithmVersion();
    }

    public String boundaryIdentity() {
        return specification.boundaryIdentity();
    }

    public Specification specification() {
        return specification;
    }

    public GenerationBoundary boundary() {
        return boundary;
    }

    public TerrainBoundarySignatureStore.Snapshot terrainSignatures() {
        return terrainSignatures;
    }

    public boolean isHistoricalBlock(int blockX, int blockZ) {
        return boundary.isHistoricalBlock(blockX, blockZ);
    }

    public double distanceToHistoricalChunks(int blockX, int blockZ) {
        return Math.min(terrainSampleAt(blockX, blockZ).distanceToHistoricalTerrain(), widthBlocks());
    }

    public double newEpochWeightAt(int blockX, int blockZ) {
        return terrainSampleAt(blockX, blockZ).newEpochWeight();
    }

    public double hydrologyWeightAt(int blockX, int blockZ) {
        return terrainSampleAt(blockX, blockZ).hydrologyWeight();
    }

    public boolean allowsNewDiscreteContentAt(int blockX, int blockZ) {
        return !boundary.isHistoricalBlock(blockX, blockZ);
    }

    public boolean hasTransitionAtChunk(int chunkX, int chunkZ) {
        if (boundary.isHistoricalChunk(chunkX, chunkZ)) {
            return false;
        }
        int minimumX = Math.multiplyExact(chunkX, GenerationBoundary.CHUNK_SIZE);
        int minimumZ = Math.multiplyExact(chunkZ, GenerationBoundary.CHUNK_SIZE);
        return terrainSampler.intersectsTerrainBand(minimumX, minimumZ,
                Math.addExact(minimumX, GenerationBoundary.CHUNK_SIZE - 1),
                Math.addExact(minimumZ, GenerationBoundary.CHUNK_SIZE - 1));
    }

    public BoundaryGeometryInfluence geometryAt(int blockX, int blockZ) {
        if (boundary.isHistoricalBlock(blockX, blockZ)) {
            return BoundaryGeometryInfluence.none();
        }
        return terrainSampler.geometryAt(blockX, blockZ);
    }

    public TerrainSample terrainSampleAt(int blockX, int blockZ) {
        if (boundary.isHistoricalBlock(blockX, blockZ)) {
            return TerrainSample.historicalTerrain();
        }
        return terrainSampler.sample(blockX, blockZ);
    }

    public Optional<String> historicalPhysicalBiomeKeyAt(int blockX, int blockY, int blockZ) {
        return historicalPhysicalBiomeKeyAt(blockX, blockY, blockZ, terrainSampleAt(blockX, blockZ));
    }

    public Optional<String> historicalPhysicalBiomeKeyAt(
            int blockX,
            int blockY,
            int blockZ,
            TerrainSample sample
    ) {
        Objects.requireNonNull(sample, "Terrain sample");
        if (sample.newEpochWeight() == 1D) {
            return Optional.empty();
        }
        double weight = GenerationBlend.newEpochWeight(
                Math.max(0D, sample.distanceToHistoricalTerrain() - 1D), Math.max(1, widthBlocks() - 1));
        return GenerationBlend.usesHistoricalMaterial(blockX, blockY, blockZ, weight)
                ? sample.historicalPhysicalBiomeKeyAt(blockY) : Optional.empty();
    }

    public boolean allowsNewFootprint(int minimumX, int minimumZ, int maximumX, int maximumZ) {
        if (minimumX > maximumX || minimumZ > maximumZ) {
            throw new IllegalArgumentException("Generation footprint bounds are inverted");
        }
        return !boundary.intersectsHistoricalBlocks(minimumX, minimumZ, maximumX, maximumZ);
    }

    long terrainCatalogProbeCount() {
        return terrainSampler.catalogProbeCount();
    }

    long terrainShardLoadCount() {
        return terrainSampler.shardLoadCount();
    }

    long terrainCandidateBuildCount() {
        return terrainSampler.candidateBuildCount();
    }

    public record Specification(
            long activationId,
            String oldEpochId,
            String newEpochId,
            int algorithmVersion,
            int widthBlocks,
            String boundaryIdentity,
            String terrainSignatureIdentity
    ) {
        public Specification {
            if (activationId <= 0L) {
                throw new IllegalArgumentException("Activation ID must be positive");
            }
            oldEpochId = requireIdentifier(oldEpochId, "Old epoch ID");
            newEpochId = requireIdentifier(newEpochId, "New epoch ID");
            boundaryIdentity = requireIdentifier(boundaryIdentity, "Boundary identity");
            terrainSignatureIdentity = requireIdentifier(
                    terrainSignatureIdentity,
                    "Terrain signature identity"
            );
            if (oldEpochId.equals(newEpochId)) {
                throw new IllegalArgumentException("Transition epoch IDs must differ");
            }
            if (widthBlocks <= 0) {
                throw new IllegalArgumentException("Transition width must be positive");
            }
            if (algorithmVersion < 1) {
                throw new IllegalArgumentException("Transition algorithm version must be positive");
            }
        }

        private static String requireIdentifier(String identifier, String label) {
            String requiredIdentifier = Objects.requireNonNull(identifier, label);
            if (requiredIdentifier.isBlank()) {
                throw new IllegalArgumentException(label + " cannot be blank");
            }
            return requiredIdentifier;
        }
    }

    public record TerrainSample(
            double distanceToHistoricalTerrain,
            double newEpochWeight,
            double hydrologyWeight,
            TerrainBoundarySignature nearestSignature,
            double historicalSurfaceHeight,
            double historicalOceanFloorHeight,
            double historicalUpperCeilingDepth
    ) {
        public TerrainSample {
            if (!Double.isFinite(distanceToHistoricalTerrain) || distanceToHistoricalTerrain < 0D) {
                throw new IllegalArgumentException("Historical terrain distance must be finite and non-negative");
            }
            if (!Double.isFinite(newEpochWeight) || newEpochWeight < 0D || newEpochWeight > 1D) {
                throw new IllegalArgumentException("New epoch weight must be between zero and one");
            }
            if (!Double.isFinite(hydrologyWeight) || hydrologyWeight < 0D || hydrologyWeight > 1D) {
                throw new IllegalArgumentException("Hydrology weight must be between zero and one");
            }
            if (nearestSignature != null
                    && (!Double.isFinite(historicalSurfaceHeight)
                    || !Double.isFinite(historicalOceanFloorHeight)
                    || !Double.isFinite(historicalUpperCeilingDepth)
                    || historicalUpperCeilingDepth < 0D)) {
                throw new IllegalArgumentException("Historical terrain heights must be finite");
            }
        }

        static TerrainSample newTerrain(double distance) {
            return new TerrainSample(distance, 1D, 1D, null, 0D, 0D, 0D);
        }

        static TerrainSample historicalTerrain() {
            return new TerrainSample(0D, 0D, 0D, null, 0D, 0D, 0D);
        }

        public boolean hasHistoricalSignature() {
            return nearestSignature != null;
        }

        public Optional<String> historicalPhysicalBiomeKeyAt(int blockY) {
            if (nearestSignature == null || nearestSignature.sampleCount() == 0) {
                return Optional.empty();
            }
            TerrainBoundarySignature.VerticalLayout layout = nearestSignature.samples().layout();
            long offset = (long) blockY - layout.minimumY();
            long roundedIndex = Math.round(offset / (double) layout.sampleStep());
            int sampleIndex = (int) Math.max(0L, Math.min(roundedIndex, layout.sampleCount() - 1L));
            return Optional.of(nearestSignature.biomeAtSample(sampleIndex));
        }
    }
}
