package com.volmit.iris.engine.feature;

import art.arcane.amulet.geometry.Vec;
import art.arcane.amulet.range.IntegerRange;
import art.arcane.spatial.hunk.storage.ArrayHunk;
import art.arcane.spatial.hunk.view.HunkView;
import com.volmit.iris.platform.PlatformNamespaced;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public <T extends PlatformNamespaced> IrisFeatureTarget<T> hunked()
    {
        return new IrisFeatureTarget<>(new ArrayHunk<>(width, height, depth), this);
    }

    public <T extends PlatformNamespaced> IrisFeatureTarget<T> hunked(IrisFeatureTarget<T> origin)
    {
        return new IrisFeatureTarget<>(new HunkView<>(origin.getHunk(), width, height, depth, offsetX - origin.getOffsetX(), offsetY - origin.getOffsetY(), offsetZ - origin.getOffsetZ()), this);
    }

    Stream<IrisFeatureSizedTarget> splitX() {
        if(width <= 1) {
            return Stream.of(this);
        }

        return Stream.of(IrisFeatureSizedTarget.builder()
                .width(width/2).height(height).depth(depth)
                .offsetX(offsetX).offsetY(offsetY).offsetZ(offsetZ).build(),
            IrisFeatureSizedTarget.builder()
                .width(width - (width/2)).height(height).depth(depth)
                .offsetX(offsetX + (width/2)).offsetY(offsetY).offsetZ(offsetZ).build());
    }

    Stream<IrisFeatureSizedTarget> splitY() {
        if(height <= 1) {
            return Stream.of(this);
        }

        return Stream.of(IrisFeatureSizedTarget.builder()
                .width(width).height(height/2).depth(depth)
                .offsetX(offsetX).offsetY(offsetY).offsetZ(offsetZ).build(),
            IrisFeatureSizedTarget.builder()
                .width(width).height(height - (height / 2)).depth(depth)
                .offsetX(offsetX).offsetY(offsetY + (height/2)).offsetZ(offsetZ).build());
    }

    Stream<IrisFeatureSizedTarget> splitZ() {
        if(depth <= 1) {
            return Stream.of(this);
        }

        return Stream.of(IrisFeatureSizedTarget.builder()
                .width(width).height(height).depth(depth/2)
                .offsetX(offsetX).offsetY(offsetY).offsetZ(offsetZ).build(),
            IrisFeatureSizedTarget.builder()
                .width(width).height(height).depth(depth - (depth/2))
                .offsetX(offsetX).offsetY(offsetY).offsetZ(offsetZ + (depth/2)).build());
    }

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

    public static IrisFeatureSizedTarget mergedSize(Stream<IrisFeatureSizedTarget> targets, boolean x, boolean y, boolean z) {
        List<IrisFeatureSizedTarget> t = targets.toList();
        return IrisFeatureSizedTarget.builder()
            .width(x ? t.stream().mapToInt(IrisFeatureSizedTarget::getWidth).sum() : t[0].getWidth())
            .height(y ? t.stream().mapToInt(IrisFeatureSizedTarget::getHeight).sum() : t[0].getHeight())
            .depth(z ? t.stream().mapToInt(IrisFeatureSizedTarget::getDepth).sum() : t[0].getDepth())
            .offsetX(x ? t.stream().mapToInt(IrisFeatureSizedTarget::getOffsetX).min().orElse(0) : t[0].getOffsetX())
            .offsetY(y ? t.stream().mapToInt(IrisFeatureSizedTarget::getOffsetY).min().orElse(0) : t[0].getOffsetY())
            .offsetZ(z ? t.stream().mapToInt(IrisFeatureSizedTarget::getOffsetZ).min().orElse(0) : t[0].getOffsetZ())
            .build();
    }
}
