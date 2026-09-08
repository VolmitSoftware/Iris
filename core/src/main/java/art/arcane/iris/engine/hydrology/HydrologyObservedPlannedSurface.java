package art.arcane.iris.engine.hydrology;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HydrologyObservedPlannedSurface implements HydrologyCaveVoxelViewFactory.PlannedSurface {
    private final HydrologyCaveVoxelViewFactory.PlannedSurface delegate;
    private final Long2ObjectOpenHashMap<ArrayList<Observation>> observations;

    public HydrologyObservedPlannedSurface(HydrologyCaveVoxelViewFactory.PlannedSurface delegate) {
        this.delegate = Objects.requireNonNull(delegate);
        this.observations = new Long2ObjectOpenHashMap<>();
    }

    @Override
    public int resolve(int x, int z, int naturalHeight) {
        int resolvedHeight = delegate.resolve(x, z, naturalHeight);
        long key = RiverFootprint.pack(x, z);
        ArrayList<Observation> column = observations.get(key);
        if (column == null) {
            column = new ArrayList<>(1);
            observations.put(key, column);
        }
        for (Observation observation : column) {
            if (observation instanceof HeightObservation height && height.naturalHeight() == naturalHeight) {
                return resolvedHeight;
            }
        }
        column.add(new HeightObservation(x, z, naturalHeight, resolvedHeight));
        return resolvedHeight;
    }

    @Override
    public boolean ownsTerrain(int x, int z) {
        boolean owned = delegate.ownsTerrain(x, z);
        long key = RiverFootprint.pack(x, z);
        ArrayList<Observation> column = observations.get(key);
        if (column == null) {
            column = new ArrayList<>(1);
            observations.put(key, column);
        }
        for (Observation observation : column) {
            if (observation instanceof OwnershipObservation) {
                return owned;
            }
        }
        column.add(new OwnershipObservation(x, z, owned));
        return owned;
    }

    public List<Observation> observationsAt(int x, int z) {
        ArrayList<Observation> column = observations.get(RiverFootprint.pack(x, z));
        return column == null ? List.of() : List.copyOf(column);
    }

    public sealed interface Observation permits HeightObservation, OwnershipObservation {
        boolean matches(HydrologyCaveVoxelViewFactory.PlannedSurface surface);
    }

    public record HeightObservation(
            int x,
            int z,
            int naturalHeight,
            int resolvedHeight
    ) implements Observation {
        @Override
        public boolean matches(HydrologyCaveVoxelViewFactory.PlannedSurface surface) {
            return surface.resolve(x, z, naturalHeight) == resolvedHeight;
        }
    }

    public record OwnershipObservation(int x, int z, boolean terrainOwned) implements Observation {
        @Override
        public boolean matches(HydrologyCaveVoxelViewFactory.PlannedSurface surface) {
            return surface.ownsTerrain(x, z) == terrainOwned;
        }
    }
}
