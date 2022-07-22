package com.volmit.iris.engine.feature;

import art.arcane.spatial.hunk.Hunk;
import art.arcane.spatial.hunk.view.HunkView;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.stream.Stream;

@Data
@EqualsAndHashCode(callSuper = true)
public class FeatureTarget<T extends PlatformNamespaced> extends FeatureSizedTarget {
    private final Hunk<T> hunk;

    public FeatureTarget(Hunk<T> hunk, int offsetX, int offsetY, int offsetZ)
    {
        super(hunk.getWidth(), hunk.getHeight(), hunk.getDepth(), offsetX, offsetY, offsetZ);
        this.hunk = hunk;
    }

    public FeatureTarget(Hunk<T> hunk, FeatureSizedTarget target)
    {
        this(hunk, target.getOffsetX(), target.getOffsetY(), target.getOffsetZ());
    }

    public static <V extends PlatformNamespaced> FeatureTarget<V> mergedTarget(Stream<FeatureTarget<V>> targets, FeatureTarget<V> origin, boolean x, boolean y, boolean z)
    {
        List<FeatureTarget<V>> t = targets.toList();
        FeatureSizedTarget mergedSize = FeatureSizedTarget.mergedSize(t.stream().map(i -> i), x, y, z);
        Hunk<V> hunk = new HunkView<>(origin.getHunk(), mergedSize.getWidth(), mergedSize.getHeight(), mergedSize.getDepth(),
            mergedSize.getOffsetX() - origin.getOffsetX(),
            mergedSize.getOffsetY() - origin.getOffsetY(),
            mergedSize.getOffsetZ() - origin.getOffsetZ());
        return new FeatureTarget<>(hunk, mergedSize);
    }
}
