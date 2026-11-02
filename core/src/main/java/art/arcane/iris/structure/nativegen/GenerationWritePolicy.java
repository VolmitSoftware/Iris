package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.structure.object.IrisStaticObjectLayer;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPositionPredicate;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.structure.StructureSeaSurface;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GenerationWritePolicy {
    private GenerationWritePolicy() {
    }

    public static boolean isHistoricalStructure(Engine engine, long activation) {
        if (activation <= 0 || !(engine instanceof IrisEngine irisEngine)) {
            return false;
        }
        GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
        return router != null && activation < router.history().activeActivation().activationId();
    }

    public static StructureSeaSurface seaSurface(Engine engine) {
        IrisDimension dimension = engine.getDimension();
        int fluidHeight = dimension.getFluidHeight();
        if (fluidHeight <= 0) {
            return StructureSeaSurface.NONE;
        }
        for (NativeBlockState fluid : dimension.getFluidPalette().getBlockData(engine.getData())) {
            if (fluid.isFluid()) {
                return new StructureSeaSurface(engine.getMinHeight() + fluidHeight, fluid);
            }
        }
        return StructureSeaSurface.NONE;
    }

    public static NativeBlockPositionPredicate protectedPositions(
            IrisStaticObjectLayer staticObjects,
            DimensionStackContext stackContext,
            int minimumY
    ) {
        Map<Long, DimensionStackLayout> layouts = new ConcurrentHashMap<>();
        return (x, y, z) -> {
            if (!staticObjects.isEmpty() && staticObjects.contains(
                    x, y - minimumY, z)) {
                return true;
            }
            if (stackContext == null) {
                return false;
            }
            long columnKey = ((long) x << 32)
                    ^ (z & 0xFFFFFFFFL);
            DimensionStackLayout layout = layouts.computeIfAbsent(
                    columnKey,
                    ignored -> stackContext.sample(x, z)
            );
            return layout.isHostFeatureProtectedY(y - minimumY);
        };
    }
}
