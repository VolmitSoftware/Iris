package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.Objects;

public record TerrainHeight(Field height, Field slope) {
    public TerrainHeight {
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(slope, "slope");
    }
}
