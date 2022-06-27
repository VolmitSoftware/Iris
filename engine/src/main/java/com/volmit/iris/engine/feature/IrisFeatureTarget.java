package com.volmit.iris.engine.feature;

import art.arcane.amulet.collections.hunk.Hunk;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IrisFeatureTarget<T extends PlatformNamespaced> extends IrisFeatureSizedTarget {
    private final Hunk<T> hunk;

    public IrisFeatureTarget(Hunk<T> hunk, int offsetX, int offsetY, int offsetZ)
    {
        super(hunk.getWidth(), hunk.getHeight(), hunk.getDepth(), offsetX, offsetY, offsetZ);
        this.hunk = hunk;
    }

    public IrisFeatureTarget(Hunk<T> hunk, IrisFeatureSizedTarget target)
    {
        this(hunk, target.getOffsetX(), target.getOffsetY(), target.getOffsetZ());
    }
}
