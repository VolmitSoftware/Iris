package art.arcane.iris.modded;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionTerrainContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.nativelib.terrain.NativeSpawnBiomePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record ModdedSpawnBiomePolicy(Supplier<Engine> engines, BiFunction<Engine, String, NativeGenerationLease> leases)
        implements NativeSpawnBiomePolicy<Engine> {
    @Override
    public Engine current() { return engines.get(); }

    @Override
    public int runtimeId(Engine engine) { return engine.getCacheID(); }

    @Override
    public NativeGenerationLease lease(Engine engine) { return leases.apply(engine, "modded_spawn_biomes"); }

    @Override
    public NativeGenerationScope context(Engine engine, NativeGenerationLease lease) {
        return IrisContext.open(engine, lease.sessionId(), null);
    }

    @Override
    public void populate(Engine engine, MappingTarget target) {
        IrisDimension host = engine.getDimension();
        add(target, host, engine.getData(), host.getReachableBiomes(engine));
        DimensionStackContext stack = engine.getDimensionStackContext();
        if (stack != null) {
            for (DimensionTerrainContext terrain : stack.getLayersBottomToTop()) {
                if (!terrain.isSelfReferencing()) {
                    IrisDimension dimension = terrain.getDimension();
                    add(target, dimension, terrain.getData(), dimension.getReachableBiomes(terrain));
                }
            }
        }
    }

    private static void add(MappingTarget target, IrisDimension dimension, IrisData data, Iterable<IrisBiome> biomes) {
        for (IrisBiome biome : biomes) {
            if (biome == null || !biome.isCustom()) { continue; }
            Consumer<String> custom = target.vanilla(biome.getVanillaDerivativeKey());
            if (custom == null) { continue; }
            for (IrisBiomeCustom derivative : biome.getCustomDerivitives()) {
                custom.accept(data.customBiomeResourceKey(dimension, derivative));
            }
        }
    }
}
