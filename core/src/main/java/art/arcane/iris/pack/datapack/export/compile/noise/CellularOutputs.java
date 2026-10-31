package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.Objects;

public record CellularOutputs(Field cellValue, Field f1, Field f2, Field winnerX, Field winnerZ) {
    public CellularOutputs {
        Objects.requireNonNull(cellValue, "cellValue");
        Objects.requireNonNull(f1, "f1");
        Objects.requireNonNull(f2, "f2");
        Objects.requireNonNull(winnerX, "winnerX");
        Objects.requireNonNull(winnerZ, "winnerZ");
    }
}
