package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class ReachShaper {
    private static final int SMOOTHING_PASSES = 2;
    private static final float TAPER_LENGTH = 24f;
    private static final float WAIST = 0.5f;
    private static final float OUTLET_LENGTH = 8f;
    private static final long SALT_START = 0x52454143485354L;
    private static final long SALT_END = 0x524541434845L;
    private static final long SALT_COARSE_START = 0x434f4152534553L;
    private static final long SALT_COARSE_END = 0x434f415253454eL;
    private static final long SALT_SHAPE = 0x5348415045L;

    private final HydrologySettings settings;

    public ReachShaper(HydrologySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public ShapedNetwork shape(DemGrid filled, int[] receivers, float[] accumulation, BitSet channels, List<Pond> ponds,
                               HeightLattice fine, long seed) {
        requireNetwork(filled, receivers, accumulation, channels);
        PondLookup lookup = new PondLookup(ponds);
        int[] pondAt = lookup.cells(filled);
        int[] outletCells = outletCells(filled, receivers, accumulation, channels, lookup, pondAt);
        BitSet outlets = new BitSet(receivers.length);
        for (int cell : outletCells) {
            if (cell >= 0) {
                outlets.set(cell);
            }
        }
        List<ReachTracer.Chain> chains = ReachTracer.trace(filled, receivers, channels, pondAt, outlets, settings.seaLevel());
        ReachFitter fitter = new ReachFitter(Objects.requireNonNull(fine, "fine"), lookup);
        List<ReachDraft> drafts = new ArrayList<>(chains.size());
        for (ReachTracer.Chain chain : chains) {
            drafts.add(draft(filled, accumulation, chain, lookup, fitter, seed));
        }
        link(drafts, ReachTracer.downstream(chains));
        int[] starts = ReachTracer.starts(chains);
        int[] outletDrafts = new int[outletCells.length];
        for (int pond = 0; pond < outletCells.length; pond++) {
            outletDrafts[pond] = outletCells[pond] < 0 ? -1 : ReachTracer.indexOf(starts, outletCells[pond]);
        }
        openPondLoops(drafts, ponds, outletDrafts);
        for (int outlet : outletDrafts) {
            if (outlet >= 0) {
                drafts.get(outlet).outlet = true;
            }
        }
        ReachGrader.PondChain chain = pondChain(drafts, ponds, outletDrafts);
        int[] order = upstreamFirst(drafts, chain);
        int[] levels = ReachGrader.grade(drafts, order, settings.seaLevel(), settings.deformation().maxFill(), chain);
        for (int index : order) {
            finish(drafts, drafts.get(index));
        }
        List<Pond> linked = new ArrayList<>(ponds.size());
        for (int pond = 0; pond < ponds.size(); pond++) {
            Pond settled = lowered(ponds.get(pond), levels[pond]);
            linked.add(outletDrafts[pond] < 0 ? settled : settled.withOutletReachId(drafts.get(outletDrafts[pond]).id));
        }
        return new ShapedNetwork(reaches(drafts), linked);
    }

    public List<Reach> coarsePaths(DemGrid filled, int[] receivers, float[] accumulation, BitSet channels, long seed) {
        requireNetwork(filled, receivers, accumulation, channels);
        List<ReachTracer.Chain> chains = ReachTracer.trace(filled, receivers, channels, null, new BitSet(), settings.seaLevel());
        int[] downstream = ReachTracer.downstream(chains);
        long[] ids = new long[chains.size()];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = id(seed, SALT_COARSE_START, SALT_COARSE_END, filled, chains.get(index));
        }
        List<Reach> paths = new ArrayList<>(chains.size());
        for (int index = 0; index < ids.length; index++) {
            paths.add(coarsePath(filled, accumulation, chains.get(index), ids[index], downstream[index] < 0 ? 0L : ids[downstream[index]]));
        }
        return List.copyOf(paths);
    }

    private Reach coarsePath(DemGrid grid, float[] accumulation, ReachTracer.Chain chain, long id, long downstreamId) {
        int[] cells = chain.cells();
        int count = cells.length;
        float[] catchment = catchments(accumulation, chain);
        HydrologyTier tier = settings.tierFor(catchment[count - 1]);
        float[] xs = new float[count];
        float[] zs = new float[count];
        int[] grade = new int[count];
        float[] halfWidth = new float[count];
        float[] depth = new float[count];
        ReachStyle[] style = new ReachStyle[count];
        for (int point = 0; point < count; point++) {
            xs[point] = grid.blockX(cells[point]);
            zs[point] = grid.blockZ(cells[point]);
            grade[point] = Math.max(settings.seaLevel(), (int) Math.floor(grid.values()[cells[point]]) - tier.inset());
            halfWidth[point] = tier.halfWidth(catchment[point]);
            depth[point] = tier.depth(halfWidth[point]);
            style[point] = ReachStyle.RUN;
        }
        if (chain.end() == ReachTracer.End.OCEAN) {
            style[count - 1] = ReachStyle.MOUTH;
        }
        return new Reach(id, tier, downstreamId, xs, zs, grade, halfWidth, depth, style, catchment[count - 1]);
    }

    private ReachDraft draft(DemGrid grid, float[] accumulation, ReachTracer.Chain chain, PondLookup ponds, ReachFitter fitter, long seed) {
        float[] cellCatchment = catchments(accumulation, chain);
        float catchment = cellCatchment[cellCatchment.length - 1];
        HydrologyTier tier = settings.tierFor(catchment);
        long shapeSeed = CellHash.mix(seed, SALT_SHAPE, grid.blockX(chain.start()), grid.blockZ(chain.start()));
        ReachFitter.Fitted fitted = fitter.fit(grid, chain.cells(), cellCatchment, tier, shapeSeed);
        int endLevel = chain.end() == ReachTracer.End.POND ? ponds.pond(chain.pond()).level() : Integer.MIN_VALUE;
        return new ReachDraft(chain, id(seed, SALT_START, SALT_END, grid, chain), tier, catchment, endLevel, fitted);
    }

    private static float[] catchments(float[] accumulation, ReachTracer.Chain chain) {
        int[] cells = chain.cells();
        float[] catchment = new float[cells.length];
        for (int point = 0; point < cells.length; point++) {
            catchment[point] = accumulation[cells[point]];
        }
        if (chain.end() != ReachTracer.End.EDGE) {
            catchment[cells.length - 1] = catchment[cells.length - 2];
        }
        return catchment;
    }

    private static void link(List<ReachDraft> drafts, int[] downstream) {
        int[] counts = new int[drafts.size()];
        for (int target : downstream) {
            if (target >= 0) {
                counts[target]++;
            }
        }
        for (int index = 0; index < counts.length; index++) {
            drafts.get(index).incoming = new int[counts[index]];
        }
        Arrays.fill(counts, 0);
        for (int index = 0; index < downstream.length; index++) {
            int target = downstream[index];
            drafts.get(index).downstream = target;
            if (target >= 0) {
                drafts.get(target).incoming[counts[target]++] = index;
            }
        }
    }

    private static ReachGrader.PondChain pondChain(List<ReachDraft> drafts, List<Pond> ponds, int[] outletDrafts) {
        int[] levels = new int[ponds.size()];
        boolean[] fixed = new boolean[ponds.size()];
        for (int pond = 0; pond < levels.length; pond++) {
            levels[pond] = ponds.get(pond).level();
            fixed[pond] = ponds.get(pond).kind() == PondKind.LAKE;
        }
        int[] inflowPond = new int[drafts.size()];
        for (int index = 0; index < inflowPond.length; index++) {
            ReachTracer.Chain chain = drafts.get(index).chain;
            inflowPond[index] = chain.end() == ReachTracer.End.POND ? chain.pond() : -1;
        }
        return new ReachGrader.PondChain(levels, fixed, inflowPond, outletDrafts);
    }

    private static Pond lowered(Pond pond, int level) {
        if (level >= pond.level()) {
            return pond;
        }
        return new Pond(pond.id(), pond.kind(), level, pond.floor(), pond.spillX(), pond.spillZ(), pond.spillHeight(), pond.mask(),
                pond.outletReachId(), pond.damRaise(), pond.fluid(), pond.floorX(), pond.floorZ());
    }

    private static void openPondLoops(List<ReachDraft> drafts, List<Pond> ponds, int[] outletDrafts) {
        int count = drafts.size();
        byte[] state = new byte[count];
        int[] path = new int[count];
        for (int start = 0; start < count; start++) {
            int length = 0;
            int index = start;
            while (index >= 0 && state[index] == 0) {
                state[index] = 1;
                path[length++] = index;
                index = onward(drafts, ponds, outletDrafts, index);
            }
            if (index >= 0 && state[index] == 1) {
                outletDrafts[lowestPondOnLoop(drafts, ponds, outletDrafts, index)] = -1;
            }
            for (int step = 0; step < length; step++) {
                state[path[step]] = 2;
            }
        }
    }

    private static int lowestPondOnLoop(List<ReachDraft> drafts, List<Pond> ponds, int[] outletDrafts, int entry) {
        int lowest = -1;
        int index = entry;
        do {
            ReachTracer.Chain chain = drafts.get(index).chain;
            if (drafts.get(index).downstream < 0 && chain.end() == ReachTracer.End.POND) {
                int pond = chain.pond();
                if (lowest < 0 || ponds.get(pond).level() < ponds.get(lowest).level()
                        || ponds.get(pond).level() == ponds.get(lowest).level() && pond < lowest) {
                    lowest = pond;
                }
            }
            index = onward(drafts, ponds, outletDrafts, index);
        } while (index != entry);
        return lowest;
    }

    private static int onward(List<ReachDraft> drafts, List<Pond> ponds, int[] outletDrafts, int index) {
        ReachDraft draft = drafts.get(index);
        if (draft.downstream >= 0) {
            return draft.downstream;
        }
        if (draft.chain.end() != ReachTracer.End.POND || ponds.get(draft.chain.pond()).kind() == PondKind.LAKE) {
            return -1;
        }
        return outletDrafts[draft.chain.pond()];
    }

    private static int[] upstreamFirst(List<ReachDraft> drafts, ReachGrader.PondChain chain) {
        int count = drafts.size();
        int[] pending = new int[count];
        int[] order = new int[count];
        int size = 0;
        for (int index = 0; index < count; index++) {
            pending[index] += drafts.get(index).incoming.length;
            int outlet = chain.outletOf(chain.inflowPond()[index]);
            if (outlet >= 0) {
                pending[outlet]++;
            }
        }
        for (int index = 0; index < count; index++) {
            if (pending[index] == 0) {
                order[size++] = index;
            }
        }
        for (int head = 0; head < size; head++) {
            int done = order[head];
            int downstream = drafts.get(done).downstream;
            if (downstream >= 0 && --pending[downstream] == 0) {
                order[size++] = downstream;
            }
            int outlet = chain.outletOf(chain.inflowPond()[done]);
            if (outlet >= 0 && --pending[outlet] == 0) {
                order[size++] = outlet;
            }
        }
        if (size == count) {
            return order;
        }
        throw new IllegalStateException("Reach and pond links loop through " + (count - size) + " of " + count + " reaches");
    }

    private static void finish(List<ReachDraft> drafts, ReachDraft draft) {
        draft.outletZone = new boolean[draft.xs.length];
        float[] distance = distances(draft.xs, draft.zs);
        for (int point = 0; point < distance.length; point++) {
            draft.outletZone[point] = draft.outlet && distance[point] <= OUTLET_LENGTH;
        }
        for (int pass = 0; pass < SMOOTHING_PASSES; pass++) {
            chaikin(draft);
        }
        unfold(draft);
        float upstream = 0f;
        for (int index : draft.incoming) {
            upstream = Math.max(upstream, drafts.get(index).maxHalfWidth);
        }
        widths(draft, upstream);
        draft.style = styles(draft.grade, draft.ocean, draft.outletZone, draft.tier.waterfallMinimumDrop());
    }

    private static void chaikin(ReachDraft draft) {
        int count = draft.xs.length;
        int size = 2 * count;
        float[] xs = new float[size];
        float[] zs = new float[size];
        float[] catchments = new float[size];
        int[] grade = new int[size];
        boolean[] ocean = new boolean[size];
        boolean[] outletZone = new boolean[size];
        for (int segment = 0; segment < count - 1; segment++) {
            int near = 1 + 2 * segment;
            int far = near + 1;
            xs[near] = 0.75f * draft.xs[segment] + 0.25f * draft.xs[segment + 1];
            xs[far] = 0.25f * draft.xs[segment] + 0.75f * draft.xs[segment + 1];
            zs[near] = 0.75f * draft.zs[segment] + 0.25f * draft.zs[segment + 1];
            zs[far] = 0.25f * draft.zs[segment] + 0.75f * draft.zs[segment + 1];
            catchments[near] = 0.75f * draft.catchments[segment] + 0.25f * draft.catchments[segment + 1];
            catchments[far] = 0.25f * draft.catchments[segment] + 0.75f * draft.catchments[segment + 1];
            carryStep(draft, segment, near, grade, ocean, outletZone);
            carryStep(draft, segment + 1, far, grade, ocean, outletZone);
        }
        xs[0] = draft.xs[0];
        zs[0] = draft.zs[0];
        catchments[0] = draft.catchments[0];
        carryStep(draft, 0, 0, grade, ocean, outletZone);
        xs[size - 1] = draft.xs[count - 1];
        zs[size - 1] = draft.zs[count - 1];
        catchments[size - 1] = draft.catchments[count - 1];
        carryStep(draft, count - 1, size - 1, grade, ocean, outletZone);
        draft.xs = xs;
        draft.zs = zs;
        draft.catchments = catchments;
        draft.grade = grade;
        draft.ocean = ocean;
        draft.outletZone = outletZone;
    }

    private static void unfold(ReachDraft draft) {
        int count = draft.xs.length;
        int[] kept = new int[count];
        float[] along = new float[count];
        int size = 1;
        for (int point = 1; point < count; point++) {
            float gap = 2f * (draft.tier.halfWidth(draft.catchments[point]) + 1f);
            int last = kept[size - 1];
            float total = along[size - 1] + (float) Math.hypot(draft.xs[point] - draft.xs[last], draft.zs[point] - draft.zs[last]);
            int cut = size;
            for (int index = size - 2; index >= 0; index--) {
                int from = kept[index];
                float dx = draft.xs[point] - draft.xs[from];
                float dz = draft.zs[point] - draft.zs[from];
                if (dx * dx + dz * dz >= gap * gap) {
                    continue;
                }
                float apart = (float) Math.sqrt(dx * dx + dz * dz);
                if (total - along[index] >= apart + gap) {
                    cut = index + 1;
                }
            }
            if (cut < size) {
                int from = kept[cut - 1];
                total = along[cut - 1] + (float) Math.hypot(draft.xs[point] - draft.xs[from], draft.zs[point] - draft.zs[from]);
            }
            size = cut;
            kept[size] = point;
            along[size] = total;
            size++;
        }
        if (size == count) {
            return;
        }
        float[] xs = new float[size];
        float[] zs = new float[size];
        float[] catchments = new float[size];
        int[] grade = new int[size];
        boolean[] ocean = new boolean[size];
        boolean[] outletZone = new boolean[size];
        for (int index = 0; index < size; index++) {
            int point = kept[index];
            xs[index] = draft.xs[point];
            zs[index] = draft.zs[point];
            catchments[index] = draft.catchments[point];
            grade[index] = draft.grade[point];
            ocean[index] = draft.ocean[point];
            outletZone[index] = draft.outletZone[point];
        }
        draft.xs = xs;
        draft.zs = zs;
        draft.catchments = catchments;
        draft.grade = grade;
        draft.ocean = ocean;
        draft.outletZone = outletZone;
    }

    private static void carryStep(ReachDraft draft, int from, int to, int[] grade, boolean[] ocean, boolean[] outletZone) {
        grade[to] = draft.grade[from];
        ocean[to] = draft.ocean[from];
        outletZone[to] = draft.outletZone[from];
    }

    private static void widths(ReachDraft draft, float upstream) {
        int count = draft.xs.length;
        boolean taper = draft.incoming.length == 0 && !draft.outlet;
        float[] distance = distances(draft.xs, draft.zs);
        float running = upstream;
        draft.halfWidth = new float[count];
        draft.depth = new float[count];
        for (int point = 0; point < count; point++) {
            float law = draft.tier.halfWidth(draft.catchments[point]);
            if (taper) {
                law *= Math.min(1f, distance[point] / TAPER_LENGTH);
            }
            draft.halfWidth[point] = Math.max(law, WAIST * running);
            running = Math.max(running, draft.halfWidth[point]);
            draft.depth[point] = draft.tier.depth(draft.halfWidth[point]);
        }
        draft.maxHalfWidth = running;
    }

    static ReachStyle[] styles(int[] grade, boolean[] ocean, boolean[] outletZone, int waterfallMinimumDrop) {
        ReachStyle[] styles = new ReachStyle[grade.length];
        for (int point = 0; point < grade.length; point++) {
            int drop = point == 0 ? 0 : grade[point - 1] - grade[point];
            styles[point] = style(drop, waterfallMinimumDrop, ocean[point], outletZone[point]);
        }
        for (int point = 1; point < grade.length; point++) {
            if (styles[point] == ReachStyle.MOUTH && styles[point - 1] != ReachStyle.MOUTH
                    && grade[point - 1] - grade[point] >= waterfallMinimumDrop) {
                styles[point - 1] = ReachStyle.WATERFALL;
            }
        }
        return styles;
    }

    private static ReachStyle style(int drop, int waterfallMinimumDrop, boolean mouth, boolean outlet) {
        if (mouth) {
            return ReachStyle.MOUTH;
        }
        if (drop >= waterfallMinimumDrop) {
            return ReachStyle.WATERFALL;
        }
        if (drop >= 2) {
            return ReachStyle.CASCADE;
        }
        if (outlet) {
            return ReachStyle.POND_OUTLET;
        }
        return drop == 1 ? ReachStyle.RIFFLE : ReachStyle.RUN;
    }

    private static List<Reach> reaches(List<ReachDraft> drafts) {
        List<Reach> reaches = new ArrayList<>(drafts.size());
        for (ReachDraft draft : drafts) {
            long downstreamId = draft.downstream < 0 ? 0L : drafts.get(draft.downstream).id;
            reaches.add(new Reach(draft.id, draft.tier, downstreamId, draft.xs, draft.zs, draft.grade, draft.halfWidth, draft.depth,
                    draft.style, draft.catchment));
        }
        return reaches;
    }

    private static int[] outletCells(DemGrid grid, int[] receivers, float[] accumulation, BitSet channels, PondLookup ponds,
                                     int[] pondAt) {
        int[] exits = new int[ponds.size()];
        float[] outflow = new float[ponds.size()];
        Arrays.fill(exits, -1);
        for (int cell = 0; cell < receivers.length; cell++) {
            int pond = pondAt[cell];
            int receiver = receivers[cell];
            if (pond >= 0 && receiver >= 0 && pondAt[receiver] != pond
                    && drains(grid, ponds.pond(pond), accumulation[cell], receiver, outflow[pond], exits[pond])) {
                exits[pond] = receiver;
                outflow[pond] = accumulation[cell];
            }
        }
        int[] outlets = new int[ponds.size()];
        for (int pond = 0; pond < outlets.length; pond++) {
            int exit = exits[pond] >= 0 ? exits[pond] : cellAt(grid, ponds.pond(pond).spillX(), ponds.pond(pond).spillZ());
            outlets[pond] = exit >= 0 && channels.get(exit) && pondAt[exit] < 0 ? exit : -1;
        }
        return outlets;
    }

    private static boolean drains(DemGrid grid, Pond pond, float flow, int candidate, float bestFlow, int current) {
        if (current < 0 || flow > bestFlow) {
            return true;
        }
        if (flow < bestFlow) {
            return false;
        }
        long distance = spillDistance(grid, pond, candidate);
        long best = spillDistance(grid, pond, current);
        return distance < best || (distance == best && candidate < current);
    }

    private static long spillDistance(DemGrid grid, Pond pond, int cell) {
        long dx = grid.blockX(cell) - pond.spillX();
        long dz = grid.blockZ(cell) - pond.spillZ();
        return dx * dx + dz * dz;
    }

    private static int cellAt(DemGrid grid, int x, int z) {
        int i = Math.floorDiv(x - grid.originX() + (grid.cell() >> 1), grid.cell());
        int j = Math.floorDiv(z - grid.originZ() + (grid.cell() >> 1), grid.cell());
        return i < 0 || j < 0 || i >= grid.width() || j >= grid.depth() ? -1 : j * grid.width() + i;
    }

    private static float[] distances(float[] xs, float[] zs) {
        float[] distance = new float[xs.length];
        for (int point = 1; point < xs.length; point++) {
            float dx = xs[point] - xs[point - 1];
            float dz = zs[point] - zs[point - 1];
            distance[point] = distance[point - 1] + (float) Math.sqrt(dx * dx + dz * dz);
        }
        return distance;
    }

    private static long id(long seed, long startSalt, long endSalt, DemGrid grid, ReachTracer.Chain chain) {
        long start = CellHash.mix(seed, startSalt, grid.blockX(chain.start()), grid.blockZ(chain.start()));
        return CellHash.mix(start, endSalt, grid.blockX(chain.last()), grid.blockZ(chain.last()));
    }

    private static void requireNetwork(DemGrid grid, int[] receivers, float[] accumulation, BitSet channels) {
        int count = grid.width() * grid.depth();
        if (receivers.length != count || accumulation.length != count || channels.length() > count) {
            throw new IllegalArgumentException("Drainage network does not match the " + count + "-cell grid: receivers=" + receivers.length
                    + " accumulation=" + accumulation.length + " channels=" + channels.length());
        }
    }
}
