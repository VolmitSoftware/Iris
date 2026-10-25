package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;

import java.util.List;

public record HydrologyTier(
        String name,
        float minCatchment,
        float minHalfWidth,
        float maxHalfWidth,
        float widthFactor,
        float depthRatio,
        int inset,
        boolean cascades,
        int waterfallMinimumDrop,
        int bankBand,
        IrisBankStyle bankStyle,
        boolean deltas,
        IrisHydrologyMaterials materials,
        String fluid,
        List<IrisDecorator> decorators
) {
    private static final float CATCHMENT_SCALE = 2500f;
    private static final float MINIMUM_DEPTH = 1f;

    public float halfWidth(float catchment) {
        float grown = minHalfWidth + widthFactor * (float) Math.sqrt(Math.max(0f, catchment) / CATCHMENT_SCALE);
        return Math.clamp(grown, minHalfWidth, maxHalfWidth);
    }

    public float depth(float halfWidth) {
        return Math.max(MINIMUM_DEPTH, depthRatio * halfWidth);
    }
}
