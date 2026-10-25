package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.Set;

final class HydrologyShoreTargets {
    private final Set<String> targets;
    private volatile boolean placed;
    private volatile Engine resolvedFor;

    HydrologyShoreTargets(Set<String> targets) {
        this.targets = Set.copyOf(targets);
    }

    boolean placedBy(Engine engine) {
        if (resolvedFor != engine) {
            IrisComplex complex = engine.getComplex();
            HydrologyRuntime runtime = complex == null ? null : complex.getHydrologyRuntime();
            placed = runtime != null && runtime.enabled() && placesAny(engine, targets);
            resolvedFor = engine;
        }
        return placed;
    }

    static boolean placesAny(Engine engine, Set<String> targets) {
        IrisDimension dimension = engine.getDimension();
        if (places(engine, dimension.getHydrologyPolicy(), targets)) {
            return true;
        }
        for (IrisRegion region : dimension.getAllRegions(engine)) {
            if (region != null && places(engine, region.getHydrologyPolicy(), targets)) {
                return true;
            }
        }
        for (IrisBiome biome : dimension.getReachableBiomes(engine)) {
            if (biome != null && places(engine, biome.getHydrologyPolicy(), targets)) {
                return true;
            }
        }
        return false;
    }

    private static boolean places(Engine engine, IrisHydrologyPolicy policy, Set<String> targets) {
        if (policy == null || policy.getShoreBiomes() == null) {
            return false;
        }
        for (String key : policy.getShoreBiomes()) {
            if (key != null && HintedLocator.closureContains(engine, key, targets)) {
                return true;
            }
        }
        return false;
    }
}
