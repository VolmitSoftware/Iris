package com.volmit.support;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import java.util.List;
import java.util.function.BiFunction;

/** Settings declared per world column by a function, for chunk and mantle tests. */
public final class ColumnSettings implements FeatureSettings {
    public static final ColumnSettings NONE = new ColumnSettings((x, z) -> List.of());

    private final BiFunction<Integer, Integer, List<Entry<?>>> columns;

    public ColumnSettings(BiFunction<Integer, Integer, List<Entry<?>>> columns) {
        this.columns = columns;
    }

    /** The same entries at every column. */
    public static ColumnSettings everywhere(Entry<?>... entries) {
        var all = List.of(entries);
        return new ColumnSettings((x, z) -> all);
    }

    @Override
    @SuppressWarnings("unchecked") // Filtered by the requested type.
    public <F extends IrisFeature> List<Entry<F>> at(Class<F> featureType, int worldX, int worldZ) {
        return columns.apply(worldX, worldZ).stream()
                .filter(entry -> featureType.isInstance(entry.value()))
                .map(entry -> (Entry<F>) entry)
                .toList();
    }
}
