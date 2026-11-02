package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.ShoreBiomeLineages;

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
        for (String key : ShoreBiomeLineages.placed(dimension, dimension.getAllRegions(engine), engine)) {
            if (HintedLocator.closureContains(engine, key, targets)) {
                return true;
            }
        }
        return false;
    }
}
