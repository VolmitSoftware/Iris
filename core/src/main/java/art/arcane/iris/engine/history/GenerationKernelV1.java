package art.arcane.iris.engine.history;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.DimensionStackContext;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.IrisEngineMantle;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.actuator.IrisDimensionStackActuator;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.modifier.IrisCustomModifier;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionMode;
import art.arcane.iris.engine.object.IrisDimensionModeType;
import art.arcane.iris.engine.object.IrisStaticObjectLayer;
import art.arcane.iris.spi.IrisLogging;

import java.nio.file.Path;

final class GenerationKernelV1 implements GenerationKernelRegistry.RuntimeFactory {
    static final String IMPLEMENTATION_FINGERPRINT =
            GenerationBuildRevision.requireFingerprint(1, GenerationKernelV1.class);

    @Override
    public SeedManager createSeedManager(long worldSeed) {
        return new SeedManager(worldSeed);
    }

    @Override
    public EngineMantle createMantle(IrisEngine engine, Path storageDirectory) {
        return new IrisEngineMantle(engine, storageDirectory);
    }

    @Override
    public IrisComplex create(IrisEngine engine, TransitionGenerationPlan transitionPlan, boolean detached) {
        return new IrisComplex(engine, transitionPlan, detached);
    }

    @Override
    public UpperDimensionContext createUpperContext(IrisEngine engine) {
        IrisDimension dimension = engine.getDimension();
        if (!dimension.hasUpperDimension()) {
            return null;
        }
        String upperKey = dimension.getUpperDimension();
        IrisDimension upperDimension = upperKey.equals(dimension.getLoadKey())
                ? dimension
                : engine.getData().getDimensionLoader().load(upperKey, false);
        if (upperDimension == null) {
            throw new IllegalStateException("Upper dimension '" + upperKey
                    + "' is absent from the immutable Iris epoch pack.");
        }
        UpperDimensionContext context = UpperDimensionContext.create(engine, upperDimension);
        IrisLogging.info("Upper dimension enabled: " + upperKey
                + (context.isSelfReferencing() ? " (self-referencing)" : " (cross-referencing)"));
        return context;
    }

    @Override
    public DimensionStackContext createDimensionStackContext(IrisEngine engine) {
        IrisDimension dimension = engine.getDimension();
        if (!dimension.hasDimensionStack()) {
            return null;
        }
        if (dimension.hasUpperDimension()) {
            throw new IllegalStateException("Dimension '" + dimension.getLoadKey()
                    + "' cannot enable both dimensionStack and upperDimension.");
        }
        DimensionStackContext context = DimensionStackContext.create(engine, dimension.getDimensionStack());
        IrisLogging.info("Dimension stack enabled: "
                + String.join(" -> ", dimension.getDimensionStack().getDimensions()));
        return context;
    }

    @Override
    public EngineMode createMode(IrisEngine engine) {
        Throwable configuredFailure = null;
        try {
            IrisDimensionMode configuredMode = engine.getDimension().getMode();
            if (configuredMode == null) {
                configuredMode = new IrisDimensionMode();
                engine.getDimension().setMode(configuredMode);
            }
            EngineMode configured = configuredMode.create(engine);
            if (configured == null) {
                throw new IllegalStateException("Dimension mode factory returned null");
            }
            return configured;
        } catch (Throwable failure) {
            configuredFailure = failure;
            IrisLogging.reportError(failure);
            if (engine.getModeFallbackLogged().compareAndSet(false, true)) {
                IrisLogging.warn("Failed to initialize configured dimension mode for "
                        + engine.getDimension().getLoadKey() + ", falling back to OVERWORLD mode.");
            }
        }

        try {
            EngineMode fallback = IrisDimensionModeType.OVERWORLD.create(engine);
            if (fallback == null) {
                throw new IllegalStateException("OVERWORLD mode factory returned null");
            }
            return fallback;
        } catch (Throwable fallbackFailure) {
            fallbackFailure.addSuppressed(configuredFailure);
            throw new IllegalStateException("Both configured and fallback Iris engine modes failed.", fallbackFailure);
        }
    }

    @Override
    public void registerStaticObjects(IrisEngine engine, EngineMode mode) {
        IrisStaticObjectLayer staticObjects = engine.getDimension().getStaticObjectLayer(engine.getData());
        mode.registerStage((x, z, blocks, biomes, multicore, context) ->
                staticObjects.apply(engine, x, z, blocks));
    }

    @Override
    public void registerDimensionStack(
            IrisEngine engine,
            EngineMode mode,
            DimensionStackContext context
    ) {
        if (context == null) {
            return;
        }
        IrisDimensionStackActuator dimensionStack = new IrisDimensionStackActuator(engine);
        mode.registerTerrainStage((x, z, blocks, biomes, multicore, chunkContext) ->
                dimensionStack.actuate(x, z, blocks, multicore, chunkContext));
        IrisCustomModifier stackCustom = new IrisCustomModifier(engine);
        mode.registerStage((x, z, blocks, biomes, multicore, chunkContext) ->
                stackCustom.modify(x, z, blocks, multicore, chunkContext));
    }
}
