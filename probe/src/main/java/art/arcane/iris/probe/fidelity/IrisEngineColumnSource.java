package art.arcane.iris.probe.fidelity;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.world.IrisWorld;

import java.util.Objects;

public final class IrisEngineColumnSource implements ColumnSource {
    private final Engine engine;
    private final IrisComplex complex;
    private final int minY;
    private final int maxY;
    private final int fluidHeight;

    public IrisEngineColumnSource(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.complex = engine.getComplex();
        IrisWorld world = engine.getTarget().getWorld();
        this.minY = world.minHeight();
        this.maxY = world.maxHeight();
        this.fluidHeight = (int) Math.round(complex.getFluidHeight());
    }

    @Override
    public String label() {
        return "iris:" + engine.getDimension().getLoadKey();
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public FidelityColumn column(int x, int z) {
        int height = Math.min(complex.naturalTrueHeight(x, z), maxY - minY - 1);
        IrisBiome biome = complex.getNaturalTrueBiomeStream().get(x, z);
        return new FidelityColumn(biome == null ? null : biome.getLoadKey(), height + minY, height < fluidHeight);
    }

    @Override
    public void close() {
    }
}
