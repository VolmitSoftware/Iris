package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.grid.Basin;
import art.arcane.iris.generation.hydrology.grid.BasinExtractor;
import art.arcane.iris.generation.hydrology.grid.Breacher;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.ChannelHeads;
import art.arcane.iris.generation.hydrology.grid.D8Router;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.FlowAccumulator;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.PriorityFlood;
import art.arcane.iris.generation.hydrology.water.LakeCandidate;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class RegionPlanner {
    public static final int CELL = 64;
    public static final int FINE_CELL = 4;
    private static final int REGION_CELLS = RegionKey.SIZE / CELL;
    private static final int SPAN = 3 * REGION_CELLS;
    private static final float BASIN_DEPTH = 0.5f;
    private static final int BREACH_PASSES = 8;
    private static final long SALT_HEAD = 0x4d414a4f524844L;

    private final HydrologySettings settings;
    private final HeightLattice lattice;
    private final HeightLattice fine;
    private final PondPlanner ponds;
    private final ReachShaper shaper;

    public RegionPlanner(HydrologySettings settings, HeightLattice lattice, HeightLattice fine, PondPlanner ponds, ReachShaper shaper) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.lattice = Objects.requireNonNull(lattice, "lattice");
        this.fine = Objects.requireNonNull(fine, "fine");
        this.ponds = Objects.requireNonNull(ponds, "ponds");
        this.shaper = Objects.requireNonNull(shaper, "shaper");
        if (lattice.cell() != CELL) {
            throw new IllegalArgumentException("Region lattice cell " + lattice.cell() + " must be " + CELL);
        }
        if (fine.cell() != FINE_CELL) {
            throw new IllegalArgumentException("Region fine lattice cell " + fine.cell() + " must be " + FINE_CELL);
        }
    }

    public RegionComputation compute(RegionKey key) {
        Objects.requireNonNull(key, "key");
        int cellX = Math.multiplyExact(key.rx() - 1, REGION_CELLS);
        int cellZ = Math.multiplyExact(key.rz() - 1, REGION_CELLS);
        HydrologySampleBuffer samples = new HydrologySampleBuffer(SPAN * SPAN);
        lattice.fill(cellX, cellZ, SPAN, SPAN, samples);
        DemGrid raw = new DemGrid(cellX * CELL, cellZ * CELL, CELL, SPAN, SPAN);
        System.arraycopy(samples.height(), 0, raw.values(), 0, SPAN * SPAN);
        Drainage drainage = drain(raw, seeds(samples.ocean()));
        List<LakeCandidate> candidates = ponds.lakeCandidates(raw, drainage.basins());
        List<Pond> lakes = ownedLakes(key, ponds.lakes(measuredCandidates(key, candidates), fine));
        List<Reach> reaches = majorReaches(raw, drainage.filled(), samples);
        return new RegionComputation(new RegionPlan(key, ownedReaches(key, reaches), lakes), reaches, candidates);
    }

    public RegionPlan plan(RegionKey key) {
        return compute(key).owned();
    }

    private static BitSet seeds(boolean[] ocean) {
        BitSet seeds = new BitSet(ocean.length);
        for (int cell = 0; cell < ocean.length; cell++) {
            int i = cell % SPAN;
            int j = cell / SPAN;
            if (ocean[cell] || i == 0 || j == 0 || i == SPAN - 1 || j == SPAN - 1) {
                seeds.set(cell);
            }
        }
        return seeds;
    }

    private Drainage drain(DemGrid raw, BitSet seeds) {
        float[] filled = PriorityFlood.fill(raw, seeds, settings.drainage().fillEpsilon());
        List<Basin> basins = BasinExtractor.basins(raw, filled, BASIN_DEPTH, settings.seaLevel());
        for (int pass = 0; pass < BREACH_PASSES && oversized(basins); pass++) {
            breach(raw, filled, basins);
            filled = PriorityFlood.fill(raw, seeds, settings.drainage().fillEpsilon());
            basins = BasinExtractor.basins(raw, filled, BASIN_DEPTH, settings.seaLevel());
        }
        return new Drainage(filled, basins);
    }

    private boolean oversized(List<Basin> basins) {
        for (Basin basin : basins) {
            if (oversized(basin)) {
                return true;
            }
        }
        return false;
    }

    private boolean oversized(Basin basin) {
        return (long) basin.cells().length * CELL * CELL > settings.lakes().maxArea()
                || (long) basin.span(SPAN) * CELL > LakeCandidate.MAX_EXTENT;
    }

    private void breach(DemGrid raw, float[] filled, List<Basin> basins) {
        for (Basin basin : basins) {
            if (oversized(basin)) {
                Breacher.breach(raw, filled, basin);
            }
        }
    }

    private List<Reach> majorReaches(DemGrid raw, float[] filled, HydrologySampleBuffer samples) {
        DemGrid surface = new DemGrid(raw.originX(), raw.originZ(), CELL, SPAN, SPAN);
        System.arraycopy(filled, 0, surface.values(), 0, filled.length);
        int[] receivers = D8Router.receivers(surface, filled);
        float[] weights = new float[filled.length];
        for (int cell = 0; cell < filled.length; cell++) {
            if (samples.ocean()[cell]) {
                receivers[cell] = -1;
            }
            weights[cell] = samples.rainfall()[cell] * CELL * CELL;
        }
        float[] accumulation = FlowAccumulator.accumulate(receivers, filled, weights, settings.drainage().saturationCatchment());
        BitSet candidates = ChannelHeads.candidates(surface, accumulation, samples.permission(), settings.majorTier().minCatchment(), settings.seaLevel());
        BitSet channels = ChannelHeads.keep(surface, samples.height(), receivers, candidates, settings.drainage().headRelief(), cell -> false,
                cell -> CellHash.unit(CellHash.mix(settings.seed(), SALT_HEAD, surface.blockX(cell), surface.blockZ(cell))) < settings.density());
        return shaper.coarsePaths(surface, receivers, accumulation, channels, settings.seed());
    }

    private static List<Reach> ownedReaches(RegionKey key, List<Reach> reaches) {
        List<Reach> owned = new ArrayList<>();
        for (Reach reach : reaches) {
            int last = reach.xs().length - 1;
            if (key.equals(RegionKey.containing((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last])))) {
                owned.add(reach);
            }
        }
        return owned;
    }

    static List<LakeCandidate> measuredCandidates(RegionKey key, List<LakeCandidate> candidates) {
        int count = candidates.size();
        boolean[] measured = new boolean[count];
        int[] queue = new int[count];
        int tail = 0;
        for (int index = 0; index < count; index++) {
            LakeCandidate candidate = candidates.get(index);
            if (key.equals(RegionKey.containing(candidate.floorX(), candidate.floorZ()))) {
                measured[index] = true;
                queue[tail++] = index;
            }
        }
        for (int head = 0; head < tail; head++) {
            LakeCandidate reached = candidates.get(queue[head]);
            for (int index = 0; index < count; index++) {
                if (!measured[index] && reached.near(candidates.get(index))) {
                    measured[index] = true;
                    queue[tail++] = index;
                }
            }
        }
        List<LakeCandidate> selected = new ArrayList<>(tail);
        for (int index = 0; index < count; index++) {
            if (measured[index]) {
                selected.add(candidates.get(index));
            }
        }
        return selected;
    }

    private static List<Pond> ownedLakes(RegionKey key, List<Pond> lakes) {
        List<Pond> owned = new ArrayList<>();
        for (Pond lake : lakes) {
            if (key.equals(RegionKey.containing(lake.floorX(), lake.floorZ()))) {
                owned.add(lake);
            }
        }
        return owned;
    }

    private record Drainage(float[] filled, List<Basin> basins) {
    }
}
