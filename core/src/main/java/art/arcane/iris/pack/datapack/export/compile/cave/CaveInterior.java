package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.Objects;

record CaveInterior(Field field, int minY, int maxY) {
    CaveInterior {
        Objects.requireNonNull(field);
        if (maxY < minY) {
            throw new IllegalArgumentException("Cave interior needs maxY at or above minY, got " + minY + ".." + maxY);
        }
    }
}
