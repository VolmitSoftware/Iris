package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.RiverFootprint;
import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Objects;

public final class ErosionField {
    private final Long2ObjectOpenHashMap<SurfaceColumn> columns;
    private final int uncontainedWetCells;
    private final HydrologyCandidateRejection rejection;
    private final int rejectionDetail;
    private final long bankExcavation;

    ErosionField(Long2ObjectOpenHashMap<SurfaceColumn> columns, int uncontainedWetCells,
                 HydrologyCandidateRejection rejection, int rejectionDetail, long bankExcavation) {
        this.columns = Objects.requireNonNull(columns, "columns");
        this.uncontainedWetCells = uncontainedWetCells;
        this.rejection = rejection;
        this.rejectionDetail = rejectionDetail;
        this.bankExcavation = bankExcavation;
    }

    public Long2ObjectOpenHashMap<SurfaceColumn> columns() {
        return columns;
    }

    public SurfaceColumn column(int x, int z) {
        return columns.get(RiverFootprint.pack(x, z));
    }

    public int uncontainedWetCells() {
        return uncontainedWetCells;
    }

    public int size() {
        return columns.size();
    }

    public HydrologyCandidateRejection rejection() {
        return rejection;
    }

    public int rejectionDetail() {
        return rejectionDetail;
    }

    public long bankExcavation() {
        return bankExcavation;
    }
}
