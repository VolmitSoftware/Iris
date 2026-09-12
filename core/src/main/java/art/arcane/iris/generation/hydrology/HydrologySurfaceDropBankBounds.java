package art.arcane.iris.generation.hydrology;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class HydrologySurfaceDropBankBounds {
    private HydrologySurfaceDropBankBounds() {
    }

    static List<HydrologyColumnSample> constrain(List<HydrologyColumnSample> columns, int maximumWidth) {
        Int2ObjectOpenHashMap<IntArrayList> wetRows = wetRows(columns);
        ArrayList<HydrologyColumnSample> constrained = new ArrayList<>(columns.size());
        for (HydrologyColumnSample column : columns) {
            if (!hasDryCut(column) || withinWidth(wetRows, column.x(), column.z(), maximumWidth)) {
                constrained.add(column);
                continue;
            }
            ArrayList<HydrologyColumnLayer> layers = new ArrayList<>(column.layers().size());
            for (HydrologyColumnLayer layer : column.layers()) {
                layers.add(!layer.channel() && layer.terrainOwned()
                        ? naturalLayer(layer, column.naturalHeight()) : layer);
            }
            constrained.add(new HydrologyColumnSample(column.x(), column.z(), column.naturalHeight(),
                    column.seaLevel(), column.ocean(), column.parentBiomeKey(), layers));
        }
        return List.copyOf(constrained);
    }

    private static Int2ObjectOpenHashMap<IntArrayList> wetRows(List<HydrologyColumnSample> columns) {
        Int2ObjectOpenHashMap<IntArrayList> rows = new Int2ObjectOpenHashMap<>();
        for (HydrologyColumnSample column : columns) {
            for (HydrologyColumnLayer layer : column.layers()) {
                if (layer.channel() && layer.fluidOwned() && layer.bedY() < layer.fluidHeadY()) {
                    rows.computeIfAbsent(column.z(), ignored -> new IntArrayList()).add(column.x());
                    break;
                }
            }
        }
        for (IntArrayList row : rows.values()) {
            Arrays.sort(row.elements(), 0, row.size());
        }
        return rows;
    }

    private static boolean hasDryCut(HydrologyColumnSample column) {
        for (HydrologyColumnLayer layer : column.layers()) {
            if (!layer.channel() && layer.terrainOwned() && layer.bedY() < column.naturalHeight()) {
                return true;
            }
        }
        return false;
    }

    private static boolean withinWidth(Int2ObjectOpenHashMap<IntArrayList> wetRows, int x, int z, int maximumWidth) {
        long maximumDistance = (long) maximumWidth * maximumWidth;
        for (int deltaZ = 0; deltaZ <= maximumWidth; deltaZ++) {
            long remainingDistance = maximumDistance - (long) deltaZ * deltaZ;
            if (nearWetX(wetRows.get(z - deltaZ), x, remainingDistance)
                    || deltaZ > 0 && nearWetX(wetRows.get(z + deltaZ), x, remainingDistance)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearWetX(IntArrayList row, int x, long maximumDistance) {
        if (row == null) {
            return false;
        }
        int found = Arrays.binarySearch(row.elements(), 0, row.size(), x);
        if (found >= 0) {
            return true;
        }
        int next = -found - 1;
        if (next < row.size()) {
            long distance = (long) row.getInt(next) - x;
            if (distance * distance <= maximumDistance) {
                return true;
            }
        }
        if (next > 0) {
            long distance = (long) row.getInt(next - 1) - x;
            return distance * distance <= maximumDistance;
        }
        return false;
    }

    private static HydrologyColumnLayer naturalLayer(HydrologyColumnLayer layer, int naturalHeight) {
        return new HydrologyColumnLayer(layer.feature(), naturalHeight, naturalHeight, naturalHeight,
                false, layer.shore(), false, false, false, false, false, false, false,
                layer.profileKey(), layer.surfaceBiomeKey(), layer.mouthBiomeKey(), layer.shoreBiomeKey(),
                layer.bankBiomeKey(), layer.floodedCaveBiomeKey());
    }
}
