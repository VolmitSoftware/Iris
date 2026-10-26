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
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletPlanner;
import art.arcane.iris.generation.hydrology.water.CupSet;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WindowPlanner {
    public static final int CELL = 8;
    public static final int FINE_CELL = 4;
    private static final int WINDOW_CELLS = WindowKey.SIZE / CELL;
    private static final int SPAN = 3 * WINDOW_CELLS;
    private static final int REACH_OVERHANG = 256;
    private static final float BASIN_DEPTH = 0.5f;
    private static final int BREACH_PASSES = 8;
    private static final long SALT_HEAD = 0x5354524541484dL;

    private final HydrologySettings settings;
    private final HeightLattice lattice;
    private final HeightLattice fine;
    private final PondPlanner ponds;
    private final ReachShaper shaper;
    private final SeaInletPlanner inlets;

    public WindowPlanner(HydrologySettings settings, HeightLattice lattice, HeightLattice fine, PondPlanner ponds, ReachShaper shaper) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.lattice = Objects.requireNonNull(lattice, "lattice");
        this.fine = Objects.requireNonNull(fine, "fine");
        this.ponds = Objects.requireNonNull(ponds, "ponds");
        this.shaper = Objects.requireNonNull(shaper, "shaper");
        this.inlets = new SeaInletPlanner(settings);
        if (lattice.cell() != CELL) {
            throw new IllegalArgumentException("Window lattice cell " + lattice.cell() + " must be " + CELL);
        }
        if (fine.cell() != FINE_CELL) {
            throw new IllegalArgumentException("Window fine lattice cell " + fine.cell() + " must be " + FINE_CELL);
        }
    }

    public static List<RegionKey> regions(WindowKey key) {
        return RegionKey.covering(computeMin(key.wx()) - REACH_OVERHANG, computeMin(key.wz()) - REACH_OVERHANG,
                computeMax(key.wx()) + REACH_OVERHANG, computeMax(key.wz()) + REACH_OVERHANG);
    }

    public static List<RegionKey> lakeRegions(WindowKey key) {
        return RegionKey.lakeOwners(computeMin(key.wx()), computeMin(key.wz()), computeMax(key.wx()), computeMax(key.wz()));
    }

    private static int computeMin(int window) {
        return (window - 1) * WindowKey.SIZE;
    }

    private static int computeMax(int window) {
        return (window + 2) * WindowKey.SIZE - 1;
    }

    public WindowComputation compute(WindowKey key, List<RegionPlan> regions) {
        Objects.requireNonNull(key, "key");
        Map<RegionKey, RegionPlan> plans = plansByKey(key, regions);
        int cellX = Math.multiplyExact(key.wx() - 1, WINDOW_CELLS);
        int cellZ = Math.multiplyExact(key.wz() - 1, WINDOW_CELLS);
        HydrologySampleBuffer samples = new HydrologySampleBuffer(SPAN * SPAN);
        lattice.fill(cellX, cellZ, SPAN, SPAN, samples);
        DemGrid raw = new DemGrid(cellX * CELL, cellZ * CELL, CELL, SPAN, SPAN);
        System.arraycopy(samples.height(), 0, raw.values(), 0, SPAN * SPAN);
        List<Pond> lakes = regionLakes(plans.values());
        BurnedTrunks trunks = BurnedTrunks.burn(raw, majorReaches(key, plans), settings.majorTier().inset(), settings.drainage().fillEpsilon());
        BitSet seeds = seeds(samples.ocean(), trunks);
        DemGrid preliminary = new DemGrid(raw.originX(), raw.originZ(), CELL, SPAN, SPAN);
        System.arraycopy(raw.values(), 0, preliminary.values(), 0, SPAN * SPAN);
        Drainage initial = drain(preliminary, seeds, samples, trunks, lakes);
        BitSet channelHint = (BitSet) initial.channels().clone();
        channelHint.andNot(trunks.cells());
        CupSet cups = ponds.subtractCups(raw, key, samples, channelHint);
        Drainage drainage;
        if (cups.size() == 0) {
            raw = preliminary;
            drainage = initial;
        } else {
            trunks.descend(raw.values());
            drainage = drain(raw, seeds, samples, trunks, lakes);
        }
        List<Pond> water = new ArrayList<>(ponds.ponds(raw, drainage.filled(), drainage.basins(), lakes, fine, cups));
        water.addAll(lakes);
        ShapedNetwork network = shaper.shape(drainage.surface(), drainage.receivers(), drainage.accumulation(), drainage.channels(), water,
                fine, settings.seed());
        int passes = water.size() + 1;
        for (int pass = 1; lowered(water, network.ponds()); pass++) {
            if (pass >= passes) {
                throw new IllegalStateException("Window " + key + " ponds did not settle within " + passes + " shaping passes");
            }
            water = replanned(settled(raw, cups, water, network.ponds()));
            network = shaper.shape(drainage.surface(), drainage.receivers(), drainage.accumulation(), drainage.channels(), water, fine,
                    settings.seed());
        }
        List<Reach> reaches = network.reaches();
        List<Pond> windowPonds = windowPonds(network.ponds());
        List<SeaInlet> planned = inlets.plan(raw, samples, drainage.receivers(), drainage.accumulation(), network, settings.seed());
        WindowPlan owned = new WindowPlan(key, ownedReaches(key, reaches), ownedPonds(key, windowPonds), ownedInlets(key, planned));
        return new WindowComputation(owned, reaches, windowPonds);
    }

    public WindowPlan plan(WindowKey key, List<RegionPlan> regions) {
        return compute(key, regions).owned();
    }

    private static Map<RegionKey, RegionPlan> plansByKey(WindowKey key, List<RegionPlan> regions) {
        Map<RegionKey, RegionPlan> plans = new HashMap<>();
        for (RegionPlan plan : regions) {
            if (plans.put(plan.key(), plan) != null) {
                throw new IllegalArgumentException("Window " + key + " got two plans for region " + plan.key());
            }
        }
        List<RegionKey> needed = lakeRegions(key);
        if (!plans.keySet().equals(new HashSet<>(needed))) {
            throw new IllegalArgumentException("Window " + key + " needs the region plans " + needed + ", got " + plans.keySet());
        }
        return plans;
    }

    private static List<Reach> majorReaches(WindowKey key, Map<RegionKey, RegionPlan> plans) {
        List<Reach> reaches = new ArrayList<>();
        for (RegionKey region : regions(key)) {
            reaches.addAll(plans.get(region).majorReaches());
        }
        reaches.sort(Comparator.comparingLong(Reach::id).thenComparingDouble(reach -> reach.xs()[0])
                .thenComparingDouble(reach -> reach.zs()[0]));
        return reaches;
    }

    private static List<Pond> regionLakes(Collection<RegionPlan> regions) {
        List<Pond> lakes = new ArrayList<>();
        for (RegionPlan region : regions) {
            lakes.addAll(region.lakes());
        }
        lakes.sort(Comparator.comparingLong(Pond::id).thenComparingInt(Pond::floorX).thenComparingInt(Pond::floorZ));
        return List.copyOf(lakes);
    }

    private static BitSet seeds(boolean[] ocean, BurnedTrunks trunks) {
        BitSet seeds = trunks.cells();
        for (int cell = 0; cell < ocean.length; cell++) {
            int i = cell % SPAN;
            int j = cell / SPAN;
            if (ocean[cell] || i == 0 || j == 0 || i == SPAN - 1 || j == SPAN - 1) {
                seeds.set(cell);
            }
        }
        return seeds;
    }

    private Drainage drain(DemGrid raw, BitSet seeds, HydrologySampleBuffer samples, BurnedTrunks trunks, List<Pond> lakes) {
        float epsilon = settings.drainage().fillEpsilon();
        float[] filled = PriorityFlood.fill(raw, seeds, epsilon);
        List<Basin> basins = BasinExtractor.basins(raw, filled, BASIN_DEPTH, settings.seaLevel());
        for (int pass = 0; pass < BREACH_PASSES && breach(raw, filled, basins, lakes); pass++) {
            trunks.descend(raw.values());
            filled = PriorityFlood.fill(raw, seeds, epsilon);
            basins = BasinExtractor.basins(raw, filled, BASIN_DEPTH, settings.seaLevel());
        }
        DemGrid surface = new DemGrid(raw.originX(), raw.originZ(), CELL, SPAN, SPAN);
        System.arraycopy(filled, 0, surface.values(), 0, filled.length);
        int[] receivers = D8Router.receivers(surface, filled);
        trunks.route(receivers);
        float[] weights = new float[filled.length];
        for (int cell = 0; cell < filled.length; cell++) {
            if (samples.ocean()[cell]) {
                receivers[cell] = -1;
            }
            weights[cell] = samples.rainfall()[cell] * CELL * CELL;
        }
        trunks.inject(weights);
        float[] accumulation = FlowAccumulator.accumulate(receivers, filled, weights, settings.drainage().saturationCatchment());
        trunks.pin(accumulation);
        BitSet channels = channels(surface, receivers, accumulation, samples, trunks);
        return new Drainage(filled, basins, surface, receivers, accumulation, channels);
    }

    private boolean breach(DemGrid raw, float[] filled, List<Basin> basins, List<Pond> lakes) {
        boolean breached = false;
        for (Basin basin : basins) {
            if (oversized(basin) && !underLake(raw, basin, lakes)) {
                Breacher.breach(raw, filled, basin);
                breached = true;
            }
        }
        return breached;
    }

    private boolean oversized(Basin basin) {
        return basin.maxDepth() > settings.drainage().breachDepth()
                || (long) basin.cells().length * CELL * CELL > settings.ponds().natural().maxArea();
    }

    private static boolean underLake(DemGrid raw, Basin basin, List<Pond> lakes) {
        int x = raw.blockX(basin.floorIndex());
        int z = raw.blockZ(basin.floorIndex());
        for (Pond lake : lakes) {
            if (lake.mask().grownContains(x, z)) {
                return true;
            }
        }
        return false;
    }

    private BitSet channels(DemGrid surface, int[] receivers, float[] accumulation, HydrologySampleBuffer samples, BurnedTrunks trunks) {
        BitSet candidates = ChannelHeads.candidates(surface, accumulation, samples.permission(), settings.tiers().get(0).minCatchment(),
                settings.seaLevel());
        return ChannelHeads.keep(surface, samples.height(), receivers, candidates, settings.drainage().headRelief(), trunks::contains,
                cell -> keepHead(surface, cell, samples.rainfall()[cell]));
    }

    private boolean keepHead(DemGrid surface, int cell, float rainfall) {
        HydrologySettings.Drainage drainage = settings.drainage();
        float keep = drainage.wetnessThinning() ? Math.clamp(Math.max(drainage.minimumHeadKeep(), rainfall), 0f, 1f) : 1f;
        keep *= settings.density();
        return CellHash.unit(CellHash.mix(settings.seed(), SALT_HEAD, surface.blockX(cell), surface.blockZ(cell))) < keep;
    }

    private static List<Reach> ownedReaches(WindowKey key, List<Reach> reaches) {
        List<Reach> owned = new ArrayList<>();
        for (Reach reach : reaches) {
            int last = reach.xs().length - 1;
            if (key.equals(WindowKey.containing((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last])))) {
                owned.add(reach);
            }
        }
        return owned;
    }

    private List<Pond> settled(DemGrid raw, CupSet cups, List<Pond> planned, List<Pond> shaped) {
        List<Pond> settled = new ArrayList<>(shaped.size());
        for (int index = 0; index < shaped.size(); index++) {
            Pond pond = shaped.get(index);
            settled.add(pond.level() < planned.get(index).level() ? ponds.reflood(raw, fine, cups, pond) : pond);
        }
        return settled;
    }

    private static boolean lowered(List<Pond> planned, List<Pond> shaped) {
        for (int index = 0; index < shaped.size(); index++) {
            if (shaped.get(index).level() < planned.get(index).level()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pond> replanned(List<Pond> settled) {
        List<Pond> replanned = new ArrayList<>(settled.size());
        for (Pond pond : settled) {
            if (pond != null) {
                replanned.add(pond.withOutletReachId(0L));
            }
        }
        return replanned;
    }

    private static List<Pond> windowPonds(List<Pond> shaped) {
        List<Pond> window = new ArrayList<>(shaped.size());
        for (Pond pond : shaped) {
            if (pond.kind() != PondKind.LAKE) {
                window.add(pond);
            }
        }
        return window;
    }

    private static List<Pond> ownedPonds(WindowKey key, List<Pond> found) {
        List<Pond> owned = new ArrayList<>();
        for (Pond pond : found) {
            if (key.equals(WindowKey.containing(pond.floorX(), pond.floorZ()))) {
                owned.add(pond);
            }
        }
        return owned;
    }

    private static List<SeaInlet> ownedInlets(WindowKey key, List<SeaInlet> planned) {
        List<SeaInlet> owned = new ArrayList<>();
        for (SeaInlet inlet : planned) {
            if (key.equals(WindowKey.containing((int) Math.floor(inlet.xs()[1]), (int) Math.floor(inlet.zs()[1])))) {
                owned.add(inlet);
            }
        }
        return owned;
    }

    private record Drainage(float[] filled, List<Basin> basins, DemGrid surface, int[] receivers, float[] accumulation, BitSet channels) {
    }
}
