package art.arcane.iris.world.pregen;

import art.arcane.iris.generation.hydrology.engine.HydrologyPregeneration;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class PregenHydrology implements AutoCloseable {
    private final Supplier<Engine> engine;
    private HydrologyRuntime owner;
    private HydrologyPregeneration epoch;
    private int[] bounds;
    private boolean closed;

    public PregenHydrology(Supplier<Engine> engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public synchronized void prepare(List<long[]> regions) {
        if (closed) {
            return;
        }
        HydrologyRuntime runtime = runtime(engine.get());
        if (runtime != owner) {
            withdraw();
            owner = runtime;
            epoch = runtime == null ? null : runtime.openPregeneration();
            applyBounds();
        }
        if (epoch != null) {
            epoch.prepare(regions);
        }
    }

    public synchronized void bound(int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        if (closed) {
            return;
        }
        bounds = new int[]{minRegionX, minRegionZ, maxRegionX, maxRegionZ};
        applyBounds();
    }

    @Override
    public synchronized void close() {
        closed = true;
        withdraw();
        owner = null;
    }

    private void applyBounds() {
        if (epoch != null && bounds != null) {
            epoch.bound(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }

    private void withdraw() {
        if (epoch != null) {
            epoch.close();
            epoch = null;
        }
    }

    public static HydrologyPregeneration open(Engine engine) {
        HydrologyRuntime runtime = runtime(engine);
        return runtime == null ? null : runtime.openPregeneration();
    }

    public static void prepareAll(HydrologyPregeneration epoch, List<long[]> regions) {
        Objects.requireNonNull(regions, "regions");
        if (epoch == null) {
            return;
        }
        int batch = HydrologyRuntime.MAX_PREGENERATION_REGIONS;
        for (int end = regions.size(); end > 0; end -= batch) {
            epoch.prepare(regions.subList(Math.max(0, end - batch), end));
        }
    }

    private static HydrologyRuntime runtime(Engine engine) {
        IrisComplex complex = engine == null ? null : engine.getComplex();
        return complex == null ? null : complex.getHydrologyRuntime();
    }
}
