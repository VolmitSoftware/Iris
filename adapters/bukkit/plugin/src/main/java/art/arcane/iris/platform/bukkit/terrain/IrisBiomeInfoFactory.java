package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisBiomeInfo;
import art.arcane.iris.api.terrain.IrisCustomBiomeInfo;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.volmlib.util.collection.KList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class IrisBiomeInfoFactory {
    private IrisBiomeInfoFactory() {
    }

    public static IrisBiomeInfo surface(Engine engine, int blockX, int blockZ, Consumer<Throwable> reportFailure) {
        Objects.requireNonNull(reportFailure, "reportFailure");
        BiomeEnvironment environment = engine.getSurfaceBiomeEnvironment(blockX, blockZ);
        IrisBiome biome = environment.biome();
        InferredType inferredType = biome.getInferredType();
        return new IrisBiomeInfo(
                biome.getLoadKey(),
                biome.getName(),
                environment.region().getLoadKey(),
                environment.region().getName(),
                biome.getDerivativeKey(),
                biome.getVanillaDerivativeKey(),
                inferredType == null ? "" : inferredType.name().toLowerCase(Locale.ROOT),
                customDerivatives(environment, reportFailure));
    }

    private static List<IrisCustomBiomeInfo> customDerivatives(
            BiomeEnvironment environment,
            Consumer<Throwable> reportFailure) {
        KList<IrisBiomeCustom> customBiomes = environment.biome().getCustomDerivitives();
        if (customBiomes == null || customBiomes.isEmpty()) {
            return List.of();
        }

        List<IrisCustomBiomeInfo> derivatives = new ArrayList<>(customBiomes.size());
        for (IrisBiomeCustom customBiome : customBiomes) {
            String registryKey = "";
            try {
                registryKey = environment.data().customBiomeResourceKey(environment.dimension(), customBiome);
            } catch (RuntimeException failure) {
                reportFailure.accept(new IllegalStateException(
                        "Unable to resolve the registry key for Iris custom biome '" + customBiome.getId() + "'.", failure));
            }
            derivatives.add(new IrisCustomBiomeInfo(customBiome.getId(), registryKey));
        }
        return derivatives;
    }
}
