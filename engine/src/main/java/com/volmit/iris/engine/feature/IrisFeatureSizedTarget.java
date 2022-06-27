package com.volmit.iris.engine.feature;

import art.arcane.amulet.collections.hunk.Hunk;
import art.arcane.amulet.geometry.Vec;
import art.arcane.amulet.range.IntegerRange;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class IrisFeatureSizedTarget {
    @Builder.Default
    private final int width = 16;
    @Builder.Default
    private final int height = 0;
    @Builder.Default
    private final int depth = 16;
    @Builder.Default
    private final int offsetX = 0;
    @Builder.Default
    private final int offsetY = 0;
    @Builder.Default
    private final int offsetZ = 0;

    public int getAbsoluteMaxX()
    {
        return getOffsetX() + getWidth() - 1;
    }

    public int getAbsoluteMaxY()
    {
        return getOffsetY() + getHeight() - 1;
    }

    public int getAbsoluteMaxZ()
    {
        return getOffsetZ() + getDepth() - 1;
    }

    public IntegerRange x()
    {
        return new IntegerRange(getOffsetX(), getAbsoluteMaxX());
    }

    public IntegerRange y()
    {
        return new IntegerRange(getOffsetY(), getAbsoluteMaxY());
    }

    public IntegerRange z()
    {
        return new IntegerRange(getOffsetZ(), getAbsoluteMaxZ());
    }

    public IntegerRange localX()
    {
        return new IntegerRange(0, getWidth() - 1);
    }

    public IntegerRange localY()
    {
        return new IntegerRange(0, getHeight() - 1);
    }

    public IntegerRange localZ()
    {
        return new IntegerRange(0, getDepth() - 1);
    }
}
