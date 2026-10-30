package art.arcane.iris.pack.datapack.export.compile.object;

import java.util.Objects;

public record VariantWarp(String key, int index, WarpField field) {
    public VariantWarp {
        Objects.requireNonNull(key, "Warp key");
        Objects.requireNonNull(field, "Warp field");
        if (index < 0) {
            throw new IllegalArgumentException("Warp variant index must not be negative, got " + index);
        }
    }
}
