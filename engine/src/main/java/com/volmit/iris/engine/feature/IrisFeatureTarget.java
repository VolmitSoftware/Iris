package com.volmit.iris.engine.feature;

import art.arcane.amulet.collections.hunk.Hunk;
import art.arcane.amulet.collections.hunk.view.HunkView;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

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

    public static <V extends PlatformNamespaced> IrisFeatureTarget<V> mergedTarget(Stream<IrisFeatureTarget<V>> targets, IrisFeatureTarget<V> origin, boolean x, boolean y, boolean z)
    {
        List<IrisFeatureTarget<V>> t = targets.toList();
        IrisFeatureSizedTarget mergedSize = IrisFeatureSizedTarget.mergedSize(t.stream().map(i -> i), x, y, z);
        Hunk<V> hunk = new HunkView<>(origin.getHunk(), mergedSize.getWidth(), mergedSize.getHeight(), mergedSize.getDepth(),
            mergedSize.getOffsetX() - origin.getOffsetX(),
            mergedSize.getOffsetY() - origin.getOffsetY(),
            mergedSize.getOffsetZ() - origin.getOffsetZ());
        return new IrisFeatureTarget<>(hunk, mergedSize);
    }
}
