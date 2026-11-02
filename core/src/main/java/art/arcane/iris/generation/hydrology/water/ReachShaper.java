package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.raster.LinkBlend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ReachShaper {
    public static final int DELTA_STEP = 2;
    private static final int SMOOTHING_PASSES = 2;
    private static final float TAPER_LENGTH = 24f;
    private static final float WAIST = 0.5f;
    private static final float OUTLET_LENGTH = 8f;
    private static final float LIP_MARGIN = 4f;
    private static final int LEAD_IN_REACH = 16;
    private static final float LIP_HALF_WIDTH = 1f;
    private static final long SALT_START = 0x52454143485354L;
    private static final long SALT_END = 0x524541434845L;
    private static final long SALT_COARSE_START = 0x434f4152534553L;
    private static final long SALT_COARSE_END = 0x434f415253454eL;
    private static final long SALT_SHAPE = 0x5348415045L;
    private static final long SALT_DELTA = 0x44454c5441L;
    private static final float DELTA_CLEARANCE = 2f;
    private static final double DELTA_BEND = 0.5D;
    private static final double DELTA_JITTER = 0.25D;

    private final HydrologySettings settings;

    public ReachShaper(HydrologySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public ShapedNetwork shape(WindowKey window, DemGrid filled, int[] receivers, float[] accumulation, BitSet channels, List<Pond> ponds,
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
        int[] sources = ReachGrader.outletPonds(outletDrafts, drafts.size());
        for (int index = 0; index < sources.length; index++) {
            if (leavesThroughALip(ponds, sources[index])) {
                hold(drafts.get(index), ponds.get(sources[index]).mask());
            }
        }
        ReachGrader.PondChain chain = pondChain(drafts, ponds, outletDrafts);
        int[] order = upstreamFirst(drafts, chain);
        int[] levels = ReachGrader.grade(drafts, order, settings.seaLevel(), settings.deformation().maxFill(), chain);
        for (int index : order) {
            finish(drafts, drafts.get(index));
        }
        for (int index = 0; index < sources.length; index++) {
            if (leavesThroughALip(ponds, sources[index])) {
                leadIn(drafts.get(index), ponds.get(sources[index]).mask());
            }
        }
        boolean[] touched = ConfluenceGrader.level(window, drafts, order);
        for (int index = 0; index < touched.length; index++) {
            if (touched[index]) {
                restyle(drafts.get(index));
            }
        }
        List<Pond> linked = new ArrayList<>(ponds.size());
        for (int pond = 0; pond < ponds.size(); pond++) {
            Pond settled = lowered(ponds.get(pond), levels[pond]);
            linked.add(outletDrafts[pond] < 0 ? settled : settled.withOutletReachId(drafts.get(outletDrafts[pond]).id));
        }
        List<Reach> reaches = reaches(drafts);
        reaches.addAll(deltas(drafts, reaches, linked, fine, seed));
        return new ShapedNetwork(reaches, linked);
    }

    private List<Reach> deltas(List<ReachDraft> drafts, List<Reach> reaches, List<Pond> ponds, HeightLattice fine, long seed) {
        List<Integer> mouths = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            ReachDraft draft = drafts.get(index);
            if (draft.tier.delta().channels() > 0 && draft.chain.end() == ReachTracer.End.OCEAN) {
                mouths.add(index);
            }
        }
        if (mouths.isEmpty()) {
            return List.of();
        }
        mouths.sort(Comparator.comparingLong((Integer index) -> drafts.get(index).id).thenComparingDouble(index -> drafts.get(index).xs[0])
                .thenComparingDouble(index -> drafts.get(index).zs[0]));
        List<Reach> accepted = new ArrayList<>();
        for (int index : mouths) {
            ReachDraft mouth = drafts.get(index);
            Reach parent = reaches.get(index);
            List<Reach> earlier = List.copyOf(accepted);
            for (Reach channel : distributaries(mouth, mouth.tier, fine, seed)) {
                if (!crosses(channel, parent, reaches, earlier, ponds)) {
                    accepted.add(channel);
                }
            }
        }
        return accepted;
    }

    private List<Reach> distributaries(ReachDraft mouth, HydrologyTier tier, HeightLattice fine, long seed) {
        int last = mouth.xs.length - 1;
        int start = estuaryStart(mouth.style);
        int channels = tier.delta().channels();
        float halfWidth = Math.min(tier.maxHalfWidth(), mouth.halfWidth[start] / (float) Math.sqrt(channels));
        float[] distance = distances(mouth.xs, mouth.zs);
        float lip = start == 0 ? 0f : 0.5f * (distance[start] - distance[start - 1]);
        float branch = distance[start] + halfWidth + DELTA_CLEARANCE + 1f + lip;
        double axisX = mouth.xs[last] - mouth.xs[start];
        double axisZ = mouth.zs[last] - mouth.zs[start];
        if (start == last || branch >= distance[last] || Math.hypot(axisX, axisZ) < 1e-3D) {
            return List.of();
        }
        float[] at = pointAt(mouth.xs, mouth.zs, distance, start, branch);
        double heading = Math.atan2(axisZ, axisX);
        long choices = CellHash.mix(seed, SALT_DELTA, (int) Math.floor(mouth.xs[last]), (int) Math.floor(mouth.zs[last]));
        int firstSide = CellHash.unit(CellHash.mix(choices, SALT_DELTA, -1, 0)) < 0.5D ? 1 : -1;
        List<Reach> made = new ArrayList<>(channels);
        for (int channel = 0; channel < channels; channel++) {
            long channelChoices = CellHash.mix(choices, SALT_DELTA, channel, 0);
            Fan fan = new Fan(CellHash.mix(mouth.id, SALT_DELTA, channel, channels), at[0], at[1], heading,
                    angle(tier.delta(), channelChoices, channel, firstSide), DELTA_BEND * CellHash.unit(CellHash.mix(channelChoices, SALT_DELTA, 2, 0)),
                    halfWidth);
            Reach reach = distributary(mouth, tier, fine, fan);
            if (reach != null) {
                made.add(reach);
            }
        }
        return made;
    }

    private static int estuaryStart(ReachStyle[] style) {
        int start = style.length - 1;
        if (style[start] != ReachStyle.MOUTH) {
            return start;
        }
        while (start > 0 && style[start - 1] == ReachStyle.MOUTH) {
            start--;
        }
        return start;
    }

    private static float[] pointAt(float[] xs, float[] zs, float[] distance, int from, float along) {
        int segment = from;
        while (distance[segment + 1] < along) {
            segment++;
        }
        float share = (along - distance[segment]) / Math.max(1e-6f, distance[segment + 1] - distance[segment]);
        return new float[]{xs[segment] + share * (xs[segment + 1] - xs[segment]), zs[segment] + share * (zs[segment + 1] - zs[segment])};
    }

    private static double angle(HydrologyTier.Delta delta, long channelChoices, int channel, int firstSide) {
        int side = channel % 2 == 0 ? firstSide : -firstSide;
        int onSide = side == firstSide ? (delta.channels() + 1) / 2 : delta.channels() / 2;
        double turn = CellHash.unit(CellHash.mix(channelChoices, SALT_DELTA, 1, 0));
        return side * Math.toRadians(delta.spreadDegrees()) * (channel / 2 + 1 - DELTA_JITTER * turn) / onSide;
    }

    private Reach distributary(ReachDraft mouth, HydrologyTier tier, HeightLattice fine, Fan fan) {
        int length = tier.delta().length();
        int stations = (length + DELTA_STEP - 1) / DELTA_STEP + 1;
        float[] xs = new float[stations];
        float[] zs = new float[stations];
        xs[0] = fan.x();
        zs[0] = fan.z();
        for (int station = 1; station < stations; station++) {
            double direction = fan.heading() + fan.angle() * (1D - fan.bend() * ((station - 1) * DELTA_STEP) / length);
            xs[station] = (float) (xs[station - 1] + DELTA_STEP * Math.cos(direction));
            zs[station] = (float) (zs[station - 1] + DELTA_STEP * Math.sin(direction));
        }
        int step = fine.cell();
        int[] nodeX = new int[3 * stations];
        int[] nodeZ = new int[3 * stations];
        for (int station = 0; station < stations; station++) {
            int next = Math.min(stations - 1, station + 1);
            int previous = Math.max(0, station - 1);
            double dx = xs[next] - xs[previous];
            double dz = zs[next] - zs[previous];
            double run = Math.max(1e-6D, Math.hypot(dx, dz));
            for (int offset = -1; offset <= 1; offset++) {
                int node = 3 * station + offset + 1;
                nodeX[node] = (int) Math.floor((xs[station] - offset * fan.halfWidth() * dz / run) / step + 0.5D);
                nodeZ[node] = (int) Math.floor((zs[station] + offset * fan.halfWidth() * dx / run) / step + 0.5D);
            }
        }
        HydrologySampleBuffer samples = new HydrologySampleBuffer(nodeX.length);
        fine.fill(nodeX, nodeZ, samples);
        float highest = settings.seaLevel() + settings.deformation().maxFill();
        int sea = -1;
        for (int station = 0; station < stations; station++) {
            if (samples.ocean()[3 * station + 1]) {
                sea = station;
                break;
            }
            for (int node = 3 * station; node < 3 * station + 3; node++) {
                if (samples.height()[node] > highest) {
                    return null;
                }
            }
        }
        if (sea < 2) {
            return null;
        }
        int end = Math.min(stations - 1, sea + 1);
        return reach(mouth, tier, fan, Arrays.copyOf(xs, end + 1), Arrays.copyOf(zs, end + 1));
    }

    private Reach reach(ReachDraft mouth, HydrologyTier tier, Fan fan, float[] xs, float[] zs) {
        int count = xs.length;
        float[] distance = distances(xs, zs);
        int[] grade = new int[count];
        float[] halfWidth = new float[count];
        float[] depth = new float[count];
        ReachStyle[] style = new ReachStyle[count];
        float channelDepth = tier.depth(fan.halfWidth());
        for (int point = 0; point < count; point++) {
            grade[point] = settings.seaLevel();
            halfWidth[point] = fan.halfWidth();
            depth[point] = channelDepth + (HydrologyTier.MINIMUM_DEPTH - channelDepth) * distance[point] / distance[count - 1];
            style[point] = ReachStyle.MOUTH;
        }
        return new Reach(fan.id(), tier, 0L, xs, zs, grade, halfWidth, depth, style, mouth.catchment / tier.delta().channels());
    }

    private boolean crosses(Reach channel, Reach parent, List<Reach> reaches, List<Reach> earlier, List<Pond> ponds) {
        float reach = channel.halfWidth()[0] + DELTA_CLEARANCE;
        int minX = (int) Math.floor(minimum(channel.xs()) - reach);
        int minZ = (int) Math.floor(minimum(channel.zs()) - reach);
        int maxX = (int) Math.ceil(maximum(channel.xs()) + reach);
        int maxZ = (int) Math.ceil(maximum(channel.zs()) + reach);
        List<Reach> others = new ArrayList<>();
        for (Reach other : reaches) {
            if (other != parent && near(other, minX, minZ, maxX, maxZ)) {
                others.add(other);
            }
        }
        for (Reach other : earlier) {
            if (near(other, minX, minZ, maxX, maxZ)) {
                others.add(other);
            }
        }
        List<Pond> nearby = new ArrayList<>();
        for (Pond pond : ponds) {
            if (near(pond.mask(), minX, minZ, maxX, maxZ)) {
                nearby.add(pond);
            }
        }
        boolean parentNear = near(parent, minX, minZ, maxX, maxZ);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (distanceTo(channel, x, z) > reach) {
                    continue;
                }
                for (Pond pond : nearby) {
                    if (pond.mask().contains(x, z)) {
                        return true;
                    }
                }
                for (Reach other : others) {
                    if (waterGrade(other, x, z) != Integer.MIN_VALUE) {
                        return true;
                    }
                }
                if (parentNear && waterGrade(parent, x, z) > settings.seaLevel()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int waterGrade(Reach reach, double x, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        int grade = Integer.MIN_VALUE;
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double ax = reach.xs()[segment];
            double az = reach.zs()[segment];
            double dx = reach.xs()[segment + 1] - ax;
            double dz = reach.zs()[segment + 1] - az;
            double length = dx * dx + dz * dz;
            double t = length == 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
            double distance = Math.hypot(x - (ax + t * dx), z - (az + t * dz));
            double width = reach.halfWidth()[segment] + t * (reach.halfWidth()[segment + 1] - reach.halfWidth()[segment]);
            if (distance <= width && distance < nearest) {
                nearest = distance;
                grade = reach.grade()[t < 0.5D ? segment : segment + 1];
            }
        }
        return grade;
    }

    private static double distanceTo(Reach reach, double x, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double ax = reach.xs()[segment];
            double az = reach.zs()[segment];
            double dx = reach.xs()[segment + 1] - ax;
            double dz = reach.zs()[segment + 1] - az;
            double length = dx * dx + dz * dz;
            double t = length == 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
            nearest = Math.min(nearest, Math.hypot(x - (ax + t * dx), z - (az + t * dz)));
        }
        return nearest;
    }

    private static boolean near(Reach reach, int minX, int minZ, int maxX, int maxZ) {
        float widest = maximum(reach.halfWidth());
        return minimum(reach.xs()) - widest <= maxX && maximum(reach.xs()) + widest >= minX
                && minimum(reach.zs()) - widest <= maxZ && maximum(reach.zs()) + widest >= minZ;
    }

    private static boolean near(PondMask mask, int minX, int minZ, int maxX, int maxZ) {
        int half = mask.step() >> 1;
        return mask.originX() - half <= maxX && mask.originX() + (mask.width() - 1) * mask.step() + half >= minX
                && mask.originZ() - half <= maxZ && mask.originZ() + (mask.depth() - 1) * mask.step() + half >= minZ;
    }

    private static float minimum(float[] values) {
        float minimum = Float.POSITIVE_INFINITY;
        for (float value : values) {
            minimum = Math.min(minimum, value);
        }
        return minimum;
    }

    private static float maximum(float[] values) {
        float maximum = Float.NEGATIVE_INFINITY;
        for (float value : values) {
            maximum = Math.max(maximum, value);
        }
        return maximum;
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

    private static boolean leavesThroughALip(List<Pond> ponds, int source) {
        return source >= 0 && ponds.get(source).kind() != PondKind.LAKE;
    }

    private static void hold(ReachDraft draft, PondMask mask) {
        draft.held = new boolean[draft.xs.length];
        draft.held[0] = true;
        for (int point = 1; point < draft.xs.length; point++) {
            if (!nearLip(mask, draft.xs[point], draft.zs[point], draft.tier.halfWidth(draft.catchments[point]))) {
                return;
            }
            draft.held[point] = true;
        }
    }

    public static boolean nearLip(PondMask mask, float x, float z, float halfWidth) {
        return mask.nearest(x, z, halfWidth + 1f + LIP_MARGIN) != null;
    }

    private static void leadIn(ReachDraft draft, PondMask mask) {
        if (mask.contains(Math.round(draft.xs[0]), Math.round(draft.zs[0]))) {
            return;
        }
        PondMask.Cell anchor = mask.nearest(draft.xs[0], draft.zs[0], LEAD_IN_REACH);
        if (anchor == null || foldsBack(draft, anchor)) {
            return;
        }
        draft.xs = prepend(draft.xs, anchor.x());
        draft.zs = prepend(draft.zs, anchor.z());
        draft.catchments = prepend(draft.catchments, draft.catchments[0]);
        draft.halfWidth = prepend(draft.halfWidth, draft.halfWidth[0]);
        draft.depth = prepend(draft.depth, draft.depth[0]);
        draft.grade = prepend(draft.grade, draft.grade[0]);
        draft.ocean = prepend(draft.ocean, false);
        draft.outletZone = prepend(draft.outletZone, true);
        draft.held = prepend(draft.held, true);
        ReachStyle[] style = new ReachStyle[draft.style.length + 1];
        style[0] = draft.style[0];
        System.arraycopy(draft.style, 0, style, 1, draft.style.length);
        draft.style = style;
    }

    private static boolean foldsBack(ReachDraft draft, PondMask.Cell anchor) {
        float along = (float) Math.hypot(draft.xs[0] - anchor.x(), draft.zs[0] - anchor.z());
        for (int point = 1; point < draft.xs.length; point++) {
            along += (float) Math.hypot(draft.xs[point] - draft.xs[point - 1], draft.zs[point] - draft.zs[point - 1]);
            float gap = 2f * (draft.halfWidth[point] + 1f);
            float apart = (float) Math.hypot(draft.xs[point] - anchor.x(), draft.zs[point] - anchor.z());
            if (apart < gap && along >= apart + gap) {
                return true;
            }
        }
        return false;
    }

    private static float[] prepend(float[] values, float first) {
        float[] result = new float[values.length + 1];
        result[0] = first;
        System.arraycopy(values, 0, result, 1, values.length);
        return result;
    }

    private static int[] prepend(int[] values, int first) {
        int[] result = new int[values.length + 1];
        result[0] = first;
        System.arraycopy(values, 0, result, 1, values.length);
        return result;
    }

    private static boolean[] prepend(boolean[] values, boolean first) {
        boolean[] result = new boolean[values.length + 1];
        result[0] = first;
        System.arraycopy(values, 0, result, 1, values.length);
        return result;
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
        float feederEnd = 0f;
        for (int index : draft.incoming) {
            ReachDraft feeder = drafts.get(index);
            upstream = Math.max(upstream, feeder.maxHalfWidth);
            feederEnd = Math.max(feederEnd, feeder.halfWidth[feeder.halfWidth.length - 1]);
        }
        widths(draft, upstream, feederEnd);
        restyle(draft);
    }

    private static void restyle(ReachDraft draft) {
        draft.style = styles(draft.grade, draft.ocean, draft.fixed(), draft.tier.waterfallMinimumDrop());
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
        boolean[] held = draft.held == null ? null : new boolean[size];
        for (int segment = 0; segment < count - 1; segment++) {
            int near = 1 + 2 * segment;
            int far = near + 1;
            xs[near] = 0.75f * draft.xs[segment] + 0.25f * draft.xs[segment + 1];
            xs[far] = 0.25f * draft.xs[segment] + 0.75f * draft.xs[segment + 1];
            zs[near] = 0.75f * draft.zs[segment] + 0.25f * draft.zs[segment + 1];
            zs[far] = 0.25f * draft.zs[segment] + 0.75f * draft.zs[segment + 1];
            catchments[near] = 0.75f * draft.catchments[segment] + 0.25f * draft.catchments[segment + 1];
            catchments[far] = 0.25f * draft.catchments[segment] + 0.75f * draft.catchments[segment + 1];
            carryStep(draft, segment, near, grade, ocean, outletZone, held);
            carryStep(draft, segment + 1, far, grade, ocean, outletZone, held);
        }
        xs[0] = draft.xs[0];
        zs[0] = draft.zs[0];
        catchments[0] = draft.catchments[0];
        carryStep(draft, 0, 0, grade, ocean, outletZone, held);
        xs[size - 1] = draft.xs[count - 1];
        zs[size - 1] = draft.zs[count - 1];
        catchments[size - 1] = draft.catchments[count - 1];
        carryStep(draft, count - 1, size - 1, grade, ocean, outletZone, held);
        draft.xs = xs;
        draft.zs = zs;
        draft.catchments = catchments;
        draft.grade = grade;
        draft.ocean = ocean;
        draft.outletZone = outletZone;
        draft.held = held;
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
        boolean[] held = draft.held == null ? null : new boolean[size];
        for (int index = 0; index < size; index++) {
            carryStep(draft, kept[index], index, grade, ocean, outletZone, held);
            xs[index] = draft.xs[kept[index]];
            zs[index] = draft.zs[kept[index]];
            catchments[index] = draft.catchments[kept[index]];
        }
        draft.xs = xs;
        draft.zs = zs;
        draft.catchments = catchments;
        draft.grade = grade;
        draft.ocean = ocean;
        draft.outletZone = outletZone;
        draft.held = held;
    }

    private static void carryStep(ReachDraft draft, int from, int to, int[] grade, boolean[] ocean, boolean[] outletZone, boolean[] held) {
        grade[to] = draft.grade[from];
        ocean[to] = draft.ocean[from];
        outletZone[to] = draft.outletZone[from];
        if (held != null) {
            held[to] = draft.held[from];
        }
    }

    private static void widths(ReachDraft draft, float upstream, float feederEnd) {
        int count = draft.xs.length;
        boolean flare = draft.incoming.length > 0 && !draft.outlet;
        boolean taper = !flare && !draft.outlet;
        float[] distance = distances(draft.xs, draft.zs);
        Opening estuary = draft.chain.end() == ReachTracer.End.OCEAN ? estuaryOpening(draft.tier.estuary(), draft.ocean, distance)
                : Opening.closed(count);
        float widening = (draft.tier.estuary().flare() - 1f) * estuary.share();
        float running = upstream;
        draft.halfWidth = new float[count];
        draft.depth = new float[count];
        for (int point = 0; point < count; point++) {
            float law = draft.tier.halfWidth(draft.catchments[point]);
            if (flare) {
                law = LinkBlend.blend(feederEnd, law, distance[point]);
            } else if (taper) {
                law *= Math.min(1f, distance[point] / TAPER_LENGTH);
            }
            float lip = draft.held != null && draft.held[point] ? LIP_HALF_WIDTH : 0f;
            float channel = Math.max(Math.max(law, WAIST * running), lip);
            float progress = estuary.progress()[point];
            draft.halfWidth[point] = Math.min(HydrologySettings.MAX_HALF_WIDTH, channel * (1f + widening * progress));
            running = Math.max(running, channel);
            float depth = draft.tier.depth(channel);
            draft.depth[point] = depth + (HydrologyTier.MINIMUM_DEPTH - depth) * progress;
        }
        draft.maxHalfWidth = running;
    }

    static Opening estuaryOpening(HydrologyTier.Estuary estuary, boolean[] ocean, float[] distance) {
        int count = distance.length;
        if (estuary.length() == 0) {
            return Opening.closed(count);
        }
        float mouth = distance[count - 1];
        int start = count - 1;
        while (start > 0 && ocean[start - 1] && mouth - distance[start - 1] <= estuary.length()) {
            start--;
        }
        float first = towardMouth(mouth - distance[start], estuary.length());
        float share = 1f - first * first;
        if (start == count - 1 || share <= 0f) {
            return Opening.closed(count);
        }
        float[] progress = new float[count];
        for (int point = start + 1; point < count; point++) {
            float toward = towardMouth(mouth - distance[point], estuary.length());
            progress[point] = (toward * toward - first * first) / share;
        }
        return new Opening(progress, share);
    }

    private static float towardMouth(float remaining, int length) {
        return Math.clamp(1f - remaining / length, 0f, 1f);
    }

    public static ReachStyle[] styles(int[] grade, boolean[] ocean, boolean[] outletZone, int waterfallMinimumDrop) {
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

    record Opening(float[] progress, float share) {
        static Opening closed(int count) {
            return new Opening(new float[count], 0f);
        }
    }

    private record Fan(long id, float x, float z, double heading, double angle, double bend, float halfWidth) {
    }
}
