package art.arcane.iris.pack.datapack.export.compile.surface;

import art.arcane.iris.pack.datapack.export.compile.WeightedState;

import java.util.List;
import java.util.Objects;

record SurfacePalette(List<WeightedState> entries, Selection selection, String noise, List<Double> thresholds) {
    SurfacePalette {
        entries = List.copyOf(entries);
        Objects.requireNonNull(selection);
        thresholds = List.copyOf(thresholds);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("A surface palette needs at least one block");
        }
        if (selection == Selection.NOISE && (noise == null || thresholds.size() != entries.size() - 1)) {
            throw new IllegalArgumentException("A noise palette needs a noise and one threshold between each pair of blocks");
        }
    }

    enum Selection {
        SINGLE,
        STATIC,
        NOISE
    }
}
