package art.arcane.iris.probe;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.IDataFixer;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class HeadlessGenerationHistorySession {
    private HeadlessGenerationHistorySession() {
    }

    static GenerationHistory create(RealPackProbeSupport.HistoryRequest request, IDataFixer fixer) throws IOException {
        GenerationHistory.FreshCreation creation = capture(request, fixer);
        Files.createDirectories(creation.dimensionRoot());
        return GenerationHistory.create(creation.dimensionRoot(), creation.packSource(), creation.packFingerprint(),
                creation.worldSeed(), creation.dimensionContract(), creation.registryContract());
    }

    static GenerationHistory createUnpublished(RealPackProbeSupport.HistoryRequest request, IDataFixer fixer) throws IOException {
        return GenerationHistory.createUnpublished(capture(request, fixer));
    }

    private static GenerationHistory.FreshCreation capture(RealPackProbeSupport.HistoryRequest request, IDataFixer fixer) throws IOException {
        IrisData data = request.data();
        IrisDimension dimension = request.dimension();
        PlatformRegistries registries = IrisPlatforms.get().registries();
        PlatformGenerationRegistry generationRegistry = registries.generationRegistry();
        Path pack = data.getDataFolder().toPath();
        String fingerprint = GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION);
        GenerationRegistryContract registryContract = GenerationRegistryContractFactory.create(
                data, dimension, fingerprint, fixer, registries, generationRegistry);
        String dimensionTypeKey = generationRegistry.dimensionTypeResourceKey(
                data.getDataFolder().getName(), dimension.getLoadKey(), dimension.getDimensionTypeKey());
        GenerationEpoch.DimensionContract dimensionContract = GenerationEpochContractFactory.create(
                dimension, dimension.getLoadKey(), dimensionTypeKey);
        return new GenerationHistory.FreshCreation(request.worldRoot(), pack, fingerprint, request.seed(),
                dimensionContract, registryContract);
    }
}
