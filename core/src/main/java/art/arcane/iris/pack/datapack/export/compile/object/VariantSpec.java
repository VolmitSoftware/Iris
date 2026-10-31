package art.arcane.iris.pack.datapack.export.compile.object;

import java.util.List;
import java.util.Objects;

public record VariantSpec(double scale, Rotation rotation, VariantWarp warp, double snow, List<ObjectEdit> edits,
                          boolean preventLeafDecay) {
    public VariantSpec {
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Variant scale must be positive, got " + scale);
        }
        Objects.requireNonNull(rotation, "Variant rotation");
        if (!Double.isFinite(snow) || snow < 0 || snow > 1) {
            throw new IllegalArgumentException("Variant snow must be within [0, 1], got " + snow);
        }
        edits = List.copyOf(edits);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean reshapes() {
        return scale != 1 || !rotation.none() || warp != null || snow > 0;
    }

    public String descriptor() {
        StringBuilder builder = new StringBuilder()
                .append("scale=").append(scale)
                .append(";rotation=").append(rotation.x()).append(',').append(rotation.y()).append(',').append(rotation.z())
                .append(";warp=").append(warp == null ? "none" : warp.key() + "#" + warp.index())
                .append(";snow=").append(snow)
                .append(";leaves=").append(preventLeafDecay)
                .append(";edits=");
        for (ObjectEdit edit : edits) {
            builder.append('[').append(edit.descriptor()).append(']');
        }
        return builder.toString();
    }

    public record Rotation(double x, double y, double z) {
        public static final Rotation NONE = new Rotation(0, 0, 0);

        public boolean none() {
            return x == 0 && y == 0 && z == 0;
        }

        public boolean rightAngles() {
            return x % 90 == 0 && y % 90 == 0 && z % 90 == 0;
        }
    }

    public static final class Builder {
        private double scale = 1;
        private Rotation rotation = Rotation.NONE;
        private VariantWarp warp;
        private double snow;
        private List<ObjectEdit> edits = List.of();
        private boolean preventLeafDecay = true;

        private Builder() {
        }

        public Builder scale(double value) {
            scale = value;
            return this;
        }

        public Builder rotation(Rotation value) {
            rotation = Objects.requireNonNull(value);
            return this;
        }

        public Builder warp(VariantWarp value) {
            warp = value;
            return this;
        }

        public Builder snow(double value) {
            snow = value;
            return this;
        }

        public Builder edits(List<ObjectEdit> value) {
            edits = Objects.requireNonNull(value);
            return this;
        }

        public Builder preventLeafDecay(boolean value) {
            preventLeafDecay = value;
            return this;
        }

        public VariantSpec build() {
            return new VariantSpec(scale, rotation, warp, snow, edits, preventLeafDecay);
        }
    }
}
