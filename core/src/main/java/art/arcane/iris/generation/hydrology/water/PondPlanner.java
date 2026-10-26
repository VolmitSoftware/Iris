package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.Basin;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.CellHeap;
import art.arcane.iris.generation.hydrology.grid.D8Router;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.volmlib.util.noise.SimplexNoise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PondPlanner {
    private static final int LEVEL_ATTEMPTS = 3;
    private static final int LEVEL_ROUNDS = 16;
    private static final int LAKE_STEP = 16;
    private static final int REFINE_SAMPLES = 64;
    private static final double SHORE_WAVELENGTH = 16D;
    private static final double SIMPLEX_FEATURE_SIZE = 100D;
    private static final long SALT_NATURAL = 0x504f4e444e4154L;
    private static final long SALT_SEEDED = 0x504f4e44534545L;
    private static final long SALT_CHAINED = 0x504f4e44434841L;
    private static final long SALT_LAKE = 0x504f4e444c414bL;
    private static final long SALT_KEEP = 0x504f4e444b4545L;
    private static final long SALT_FREEBOARD = 0x504f4e44465242L;
    private static final long SALT_SHORE = 0x53484f52454e5aL;
    private static final int[] AXIS_X = {1, -1, 0, 0};
    private static final int[] AXIS_Z = {0, 0, 1, -1};

    private final HydrologySettings settings;
    private final CupCarver carver;
    private final SimplexNoise shore;

    public PondPlanner(HydrologySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.carver = new CupCarver(settings);
        this.shore = new SimplexNoise(CellHash.mix(settings.seed(), SALT_SHORE, 0, 0));
    }

    public CupSet subtractCups(DemGrid raw, WindowKey key, HydrologySampleBuffer samples, BitSet channelHint) {
        return carver.carve(raw, key, samples, channelHint);
    }

    public List<LakeCandidate> lakeCandidates(DemGrid raw, List<Basin> basins) {
        List<LakeCandidate> candidates = new ArrayList<>();
        for (Basin basin : basins) {
            if (qualifies(raw, basin, PondKind.LAKE)) {
                candidates.add(new LakeCandidate(raw.blockX(basin.floorIndex()), raw.blockZ(basin.floorIndex()),
                        raw.values()[basin.floorIndex()], raw.blockX(basin.spillIndex()), raw.blockZ(basin.spillIndex()),
                        basin.spillHeight(), radius(raw, basin), raw.cell(), worldCells(raw, basin)));
            }
        }
        return List.copyOf(candidates);
    }

    private static long[] worldCells(DemGrid raw, Basin basin) {
        long[] cells = new long[basin.cells().length];
        for (int index = 0; index < cells.length; index++) {
            int cell = basin.cells()[index];
            cells[index] = LakeCandidate.pack(Math.floorDiv(raw.blockX(cell), raw.cell()), Math.floorDiv(raw.blockZ(cell), raw.cell()));
        }
        Arrays.sort(cells);
        return cells;
    }

    public List<Pond> lakes(List<LakeCandidate> candidates, HeightLattice fine) {
        List<LakeCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble(LakeCandidate::floorHeight).thenComparingInt(LakeCandidate::floorX)
                .thenComparingInt(LakeCandidate::floorZ));
        List<Pond> accepted = new ArrayList<>();
        for (LakeCandidate candidate : ordered) {
            Pond lake = lake(candidate, fine);
            if (lake != null && !overlapsAny(lake, accepted)) {
                accepted.add(lake);
            }
        }
        return List.copyOf(accepted);
    }

    public List<Pond> ponds(DemGrid raw, float[] filled, List<Basin> basins, List<Pond> regionLakes, HeightLattice fine,
                            CupSet cups) {
        if (filled.length != raw.values().length) {
            throw new IllegalArgumentException("Filled surface has " + filled.length + " cells, grid has " + raw.values().length);
        }
        List<Pond> accepted = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (Basin basin : basins) {
            Candidate candidate = covered(raw, basin, regionLakes) ? null : candidate(raw, basin, cups, fine);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        float[] heights = raw.values();
        candidates.sort(Comparator.<Candidate>comparingDouble(candidate -> heights[candidate.basin().floorIndex()])
                .thenComparingInt(Candidate::floorX).thenComparingInt(Candidate::floorZ));
        List<Candidate> kept = new ArrayList<>();
        List<Pond> measured = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Pond pond = measure(raw, fine, cups, candidate, Integer.MAX_VALUE);
            if (pond != null && !overlapsAny(pond, accepted) && !touchesLake(pond, regionLakes)) {
                accepted.add(pond);
                kept.add(candidate);
                measured.add(pond);
            }
        }
        settleChains(raw, filled, kept, measured, cups, fine);
        List<Pond> ponds = new ArrayList<>();
        for (Pond pond : measured) {
            if (pond != null) {
                ponds.add(pond);
            }
        }
        return List.copyOf(ponds);
    }

    public Pond reflood(DemGrid raw, HeightLattice fine, CupSet cups, Pond pond) {
        PondMask mask = pond.mask();
        int step = mask.step();
        Box box = box(raw, fine, mask.originX() - step, mask.originZ() - step, mask.originX() + mask.width() * step,
                mask.originZ() + mask.depth() * step, cups, Math.max(1, step / fine.cell()));
        if (box == null) {
            return null;
        }
        float[] ground = box.ground();
        boolean[] inside = new boolean[ground.length];
        int floor = -1;
        for (int node = 0; node < ground.length; node++) {
            inside[node] = mask.contains(box.x(node), box.z(node));
            if (inside[node] && (floor < 0 || ground[node] < ground[floor])) {
                floor = node;
            }
        }
        for (int level = pond.level(); floor >= 0 && ground[floor] < level; level--) {
            boolean[] wet = floodWithin(box, ground, inside, floor, level);
            if (pond.damRaise() > 0 || contained(box, ground, wet, level)) {
                float lowest = Float.POSITIVE_INFINITY;
                for (int node = 0; node < wet.length; node++) {
                    if (wet[node]) {
                        lowest = Math.min(lowest, ground[node]);
                    }
                }
                return new Pond(pond.id(), pond.kind(), level, (int) Math.floor(lowest), pond.spillX(), pond.spillZ(), pond.spillHeight(),
                        mask(box, ground, wet, level), pond.outletReachId(), pond.damRaise(), pond.fluid(), pond.floorX(), pond.floorZ());
            }
        }
        return null;
    }

    private boolean[] floodWithin(Box box, float[] ground, boolean[] inside, int floor, int level) {
        float[] lift = box.lift();
        boolean[] wet = new boolean[ground.length];
        int[] queue = new int[ground.length];
        int head = 0;
        int tail = 0;
        wet[floor] = true;
        queue[tail++] = floor;
        while (head < tail) {
            int node = queue[head++];
            int i = node % box.width();
            int j = node / box.width();
            for (int axis = 0; axis < AXIS_X.length; axis++) {
                int ni = i + AXIS_X[axis];
                int nj = j + AXIS_Z[axis];
                if (ni < 0 || nj < 0 || ni >= box.width() || nj >= box.depth()) {
                    continue;
                }
                int neighbour = nj * box.width() + ni;
                if (wet[neighbour] || !inside[neighbour] || !(ground[neighbour] - lift[neighbour] < level)
                        || wall(box, ground, node, neighbour, axis, level)) {
                    continue;
                }
                wet[neighbour] = true;
                queue[tail++] = neighbour;
            }
        }
        return wet;
    }

    private static boolean touchesLake(Pond pond, List<Pond> lakes) {
        PondMask mask = pond.mask();
        for (Pond lake : lakes) {
            for (int j = 0; j < mask.depth(); j++) {
                for (int i = 0; i < mask.width(); i++) {
                    int x = mask.originX() + i * mask.step();
                    int z = mask.originZ() + j * mask.step();
                    if (mask.contains(x, z) && lake.mask().grownContains(x, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean overlapsAny(Pond pond, List<Pond> accepted) {
        for (Pond other : accepted) {
            if (pond.mask().overlaps(other.mask())) {
                return true;
            }
        }
        return false;
    }

    private Candidate candidate(DemGrid raw, Basin basin, CupSet cups, HeightLattice fine) {
        PondKind kind = cups.kind(raw, basin);
        if (!qualifies(raw, basin, kind)) {
            return null;
        }
        int floorX = raw.blockX(basin.floorIndex());
        int floorZ = raw.blockZ(basin.floorIndex());
        HydrologySampleBuffer floor = sampleAt(fine, floorX, floorZ);
        if ((floor.permission()[0] & HydrologySampleBuffer.ALLOW_PONDS) == 0) {
            return null;
        }
        if (kind == PondKind.NATURAL && CellHash.unit(CellHash.mix(settings.seed(), SALT_KEEP, floorX, floorZ))
                >= Math.clamp(settings.ponds().natural().keep() * floor.pondDensity()[0], 0f, 1f) * settings.density()) {
            return null;
        }
        return new Candidate(basin, kind, floorX, floorZ, floor.rainfall()[0], fluid(floor));
    }

    private Pond lake(LakeCandidate candidate, HeightLattice fine) {
        if (CellHash.unit(CellHash.mix(settings.seed(), SALT_KEEP, candidate.floorX(), candidate.floorZ())) >= settings.density()) {
            return null;
        }
        HydrologySampleBuffer floor = sampleAt(fine, candidate.floorX(), candidate.floorZ());
        if ((floor.permission()[0] & HydrologySampleBuffer.ALLOW_PONDS) == 0) {
            return null;
        }
        return measureLake(fine, new LakeSite(candidate, floor.rainfall()[0], fluid(floor)));
    }

    private String fluid(HydrologySampleBuffer floor) {
        return floor.fluidProfile()[0] == null ? settings.ponds().fluid() : floor.fluidProfile()[0];
    }

    private boolean qualifies(DemGrid raw, Basin basin, PondKind kind) {
        long area = (long) basin.cells().length * raw.cell() * raw.cell();
        HydrologySettings.NaturalPonds natural = settings.ponds().natural();
        HydrologySettings.Lakes lakes = settings.lakes();
        return switch (kind) {
            case LAKE -> lakes.enabled() && area >= lakes.minArea() && area <= lakes.maxArea() && basin.maxDepth() >= natural.minDepth()
                    && (long) basin.span(raw.width()) * raw.cell() <= LakeCandidate.MAX_EXTENT;
            case NATURAL -> natural.enabled() && area >= natural.minArea() && area <= natural.maxArea() && basin.maxDepth() >= natural.minDepth();
            case SEEDED, CHAINED -> true;
        };
    }

    private Pond measure(DemGrid raw, HeightLattice fine, CupSet cups, Candidate candidate, int levelCap) {
        int reach = 2 * radius(raw, candidate.basin());
        Box box = box(raw, fine, candidate.floorX() - reach, candidate.floorZ() - reach,
                candidate.floorX() + reach, candidate.floorZ() + reach, cups, 1);
        if (box == null) {
            return null;
        }
        int floor = descend(box, box.nearest(candidate.floorX(), candidate.floorZ()));
        if (box.border(floor)) {
            return null;
        }
        float[] ground = box.ground();
        Spill spill = spill(box, ground, floor, null);
        int damRaise = 0;
        if (settings.ponds().dams().enabled() && spill.height() > ground[floor]) {
            ground = dam(box, ground, floor, spill.height());
            Spill raised = spill(box, ground, floor, null);
            damRaise = (int) Math.floor(raised.height()) - (int) Math.floor(spill.height());
            spill = raised;
        }
        return flooded(candidate, box, ground, floor, spill, damRaise, levelCap, damRaise > 0 ? null : fine);
    }

    private Pond measureLake(HeightLattice fine, LakeSite site) {
        LakeCandidate lake = site.lake();
        int[] cells = lake.cellBox();
        int margin = LakeCandidate.BOUND_CELLS * lake.cell() + (lake.cell() >> 1);
        Box box = box(fine, cells[0] * lake.cell() - margin, cells[1] * lake.cell() - margin, cells[2] * lake.cell() + margin - 1,
                cells[3] * lake.cell() + margin - 1, CupSet.EMPTY, Math.max(1, LAKE_STEP / fine.cell()), lake);
        if (box == null) {
            return null;
        }
        int floor = descend(box, box.nearest(site.floorX(), site.floorZ()));
        if (box.escapes(floor)) {
            return null;
        }
        int[] parent = new int[box.ground().length];
        float[] drained = drain(box, box.ground(), null, parent);
        Spill spill = top(box, box.ground(), floor, parent);
        Spill refined = refineSpill(fine, box, drained, spill);
        if (refined != null && refined.height() < spill.height()) {
            spill = refined;
        }
        return flooded(site, box, box.ground(), floor, spill, 0, Integer.MAX_VALUE, null);
    }

    private Spill refineSpill(HeightLattice fine, Box box, float[] drained, Spill spill) {
        int step = fine.cell();
        int half = REFINE_SAMPLES / 2 * step;
        Box window = box(fine, spill.x() - half, spill.z() - half, spill.x() + half - step, spill.z() + half - step, CupSet.EMPTY, 1, null);
        if (window == null) {
            return null;
        }
        float[] ground = window.ground();
        float[] levels = ground.clone();
        boolean[] exits = new boolean[ground.length];
        boolean exitFound = false;
        int lowest = -1;
        for (int node = 0; node < ground.length; node++) {
            int coarse = box.nearest(window.x(node), window.z(node));
            if (box.x(coarse) != window.x(node) || box.z(coarse) != window.z(node)) {
                continue;
            }
            if (drained[coarse] < spill.height()) {
                exits[node] = true;
                levels[node] = drained[coarse];
                exitFound = true;
            } else if (ground[node] < spill.height() && (lowest < 0 || ground[node] < ground[lowest])) {
                lowest = node;
            }
        }
        return lowest < 0 || !exitFound ? null : spill(window, levels, lowest, exits);
    }

    private Pond flooded(Site site, Box box, float[] ground, int floor, Spill spill, int damRaise, int levelCap, HeightLattice rim) {
        if (!(spill.height() > ground[floor]) || !(spill.height() > settings.seaLevel())) {
            return null;
        }
        int lowest = Math.max(settings.minY() + 1, settings.seaLevel());
        int level = Math.min(Math.min(Math.max(lowest, (int) Math.floor(spill.height()) - freeboard(site) - drawdown(site.rainfall())),
                settings.maxY() - 1), levelCap);
        int failures = 0;
        for (int round = 0; round < LEVEL_ROUNDS && level >= lowest; round++) {
            if (!(ground[floor] < level)) {
                return null;
            }
            boolean[] wet = flood(box, ground, floor, level);
            if (wet == null || !contained(box, ground, wet, level)) {
                if (++failures >= LEVEL_ATTEMPTS) {
                    return null;
                }
                level--;
                continue;
            }
            PondMask mask = mask(box, ground, wet, level);
            int limit = rim == null ? level : Math.min(level, rimGround(rim, mask) + settings.deformation().maxFill());
            if (limit >= level) {
                return pond(site, level, box, ground, wet, spill, damRaise, mask);
            }
            level = limit;
        }
        return null;
    }

    private static int rimGround(HeightLattice lattice, PondMask mask) {
        Set<Long> columns = new LinkedHashSet<>();
        for (int j = 0; j < mask.depth(); j++) {
            for (int i = 0; i < mask.width(); i++) {
                if (!mask.node(i, j)) {
                    continue;
                }
                for (int axis = 0; axis < AXIS_X.length; axis++) {
                    int ni = i + AXIS_X[axis];
                    int nj = j + AXIS_Z[axis];
                    if (!mask.node(ni, nj)) {
                        int x = mask.originX() + ni;
                        int z = mask.originZ() + nj;
                        columns.add(((long) x << 32) | (z & 0xFFFFFFFFL));
                    }
                }
            }
        }
        int[] xs = new int[columns.size()];
        int[] zs = new int[columns.size()];
        int index = 0;
        for (long column : columns) {
            xs[index] = (int) (column >> 32);
            zs[index] = (int) column;
            index++;
        }
        HydrologySampleBuffer samples = new HydrologySampleBuffer(xs.length);
        lattice.sampleColumns(xs, zs, samples);
        float lowest = Float.POSITIVE_INFINITY;
        for (int column = 0; column < xs.length; column++) {
            lowest = Math.min(lowest, samples.height()[column]);
        }
        return xs.length == 0 ? Integer.MAX_VALUE : (int) Math.floor(lowest);
    }

    private Pond pond(Site site, int level, Box box, float[] ground, boolean[] wet, Spill spill, int damRaise, PondMask mask) {
        float lowest = Float.POSITIVE_INFINITY;
        for (int node = 0; node < wet.length; node++) {
            if (wet[node]) {
                lowest = Math.min(lowest, ground[node]);
            }
        }
        long id = CellHash.mix(settings.seed(), salt(site.kind()), site.floorX(), site.floorZ());
        return new Pond(id, site.kind(), level, (int) Math.floor(lowest), spill.x(), spill.z(), spill.height(),
                mask, 0L, damRaise, site.fluid(), site.floorX(), site.floorZ());
    }

    private static boolean covered(DemGrid raw, Basin basin, List<Pond> lakes) {
        int x = raw.blockX(basin.floorIndex());
        int z = raw.blockZ(basin.floorIndex());
        for (Pond lake : lakes) {
            if (lake.mask().grownContains(x, z)) {
                return true;
            }
        }
        return false;
    }

    private void settleChains(DemGrid raw, float[] filled, List<Candidate> candidates, List<Pond> measured, CupSet cups,
                              HeightLattice fine) {
        int[] owner = new int[filled.length];
        Arrays.fill(owner, -1);
        List<Long> order = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).kind() == PondKind.CHAINED) {
                mark(candidates.get(index).basin(), owner, index);
                float level = filled[candidates.get(index).basin().floorIndex()];
                order.add(((long) ~CellHeap.sortable(level) << 32) | index);
            }
        }
        if (order.size() < 2) {
            return;
        }
        int[] receivers = D8Router.receivers(raw, filled);
        order.sort(Comparator.naturalOrder());
        for (long key : order) {
            int index = (int) key;
            while (measured.get(index) != null) {
                int target = downstream(receivers, owner, candidates.get(index).basin().spillIndex(), index);
                if (target < 0 || measured.get(target).level() < measured.get(index).level()) {
                    break;
                }
                Pond lowered = measure(raw, fine, cups, candidates.get(target), measured.get(index).level() - 1);
                measured.set(target, lowered);
                if (lowered == null) {
                    mark(candidates.get(target).basin(), owner, -1);
                }
            }
        }
    }

    private static int downstream(int[] receivers, int[] owner, int start, int self) {
        int cell = start;
        for (int steps = 0; cell >= 0 && steps < receivers.length; steps++) {
            if (owner[cell] >= 0 && owner[cell] != self) {
                return owner[cell];
            }
            cell = receivers[cell];
        }
        return -1;
    }

    private static void mark(Basin basin, int[] owner, int value) {
        for (int cell : basin.cells()) {
            owner[cell] = value;
        }
    }

    private Box box(DemGrid raw, HeightLattice fine, int minX, int minZ, int maxX, int maxZ, CupSet cups, int stride) {
        int lastX = raw.originX() + (raw.width() - 1) * raw.cell();
        int lastZ = raw.originZ() + (raw.depth() - 1) * raw.cell();
        return box(fine, Math.max(minX, raw.originX()), Math.max(minZ, raw.originZ()), Math.min(maxX, lastX), Math.min(maxZ, lastZ),
                cups, stride, null);
    }

    private Box box(HeightLattice fine, int minX, int minZ, int maxX, int maxZ, CupSet cups, int stride, LakeCandidate bound) {
        int step = fine.cell() * stride;
        int originX = Math.ceilDiv(minX, step) * step;
        int originZ = Math.ceilDiv(minZ, step) * step;
        int lastX = Math.floorDiv(maxX, step) * step;
        int lastZ = Math.floorDiv(maxZ, step) * step;
        int width = (lastX - originX) / step + 1;
        int depth = (lastZ - originZ) / step + 1;
        if (width < 3 || depth < 3) {
            return null;
        }
        HydrologySampleBuffer samples = new HydrologySampleBuffer(width * depth);
        fine.fill(originX / fine.cell(), originZ / fine.cell(), width, depth, stride, samples);
        List<CupSet.Cup> near = cups.within(originX, originZ, lastX, lastZ);
        float[] ground = samples.height();
        boolean[] ocean = samples.ocean();
        float[] lift = new float[ground.length];
        boolean[] inside = bound == null ? null : new boolean[ground.length];
        boolean rounded = settings.ponds().shore().roundness() > 0f;
        for (int node = 0; node < ground.length; node++) {
            int x = originX + (node % width) * step;
            int z = originZ + (node / width) * step;
            for (CupSet.Cup cup : near) {
                ground[node] = Math.min(ground[node], cup.floorAt(x, z));
            }
            lift[node] = rounded ? shoreLift(x, z) : 0f;
            if (inside != null) {
                inside[node] = bound.bounds(x, z);
            }
        }
        return new Box(originX, originZ, width, depth, step, ground, lift, ocean, inside);
    }

    private float shoreLift(int x, int z) {
        double scale = SIMPLEX_FEATURE_SIZE / SHORE_WAVELENGTH;
        double signed = shore.noiseSigned(x * scale, z * scale);
        return (float) (settings.ponds().shore().roundness() * 0.5D * (1D + Math.clamp(signed, -1D, 1D)));
    }

    private static int descend(Box box, int start) {
        float[] ground = box.ground();
        int[] around = new int[8];
        int node = start;
        while (true) {
            int next = node;
            int count = box.around(node, around);
            for (int index = 0; index < count; index++) {
                if (ground[around[index]] < ground[next]) {
                    next = around[index];
                }
            }
            if (next == node) {
                return node;
            }
            node = next;
        }
    }

    private static Spill spill(Box box, float[] ground, int floor, boolean[] exits) {
        int[] parent = new int[ground.length];
        drain(box, ground, exits, parent);
        return top(box, ground, floor, parent);
    }

    private static float[] drain(Box box, float[] ground, boolean[] exits, int[] parent) {
        int count = ground.length;
        float[] filled = new float[count];
        boolean[] closed = new boolean[count];
        int[] around = new int[8];
        CellHeap open = new CellHeap(2 * (box.width() + box.depth()));
        for (int node = 0; node < count; node++) {
            if (box.ocean()[node] || (exits == null ? box.border(node) : exits[node])) {
                filled[node] = ground[node];
                parent[node] = -1;
                closed[node] = true;
                open.push(ground[node], node);
            }
        }
        while (!open.isEmpty()) {
            int node = open.pop();
            int neighbours = box.around(node, around);
            for (int index = 0; index < neighbours; index++) {
                int neighbour = around[index];
                if (!closed[neighbour]) {
                    closed[neighbour] = true;
                    parent[neighbour] = node;
                    filled[neighbour] = Math.max(ground[neighbour], filled[node]);
                    open.push(filled[neighbour], neighbour);
                }
            }
        }
        return filled;
    }

    private static Spill top(Box box, float[] ground, int floor, int[] parent) {
        int top = floor;
        for (int node = floor; node >= 0; node = parent[node]) {
            if (ground[node] > ground[top]) {
                top = node;
            }
        }
        return new Spill(box.x(top), box.z(top), ground[top]);
    }

    private float[] dam(Box box, float[] ground, int floor, float spill) {
        boolean[] inside = new boolean[ground.length];
        int[] queue = new int[ground.length];
        int[] around = new int[8];
        int head = 0;
        int tail = 0;
        inside[floor] = true;
        queue[tail++] = floor;
        while (head < tail) {
            int neighbours = box.around(queue[head++], around);
            for (int index = 0; index < neighbours; index++) {
                int neighbour = around[index];
                if (!inside[neighbour] && ground[neighbour] < spill) {
                    inside[neighbour] = true;
                    queue[tail++] = neighbour;
                }
            }
        }
        float crest = spill + settings.ponds().dams().maxRaise();
        float[] raised = ground.clone();
        for (int member = 0; member < tail; member++) {
            int neighbours = box.around(queue[member], around);
            for (int index = 0; index < neighbours; index++) {
                if (!inside[around[index]]) {
                    raised[around[index]] = Math.max(raised[around[index]], crest);
                }
            }
        }
        return raised;
    }

    private boolean[] flood(Box box, float[] ground, int floor, int level) {
        float[] lift = box.lift();
        boolean[] wet = new boolean[ground.length];
        int[] queue = new int[ground.length];
        if (!(ground[floor] - lift[floor] < level) || box.escapes(floor)) {
            return null;
        }
        int head = 0;
        int tail = 0;
        wet[floor] = true;
        queue[tail++] = floor;
        while (head < tail) {
            int node = queue[head++];
            int i = node % box.width();
            int j = node / box.width();
            for (int axis = 0; axis < AXIS_X.length; axis++) {
                int ni = i + AXIS_X[axis];
                int nj = j + AXIS_Z[axis];
                if (ni < 0 || nj < 0 || ni >= box.width() || nj >= box.depth()) {
                    continue;
                }
                int neighbour = nj * box.width() + ni;
                if (wet[neighbour] || !(ground[neighbour] - lift[neighbour] < level)
                        || wall(box, ground, node, neighbour, axis, level)) {
                    continue;
                }
                if (box.escapes(neighbour)) {
                    return null;
                }
                wet[neighbour] = true;
                queue[tail++] = neighbour;
            }
        }
        return wet;
    }

    private boolean wall(Box box, float[] ground, int from, int to, int axis, int level) {
        if (ground[from] < level && ground[to] < level) {
            return false;
        }
        int step = box.step();
        int x = box.x(from);
        int z = box.z(from);
        for (int offset = 1; offset < step; offset++) {
            float between = ground[from] + (ground[to] - ground[from]) * offset / step;
            if (between - shoreLift(x + AXIS_X[axis] * offset, z + AXIS_Z[axis] * offset) >= level) {
                return true;
            }
        }
        return false;
    }

    private static boolean contained(Box box, float[] ground, boolean[] wet, int level) {
        for (int node = 0; node < wet.length; node++) {
            if (!wet[node]) {
                continue;
            }
            int i = node % box.width();
            int j = node / box.width();
            for (int axis = 0; axis < AXIS_X.length; axis++) {
                int neighbour = (j + AXIS_Z[axis]) * box.width() + i + AXIS_X[axis];
                if (!wet[neighbour] && ground[neighbour] < level) {
                    return false;
                }
            }
        }
        return true;
    }

    private static PondMask mask(Box box, float[] ground, boolean[] wet, int level) {
        int minI = Integer.MAX_VALUE;
        int minJ = Integer.MAX_VALUE;
        int maxI = Integer.MIN_VALUE;
        int maxJ = Integer.MIN_VALUE;
        for (int node = 0; node < wet.length; node++) {
            if (wet[node]) {
                minI = Math.min(minI, node % box.width());
                maxI = Math.max(maxI, node % box.width());
                minJ = Math.min(minJ, node / box.width());
                maxJ = Math.max(maxJ, node / box.width());
            }
        }
        minI = Math.max(0, minI - 1);
        minJ = Math.max(0, minJ - 1);
        maxI = Math.min(box.width() - 1, maxI + 1);
        maxJ = Math.min(box.depth() - 1, maxJ + 1);
        if (box.step() < LAKE_STEP) {
            return shorelineMask(box, ground, wet, level, minI, minJ, maxI, maxJ);
        }
        int width = maxI - minI + 1;
        int depth = maxJ - minJ + 1;
        long[] bits = new long[(width * depth + 63) >>> 6];
        for (int j = minJ; j <= maxJ; j++) {
            for (int i = minI; i <= maxI; i++) {
                if (wet[j * box.width() + i]) {
                    int index = (j - minJ) * width + (i - minI);
                    bits[index >>> 6] |= 1L << index;
                }
            }
        }
        return new PondMask(box.originX() + minI * box.step(), box.originZ() + minJ * box.step(), box.step(), width, depth, bits);
    }

    private static PondMask shorelineMask(Box box, float[] ground, boolean[] wet, int level, int minI, int minJ, int maxI, int maxJ) {
        int step = box.step();
        int half = step >> 1;
        int width = (maxI - minI + 1) * step;
        int depth = (maxJ - minJ + 1) * step;
        long[] bits = new long[(width * depth + 63) >>> 6];
        for (int z = 0; z < depth; z++) {
            int sampleZ = minJ * step + z - half;
            int j = Math.floorDiv(sampleZ, step);
            if (j < 0 || j + 1 >= box.depth()) {
                continue;
            }
            float v = Math.floorMod(sampleZ, step) / (float) step;
            for (int x = 0; x < width; x++) {
                int sampleX = minI * step + x - half;
                int i = Math.floorDiv(sampleX, step);
                if (i < 0 || i + 1 >= box.width()) {
                    continue;
                }
                float u = Math.floorMod(sampleX, step) / (float) step;
                int node = j * box.width() + i;
                if (!wet[node] && !wet[node + 1] && !wet[node + box.width()] && !wet[node + box.width() + 1]) {
                    continue;
                }
                float first = lifted(box, ground, node) * (1f - u) + lifted(box, ground, node + 1) * u;
                float second = lifted(box, ground, node + box.width()) * (1f - u)
                        + lifted(box, ground, node + box.width() + 1) * u;
                if (first * (1f - v) + second * v < level) {
                    int index = z * width + x;
                    bits[index >>> 6] |= 1L << index;
                }
            }
        }
        return new PondMask(box.originX() + minI * step - half, box.originZ() + minJ * step - half, 1, width, depth, bits);
    }

    private static float lifted(Box box, float[] ground, int node) {
        return ground[node] - box.lift()[node];
    }

    private int freeboard(Site site) {
        return CupCarver.pick(settings.ponds().freeboard(),
                CellHash.mix(settings.seed(), SALT_FREEBOARD, site.floorX(), site.floorZ()));
    }

    private int drawdown(float rainfall) {
        return Math.round(settings.ponds().climateDrawdown() * Math.clamp(1f - rainfall, 0f, 1f));
    }

    private static int radius(DemGrid raw, Basin basin) {
        int floorI = basin.floorIndex() % raw.width();
        int floorJ = basin.floorIndex() / raw.width();
        int cells = 0;
        for (int cell : basin.cells()) {
            cells = Math.max(cells, Math.max(Math.abs(cell % raw.width() - floorI), Math.abs(cell / raw.width() - floorJ)));
        }
        return (cells + 1) * raw.cell();
    }

    private static HydrologySampleBuffer sampleAt(HeightLattice fine, int x, int z) {
        int step = fine.cell();
        HydrologySampleBuffer sample = new HydrologySampleBuffer(1);
        fine.fill(Math.floorDiv(x + (step >> 1), step), Math.floorDiv(z + (step >> 1), step), 1, 1, sample);
        return sample;
    }

    private static long salt(PondKind kind) {
        return switch (kind) {
            case NATURAL -> SALT_NATURAL;
            case SEEDED -> SALT_SEEDED;
            case CHAINED -> SALT_CHAINED;
            case LAKE -> SALT_LAKE;
        };
    }

    private interface Site {
        PondKind kind();

        int floorX();

        int floorZ();

        float rainfall();

        String fluid();
    }

    private record Candidate(Basin basin, PondKind kind, int floorX, int floorZ, float rainfall, String fluid) implements Site {
    }

    private record LakeSite(LakeCandidate lake, float rainfall, String fluid) implements Site {
        @Override
        public PondKind kind() {
            return PondKind.LAKE;
        }

        @Override
        public int floorX() {
            return lake.floorX();
        }

        @Override
        public int floorZ() {
            return lake.floorZ();
        }
    }

    private record Spill(int x, int z, float height) {
    }

    private record Box(int originX, int originZ, int width, int depth, int step, float[] ground, float[] lift, boolean[] ocean,
                       boolean[] inside) {
        boolean escapes(int node) {
            return border(node) || (inside != null && !inside[node]);
        }

        int x(int node) {
            return originX + (node % width) * step;
        }

        int z(int node) {
            return originZ + (node / width) * step;
        }

        boolean border(int node) {
            int i = node % width;
            int j = node / width;
            return i == 0 || j == 0 || i == width - 1 || j == depth - 1;
        }

        int around(int node, int[] out) {
            int i = node % width;
            int j = node / width;
            int count = 0;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int ni = i + dx;
                    int nj = j + dz;
                    if ((dx != 0 || dz != 0) && ni >= 0 && nj >= 0 && ni < width && nj < depth) {
                        out[count++] = nj * width + ni;
                    }
                }
            }
            return count;
        }

        int nearest(int blockX, int blockZ) {
            int i = Math.clamp(Math.floorDiv(blockX - originX + (step >> 1), step), 0, width - 1);
            int j = Math.clamp(Math.floorDiv(blockZ - originZ + (step >> 1), step), 0, depth - 1);
            return j * width + i;
        }
    }
}
