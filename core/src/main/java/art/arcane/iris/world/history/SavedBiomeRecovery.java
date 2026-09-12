package art.arcane.iris.world.history;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.hydrology.IrisRiverPolicy;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SavedBiomeRecovery {
    private SavedBiomeRecovery() {
    }

    public static Optional<SavedBiomeChunk> recover(Input input) throws IOException {
        Objects.requireNonNull(input, "input");
        Optional<ChunkGenerationSemantics> recorded = input.history().semantics(input.chunkX(), input.chunkZ());
        if (recorded.isEmpty()) {
            return Optional.empty();
        }
        ChunkGenerationSemantics semantics = recorded.get();
        if (!semantics.sealed() || semantics.surfaceBiomeKeys().size() != 1
                || semantics.regionKeys().size() != 1) {
            return Optional.empty();
        }
        GenerationActivation activation = input.history().resolveActivation(input.chunkX(), input.chunkZ());
        if (activation.activationId() != semantics.activationId()) {
            return Optional.empty();
        }
        String biomeKey = semantics.surfaceBiomeKeys().iterator().next();
        String regionKey = semantics.regionKeys().iterator().next();
        if (!onlyKey(semantics.caveBiomeKeys(), biomeKey)) {
            return Optional.empty();
        }
        IrisDimension dimension = input.dimension();
        GenerationEpoch.DimensionContract contract = input.history().resolveEpoch(input.chunkX(), input.chunkZ())
                .dimensionContract();
        if (!contract.dimensionKey().equals(dimension.getLoadKey())
                || contract.minHeight() != dimension.getMinHeight()
                || contract.maxHeight() != dimension.getMaxHeight()
                || contract.upperTerrainEnabled() || dimension.hasUpperDimension() || dimension.hasDimensionStack()
                || !dimension.getCarving().isEmpty()
                || !optionalKey(dimension.getFocus(), biomeKey)
                || !optionalKey(dimension.getFocusRegion(), regionKey)
                || !singleChoice(dimension.getRegions(), regionKey)) {
            return Optional.empty();
        }
        IrisRegion region = input.data().getRegionLoader().load(regionKey);
        IrisBiome biome = input.data().getBiomeLoader().load(biomeKey);
        if (region == null || biome == null
                || !regionKey.equals(region.getLoadKey()) || !biomeKey.equals(biome.getLoadKey())
                || !singleChoice(region.getLandBiomes(), biomeKey)
                || !singleChoice(region.getSeaBiomes(), biomeKey)
                || !singleChoice(region.getShoreBiomes(), biomeKey)
                || !singleChoice(region.getCaveBiomes(), biomeKey)
                || !biome.getChildren().isEmpty() || !biome.getFloatingChildBiomes().isEmpty()
                || !optionalKey(biome.getCarvingBiome(), biomeKey)
                || !sameRiverBiomes(dimension.getRiverPolicy(), biomeKey)
                || !sameRiverBiomes(region.getRiverPolicy(), biomeKey)
                || !sameRiverBiomes(biome.getRiverPolicy(), biomeKey)
                || hasHistoricalColumns(input, activation)) {
            return Optional.empty();
        }
        SavedBiomeChunk.Header header = new SavedBiomeChunk.Header(input.chunkX(), input.chunkZ(),
                activation.activationId(), contract.minHeight(), contract.height());
        SavedBiomeChunk.Cell cell = new SavedBiomeChunk.Cell(activation.activationId(), biomeKey, regionKey);
        SavedBiomeChunk.Column column = new SavedBiomeChunk.Column(cell, cell,
                List.of(new SavedBiomeChunk.Span(header.minimumY(), header.maximumYExclusive(), cell)));
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(header);
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                builder.column(localX, localZ, column);
            }
        }
        return Optional.of(builder.build());
    }

    private static boolean hasHistoricalColumns(Input input, GenerationActivation activation) throws IOException {
        if (activation.isInitial()) {
            return false;
        }
        TransitionGenerationPlan transition = input.history().transitionPlan(activation.activationId());
        int minimumX = Math.multiplyExact(input.chunkX(), 16);
        int minimumZ = Math.multiplyExact(input.chunkZ(), 16);
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                if (transition.newEpochWeightAt(Math.addExact(minimumX, localX),
                        Math.addExact(minimumZ, localZ)) != 1D) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean singleChoice(Collection<String> keys, String expected) {
        return keys != null && !keys.isEmpty() && onlyKey(keys, expected);
    }

    private static boolean onlyKey(Collection<String> keys, String expected) {
        for (String key : keys) {
            if (!expected.equals(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean optionalKey(String key, String expected) {
        return key != null && (key.isEmpty() || expected.equals(key));
    }

    private static boolean sameRiverBiomes(IrisRiverPolicy policy, String biomeKey) {
        return policy == null || onlyKey(policy.getAllBiomeIds(), biomeKey);
    }

    public record Input(GenerationHistory history, int chunkX, int chunkZ, IrisData data, IrisDimension dimension) {
        public Input {
            Objects.requireNonNull(history, "history");
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(dimension, "dimension");
        }
    }
}
