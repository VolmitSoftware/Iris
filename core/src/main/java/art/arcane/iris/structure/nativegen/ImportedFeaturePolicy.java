package art.arcane.iris.structure.nativegen;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPositionPredicate;

import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.object.IrisStaticObjectLayer;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeatureControl;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeaturePolicy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ImportedFeaturePolicy implements NativeImportedFeaturePolicy<IrisDimension> {
    private static final Placement UNGUARDED = new Placement(false, false, null, null, null, null);

    protected final Engine engine;

    public ImportedFeaturePolicy(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public IrisDimension dimension() {
        return engine.getDimension();
    }

    @Override
    public int runtimeId() {
        return engine.getCacheID();
    }

    @Override
    public String dimensionKey() {
        IrisDimension dimension = engine.getDimension();
        return dimension == null ? "<unbound>" : dimension.getLoadKey();
    }

    @Override
    public NativeImportedFeatureControl control() {
        return NativeFeatureGenerationPolicy.control(engine);
    }

    @Override
    public Set<String> visibleBiomeKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            keys.add(biome.getStructureDerivativeKey());
            keys.add(biome.getDerivativeKey());
            if (includeVanillaDerivative()) {
                keys.add(biome.getVanillaDerivativeKey());
            }
            keys.addAll(biome.getBiomeScatter());
            keys.addAll(biome.getBiomeSkyScatter());
            if (biome.isCustom()) {
                for (IrisBiomeCustom custom : biome.getCustomDerivitives()) {
                    keys.add(customBiomeKey(custom));
                }
            }
        }
        return keys;
    }

    @Override
    public List<DerivativeGroup> customBiomeDerivatives() {
        List<DerivativeGroup> derivatives = new ArrayList<>();
        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            if (!biome.isCustom()) {
                continue;
            }
            List<String> customKeys = new ArrayList<>();
            for (IrisBiomeCustom custom : biome.getCustomDerivitives()) {
                customKeys.add(customBiomeKey(custom));
            }
            derivatives.add(new DerivativeGroup(biome.getLoadKey(), biome.getVanillaDerivativeKey(), customKeys));
        }
        return derivatives;
    }

    protected boolean includeVanillaDerivative() {
        return true;
    }

    protected String customBiomeKey(IrisBiomeCustom biome) {
        return engine.getData().customBiomeResourceKey(engine.getDimension(), biome);
    }

    @Override
    public Placement placement() {
        IrisStaticObjectLayer staticObjects = engine.getDimension().getStaticObjectLayer(engine.getData());
        int minimumY = engine.getMinHeight();
        DimensionStackContext stack = engine.getDimensionStackContext();
        if (staticObjects.isEmpty() && stack == null) {
            return UNGUARDED;
        }
        NativeBlockPositionPredicate protectedPosition =
                GenerationWritePolicy.protectedPositions(staticObjects, stack, minimumY);
        return new Placement(!staticObjects.isEmpty() || stack != null, stack != null,
                (x, z) -> Engine.hostHeight(engine, x, z, false) + minimumY + 1,
                (x, z) -> Engine.hostHeight(engine, x, z, true) + minimumY + 1,
                protectedPosition,
                (x, y, z) -> stack == null || stack.getLayerAt(x, y, z).terrainContext().isSelfReferencing());
    }

    @Override
    public void info(String message) {
        IrisLogging.info("Iris " + message);
    }

    @Override
    public void warn(String message) {
        IrisLogging.warn("Iris " + message);
    }

    @Override
    public void error(String message, Throwable cause) {
        if (cause == null) {
            IrisLogging.error("Iris " + message);
        } else {
            IrisLogging.reportError("Iris " + message, cause);
        }
    }
}
