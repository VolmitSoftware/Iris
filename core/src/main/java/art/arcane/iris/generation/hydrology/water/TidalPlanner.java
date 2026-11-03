package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TidalPlanner {
    private static final int CELL = WindowPlanner.CELL;
    private static final int[] SIDE_I = {1, -1, 0, 0};
    private static final int[] SIDE_J = {0, 0, 1, -1};
    private static final int MEMO_SITES = 4096;
    private static final int BUILD_ATTEMPTS = 8;
    private static final int TRIM_PASSES = 3;
    private static final float MOUTH_ZONE = 32f;
    private static final float END_SHARE = 0.5f;
    private static final float DIAGONAL = 1.41421356f;
    private static final float VALLEY_WEIGHT = 0.5f;
    private static final float BESIDE_SEA_MARGIN = 10f;
    private static final float BESIDE_SEA_SHARE = 0.4f;
    private static final float TIP_LENGTH = 12f;
    private static final float TIP_HALF_WIDTH = 1.5f;
    private static final float SHOAL_LENGTH = 16f;
    private static final float DEEP_SHARE = 0.5f;
    private static final float SCAN_STEP = 8f;
    private static final float WOBBLE_LONG = 57f;
    private static final float WOBBLE_SHORT = 23f;
    private static final float BESIDE_NEAR = 1f;
    private static final float BESIDE_FAR = 4f;
    private static final float CLEARANCE_MARGIN = 4f;
    private static final int LOW_RIVER = 2;
    private static final float LOW_RIVER_BAND = 4f;
    private static final float WALK_WIDTH = 4f;
    private static final long SALT_RANK = 0x5449444c524bL;
    private static final long SALT_ID = 0x5449444c4944L;
    private static final long SALT_SITE = 0x5449444c53L;
    private static final long SALT_DENSITY = 0x5449444c444eL;
    private static final long SALT_TARGET_X = 0x5449444c5458L;
    private static final long SALT_TARGET_Z = 0x5449444c545aL;
    private static final long SALT_LENGTH = 0x5449444c4c4eL;
    private static final long SALT_SHAPE = 0x5449444c5348L;
    private static final long SALT_MOUTH = 0x5449444c4d4fL;
    private static final long SALT_HEAD = 0x5449444c4845L;
    private static final long SALT_DEPTH = 0x5449444c4450L;
    private static final long SALT_WOBBLE = 0x5449444c5742L;

    private final HydrologySettings settings;
    private final HydrologySettings.SeaInlets config;
    private final HeightLattice fine;
    private final int stride;
    private final long seed;
    private final float territory;
    private final Map<Long, Anchor> anchors = new LinkedHashMap<>(MEMO_SITES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Anchor> eldest) {
            return size() > MEMO_SITES;
        }
    };

    public TidalPlanner(HydrologySettings settings, HeightLattice fine) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.config = settings.underground().seaInlets();
        this.fine = Objects.requireNonNull(fine, "fine");
        this.stride = CELL / fine.cell();
        this.seed = settings.seed();
        this.territory = 2f * config.mouthHalfWidth().max() + config.bankBand();
    }

    public List<Reach> plan(WindowKey window, DemGrid natural, HydrologySampleBuffer samples, ShapedNetwork network) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(network, "network");
        if (!config.enabled() || settings.density() <= 0f) {
            return List.of();
        }
        List<Anchor> own = own(window);
        if (own.isEmpty()) {
            return List.of();
        }
        Land land = new Land(natural, samples);
        float[] coast = coastDistance(land);
        boolean[] wet = wet(natural, network);
        ReachFitter fitter = new ReachFitter(fine, new PondLookup(network.ponds()));
        List<Reach> planned = new ArrayList<>();
        for (Anchor anchor : own) {
            List<Anchor> rivals = rivals(anchor);
            if (suppressed(anchor, rivals)) {
                continue;
            }
            Reach reach = site(window, land, coast, wet, anchor, rivals, fitter, network);
            if (reach != null) {
                requireWithinReach(window, reach);
                planned.add(reach);
            }
        }
        planned.sort(Comparator.comparingLong(Reach::id));
        return List.copyOf(planned);
    }

    private List<Anchor> own(WindowKey window) {
        int spacing = config.spacing();
        int minSiteX = Math.floorDiv(window.wx() * WindowKey.SIZE, spacing);
        int maxSiteX = Math.floorDiv((window.wx() + 1) * WindowKey.SIZE - 1, spacing);
        int minSiteZ = Math.floorDiv(window.wz() * WindowKey.SIZE, spacing);
        int maxSiteZ = Math.floorDiv((window.wz() + 1) * WindowKey.SIZE - 1, spacing);
        List<Anchor> own = new ArrayList<>();
        for (int siteZ = minSiteZ; siteZ <= maxSiteZ; siteZ++) {
            for (int siteX = minSiteX; siteX <= maxSiteX; siteX++) {
                Anchor anchor = anchor(siteX, siteZ);
                if (anchor.active() && anchor.owner().equals(window)) {
                    own.add(anchor);
                }
            }
        }
        return own;
    }

    private Anchor anchor(int siteX, int siteZ) {
        long key = ((long) siteX << 32) | (siteZ & 0xFFFFFFFFL);
        synchronized (anchors) {
            Anchor known = anchors.get(key);
            if (known != null) {
                return known;
            }
        }
        Anchor anchor = locate(siteX, siteZ);
        synchronized (anchors) {
            anchors.put(key, anchor);
        }
        return anchor;
    }

    private Anchor locate(int siteX, int siteZ) {
        int spacing = config.spacing();
        double targetX = (siteX + CellHash.unit(CellHash.mix(seed, SALT_TARGET_X, siteX, siteZ))) * spacing;
        double targetZ = (siteZ + CellHash.unit(CellHash.mix(seed, SALT_TARGET_Z, siteX, siteZ))) * spacing;
        WindowKey owner = WindowKey.containing((int) Math.floor(targetX), (int) Math.floor(targetZ));
        if (settings.density() < 1f && CellHash.unit(CellHash.mix(seed, SALT_DENSITY, siteX, siteZ)) >= settings.density()) {
            return new Anchor(siteX, siteZ, owner, targetX, targetZ, false, 0, 0, 0L);
        }
        int firstX = Math.ceilDiv(siteX * spacing, CELL) * CELL;
        int firstZ = Math.ceilDiv(siteZ * spacing, CELL) * CELL;
        int cellsX = Math.ceilDiv((siteX + 1) * spacing - firstX, CELL);
        int cellsZ = Math.ceilDiv((siteZ + 1) * spacing - firstZ, CELL);
        Land land = land(firstX - CELL, firstZ - CELL, cellsX + 2, cellsZ + 2);
        DemGrid grid = land.grid();
        int bestX = 0;
        int bestZ = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int j = 1; j <= cellsZ; j++) {
            for (int i = 1; i <= cellsX; i++) {
                int cell = grid.index(i, j);
                int mouth = open(land, cell) ? mouth(land, cell) : -1;
                if (mouth < 0 || !owner.equals(WindowKey.containing(grid.blockX(mouth), grid.blockZ(mouth)))) {
                    continue;
                }
                double distance = Math.hypot(grid.blockX(cell) - targetX, grid.blockZ(cell) - targetZ);
                if (distance < best) {
                    best = distance;
                    bestX = grid.blockX(cell);
                    bestZ = grid.blockZ(cell);
                }
            }
        }
        return new Anchor(siteX, siteZ, owner, targetX, targetZ, best != Double.POSITIVE_INFINITY, bestX, bestZ, CellHash.mix(seed, SALT_RANK, siteX, siteZ));
    }

    private List<Anchor> rivals(Anchor anchor) {
        double reach = 2D * (config.length().max() + 2 * CELL) + territory;
        int spacing = config.spacing();
        int minSiteX = Math.floorDiv((int) Math.floor(anchor.x() - reach), spacing);
        int maxSiteX = Math.floorDiv((int) Math.ceil(anchor.x() + reach), spacing);
        int minSiteZ = Math.floorDiv((int) Math.floor(anchor.z() - reach), spacing);
        int maxSiteZ = Math.floorDiv((int) Math.ceil(anchor.z() + reach), spacing);
        List<Anchor> rivals = new ArrayList<>();
        for (int siteZ = minSiteZ; siteZ <= maxSiteZ; siteZ++) {
            for (int siteX = minSiteX; siteX <= maxSiteX; siteX++) {
                Anchor other = anchor(siteX, siteZ);
                if (other.active() && (other.siteX() != anchor.siteX() || other.siteZ() != anchor.siteZ())) {
                    rivals.add(other);
                }
            }
        }
        return rivals;
    }

    private boolean suppressed(Anchor anchor, List<Anchor> rivals) {
        for (Anchor rival : rivals) {
            if (rival.rank() < anchor.rank() && Math.hypot(rival.x() - anchor.x(), rival.z() - anchor.z()) < territory) {
                return true;
            }
        }
        return false;
    }

    private boolean inTerritory(Anchor anchor, List<Anchor> rivals, double x, double z) {
        double own = Math.hypot(x - anchor.x(), z - anchor.z());
        for (Anchor rival : rivals) {
            if (own + territory > Math.hypot(x - rival.x(), z - rival.z())) {
                return false;
            }
        }
        return true;
    }

    private Reach site(WindowKey window, Land land, float[] coast, boolean[] wet, Anchor anchor, List<Anchor> rivals, ReachFitter fitter, ShapedNetwork network) {
        DemGrid grid = land.grid();
        int spacing = config.spacing();
        List<Integer> candidates = new ArrayList<>();
        int firstI = Math.ceilDiv(anchor.siteX() * spacing - grid.originX(), CELL);
        int firstJ = Math.ceilDiv(anchor.siteZ() * spacing - grid.originZ(), CELL);
        int lastI = Math.ceilDiv((anchor.siteX() + 1) * spacing - grid.originX(), CELL) - 1;
        int lastJ = Math.ceilDiv((anchor.siteZ() + 1) * spacing - grid.originZ(), CELL) - 1;
        for (int j = Math.max(1, firstJ); j <= Math.min(grid.depth() - 2, lastJ); j++) {
            for (int i = Math.max(1, firstI); i <= Math.min(grid.width() - 2, lastI); i++) {
                int cell = grid.index(i, j);
                int mouth = open(land, cell) && !wet[cell] ? mouth(land, cell) : -1;
                if (mouth >= 0 && window.equals(WindowKey.containing(grid.blockX(mouth), grid.blockZ(mouth)))
                        && inTerritory(anchor, rivals, grid.blockX(cell), grid.blockZ(cell))) {
                    candidates.add(cell);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble((Integer cell) -> Math.hypot(grid.blockX(cell) - anchor.targetX(), grid.blockZ(cell) - anchor.targetZ())).thenComparingInt(cell -> cell));
        int attempts = 0;
        for (int cell : candidates) {
            int[] cells = trace(land, coast, wet, anchor, rivals, cell);
            if (cells == null) {
                continue;
            }
            Reach reach = shape(land, cells, anchor, rivals, fitter, network);
            if (reach != null) {
                return reach;
            }
            if (++attempts >= BUILD_ATTEMPTS) {
                return null;
            }
        }
        return null;
    }

    private boolean[] wet(DemGrid grid, ShapedNetwork network) {
        boolean[] wet = new boolean[grid.values().length];
        for (Reach river : network.reaches()) {
            float band = Math.min(river.tier().bankBand(), config.bankBand()) + CLEARANCE_MARGIN + WALK_WIDTH;
            for (int point = 0; point < river.xs().length; point += 2) {
                float apart = river.grade()[point] - settings.seaLevel() <= LOW_RIVER ? CLEARANCE_MARGIN + LOW_RIVER_BAND : band;
                mark(grid, wet, river.xs()[point], river.zs()[point], river.halfWidth()[point] + apart);
            }
        }
        for (Pond pond : network.ponds()) {
            PondMask mask = pond.mask();
            float radius = 1.5f * mask.step() + Math.max(settings.ponds().shore().taper(), config.bankBand()) + CLEARANCE_MARGIN;
            for (int j = 0; j < mask.depth(); j++) {
                for (int i = 0; i < mask.width(); i++) {
                    if (mask.node(i, j)) {
                        mark(grid, wet, mask.originX() + i * mask.step(), mask.originZ() + j * mask.step(), radius);
                    }
                }
            }
        }
        return wet;
    }

    private static void mark(DemGrid grid, boolean[] wet, float x, float z, float radius) {
        int cell = grid.cell();
        int minI = Math.max(0, (int) Math.floor((x - radius - grid.originX()) / cell));
        int maxI = Math.min(grid.width() - 1, (int) Math.ceil((x + radius - grid.originX()) / cell));
        int minJ = Math.max(0, (int) Math.floor((z - radius - grid.originZ()) / cell));
        int maxJ = Math.min(grid.depth() - 1, (int) Math.ceil((z + radius - grid.originZ()) / cell));
        float squared = radius * radius;
        for (int j = minJ; j <= maxJ; j++) {
            float dz = grid.originZ() + j * cell - z;
            for (int i = minI; i <= maxI; i++) {
                float dx = grid.originX() + i * cell - x;
                if (dx * dx + dz * dz <= squared) {
                    wet[j * grid.width() + i] = true;
                }
            }
        }
    }

    private Land land(int originX, int originZ, int width, int depth) {
        HydrologySampleBuffer samples = new HydrologySampleBuffer(width * depth);
        fine.fill(Math.floorDiv(originX, fine.cell()), Math.floorDiv(originZ, fine.cell()), width, depth, stride, samples);
        DemGrid grid = new DemGrid(originX, originZ, CELL, width, depth);
        System.arraycopy(samples.height(), 0, grid.values(), 0, width * depth);
        return new Land(grid, samples);
    }

    private boolean open(Land land, int cell) {
        HydrologySampleBuffer samples = land.samples();
        float height = samples.height()[cell];
        return !samples.ocean()[cell] && height >= settings.seaLevel() + 1 && height <= settings.seaLevel() + config.maximumBankHeight()
                && (samples.permission()[cell] & HydrologySampleBuffer.ALLOW_STREAMS) != 0;
    }

    private static int mouth(Land land, int cell) {
        DemGrid grid = land.grid();
        int i = cell % grid.width();
        int j = cell / grid.width();
        for (int side = 0; side < SIDE_I.length; side++) {
            int ni = i + SIDE_I[side];
            int nj = j + SIDE_J[side];
            if (ni >= 0 && nj >= 0 && ni < grid.width() && nj < grid.depth() && land.samples().ocean()[grid.index(ni, nj)]) {
                return grid.index(ni, nj);
            }
        }
        return -1;
    }

    private static float[] coastDistance(Land land) {
        DemGrid grid = land.grid();
        boolean[] ocean = land.samples().ocean();
        int width = grid.width();
        int depth = grid.depth();
        float[] distance = new float[width * depth];
        Arrays.fill(distance, Float.POSITIVE_INFINITY);
        for (int cell = 0; cell < distance.length; cell++) {
            if (ocean[cell]) {
                distance[cell] = 0f;
            }
        }
        float straight = CELL;
        float diagonal = CELL * DIAGONAL;
        for (int pass = 0; pass < 2; pass++) {
            int step = pass == 0 ? -1 : 1;
            for (int b = 0; b < depth; b++) {
                int j = pass == 0 ? b : depth - 1 - b;
                for (int a = 0; a < width; a++) {
                    int i = pass == 0 ? a : width - 1 - a;
                    int cell = grid.index(i, j);
                    float best = distance[cell];
                    if (i + step >= 0 && i + step < width) {
                        best = Math.min(best, distance[grid.index(i + step, j)] + straight);
                    }
                    if (j + step >= 0 && j + step < depth) {
                        best = Math.min(best, distance[grid.index(i, j + step)] + straight);
                        if (i + step >= 0 && i + step < width) {
                            best = Math.min(best, distance[grid.index(i + step, j + step)] + diagonal);
                        }
                        if (i - step >= 0 && i - step < width) {
                            best = Math.min(best, distance[grid.index(i - step, j + step)] + diagonal);
                        }
                    }
                    distance[cell] = best;
                }
            }
        }
        return distance;
    }

    private int[] trace(Land land, float[] coast, boolean[] wet, Anchor anchor, List<Anchor> rivals, int start) {
        DemGrid grid = land.grid();
        int mouth = mouth(land, start);
        double unit = CellHash.unit(CellHash.mix(seed, SALT_LENGTH, grid.blockX(start), grid.blockZ(start)));
        float target = (float) (config.length().min() + (config.length().max() - config.length().min()) * unit);
        List<Integer> path = new ArrayList<>();
        path.add(mouth);
        path.add(start);
        int current = start;
        float length = CELL;
        float[] height = land.samples().height();
        while (length < target) {
            int i = current % grid.width();
            int j = current / grid.width();
            int best = -1;
            float bestScore = Float.POSITIVE_INFINITY;
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    int ni = i + di;
                    int nj = j + dj;
                    if (di == 0 && dj == 0 || ni < 1 || nj < 1 || ni >= grid.width() - 1 || nj >= grid.depth() - 1) {
                        continue;
                    }
                    int next = grid.index(ni, nj);
                    if (coast[next] <= coast[current] || wet[next] || !open(land, next) || !inTerritory(anchor, rivals, grid.blockX(next), grid.blockZ(next))) {
                        continue;
                    }
                    float score = height[next] - VALLEY_WEIGHT * (coast[next] - coast[current]);
                    if (score < bestScore || score == bestScore && next < best) {
                        bestScore = score;
                        best = next;
                    }
                }
            }
            if (best < 0) {
                break;
            }
            length += (float) Math.hypot(grid.blockX(best) - grid.blockX(current), grid.blockZ(best) - grid.blockZ(current));
            path.add(best);
            current = best;
        }
        if (length < config.length().min() || coast[current] < END_SHARE * length) {
            return null;
        }
        int[] cells = new int[path.size()];
        for (int index = 0; index < cells.length; index++) {
            cells[index] = path.get(cells.length - 1 - index);
        }
        return cells;
    }

    private Reach shape(Land land, int[] cells, Anchor anchor, List<Anchor> rivals, ReachFitter fitter, ShapedNetwork network) {
        DemGrid grid = land.grid();
        int last = cells.length - 1;
        int mouthX = grid.blockX(cells[last]);
        int mouthZ = grid.blockZ(cells[last]);
        int startX = grid.blockX(cells[last - 1]);
        int startZ = grid.blockZ(cells[last - 1]);
        long id = CellHash.mix(CellHash.mix(seed, SALT_ID, mouthX, mouthZ), SALT_SITE, startX, startZ);
        long shape = CellHash.mix(id, SALT_SHAPE, 0, 0);
        HydrologyTier tier = settings.tidalTier();
        ReachFitter.Fitted fitted = fitter.fit(grid, cells, new float[cells.length], tier, shape, true);
        ReachDraft draft = new ReachDraft(new ReachTracer.Chain(cells, ReachTracer.End.OCEAN, -1), id, tier, 0f, Integer.MIN_VALUE, fitted);
        draft.grade = new int[draft.xs.length];
        Arrays.fill(draft.grade, settings.seaLevel());
        draft.outletZone = new boolean[draft.xs.length];
        ReachShaper.smooth(draft);
        float[] xs = draft.xs;
        float[] zs = draft.zs;
        for (int pass = 0; pass < TRIM_PASSES; pass++) {
            int head = Math.max(groundedHead(xs, zs, id, shape), Math.max(territoryHead(xs, zs, shape, anchor, rivals), clearHead(xs, zs, shape, network)));
            if (head < 0) {
                return null;
            }
            float[] distance = ReachShaper.distances(xs, zs);
            if (distance[distance.length - 1] - distance[head] < config.length().min()) {
                return null;
            }
            if (head == 0) {
                Reach reach = reach(id, shape, xs, zs);
                if (besideSea(reach)) {
                    return null;
                }
                return reach;
            }
            xs = Arrays.copyOfRange(xs, head, xs.length);
            zs = Arrays.copyOfRange(zs, head, zs.length);
        }
        return null;
    }

    private int territoryHead(float[] xs, float[] zs, long shape, Anchor anchor, List<Anchor> rivals) {
        float[] distance = ReachShaper.distances(xs, zs);
        for (int point = xs.length - 1; point >= 0; point--) {
            if (!inTerritory(anchor, rivals, xs[point], zs[point])) {
                float keep = distance[point] + SCAN_STEP;
                int head = point;
                while (head < xs.length - 1 && distance[head] < keep) {
                    head++;
                }
                return head;
            }
        }
        return 0;
    }

    private int clearHead(float[] xs, float[] zs, long shape, ShapedNetwork network) {
        float[] distance = ReachShaper.distances(xs, zs);
        float[] widths = widths(shape, distance);
        float keep = 0f;
        for (Reach river : network.reaches()) {
            float band = Math.min(river.tier().bankBand(), config.bankBand()) + CLEARANCE_MARGIN;
            for (int segment = 0; segment < river.xs().length; segment++) {
                float apart = river.grade()[segment] - settings.seaLevel() <= LOW_RIVER ? CLEARANCE_MARGIN + LOW_RIVER_BAND : band;
                keep = Math.max(keep, blocked(xs, zs, widths, distance, river.xs()[segment], river.zs()[segment], river.halfWidth()[segment] + apart));
            }
        }
        for (Pond pond : network.ponds()) {
            PondMask mask = pond.mask();
            float radius = 1.5f * mask.step() + Math.max(settings.ponds().shore().taper(), config.bankBand()) + CLEARANCE_MARGIN;
            for (int j = 0; j < mask.depth(); j++) {
                for (int i = 0; i < mask.width(); i++) {
                    if (mask.node(i, j)) {
                        keep = Math.max(keep, blocked(xs, zs, widths, distance, mask.originX() + i * mask.step(), mask.originZ() + j * mask.step(), radius));
                    }
                }
            }
        }
        if (keep <= 0f) {
            return 0;
        }
        int head = 0;
        while (head < distance.length - 1 && distance[head] < keep) {
            head++;
        }
        return head;
    }

    private static float blocked(float[] xs, float[] zs, float[] widths, float[] distance, float x, float z, float radius) {
        for (int point = xs.length - 1; point >= 0; point--) {
            float gap = widths[point] + radius;
            float dx = xs[point] - x;
            float dz = zs[point] - z;
            if (dx * dx + dz * dz < gap * gap) {
                return distance[point] + radius + widths[point];
            }
        }
        return 0f;
    }

    private Reach reach(long id, long shape, float[] xs, float[] zs) {
        float[] distance = ReachShaper.distances(xs, zs);
        float[] halfWidth = widths(shape, distance);
        float[] depth = depths(shape, distance);
        int[] grade = new int[xs.length];
        Arrays.fill(grade, settings.seaLevel());
        ReachStyle[] style = new ReachStyle[xs.length];
        Arrays.fill(style, ReachStyle.TIDAL);
        return new Reach(id, settings.tidalTier(), 0L, xs, zs, grade, halfWidth, depth, style, 0f);
    }

    private float[] widths(long shape, float[] distance) {
        float total = distance[distance.length - 1];
        HydrologySettings.FloatRange mouths = config.mouthHalfWidth();
        HydrologySettings.FloatRange heads = config.headHalfWidth();
        float mouth = mouths.min() + (mouths.max() - mouths.min()) * (float) CellHash.unit(CellHash.mix(shape, SALT_MOUTH, 0, 0));
        float head = heads.min() + (heads.max() - heads.min()) * (float) CellHash.unit(CellHash.mix(shape, SALT_HEAD, 0, 0));
        double phaseLong = CellHash.unit(CellHash.mix(shape, SALT_WOBBLE, 0, 0));
        double phaseShort = CellHash.unit(CellHash.mix(shape, SALT_WOBBLE, 1, 0));
        float[] widths = new float[distance.length];
        for (int point = 0; point < distance.length; point++) {
            float s = distance[point];
            float t = s / total;
            double funnel = head + (mouth - head) * Math.pow(t, config.taper());
            double wobble = 0.65D * StrictMath.sin(2D * Math.PI * (s / WOBBLE_LONG + phaseLong)) + 0.35D * StrictMath.sin(2D * Math.PI * (s / WOBBLE_SHORT + phaseShort));
            double ends = Math.min(1f, Math.min(s, total - s) / TIP_LENGTH);
            double shaped = funnel * (1D + config.widthVariation() * ends * wobble);
            double tip = TIP_HALF_WIDTH + (shaped - TIP_HALF_WIDTH) * Math.min(1f, s / TIP_LENGTH);
            widths[point] = (float) Math.max(TIP_HALF_WIDTH, point == distance.length - 1 ? mouth : tip);
        }
        return widths;
    }

    private float[] depths(long shape, float[] distance) {
        float total = distance[distance.length - 1];
        HydrologySettings.IntRange range = config.depth();
        float deepest = range.min() + (range.max() - range.min()) * (float) CellHash.unit(CellHash.mix(shape, SALT_DEPTH, 0, 0));
        float[] depth = new float[distance.length];
        for (int point = 0; point < distance.length; point++) {
            float s = distance[point];
            float along = range.min() + (deepest - range.min()) * Math.min(1f, s / total / DEEP_SHARE);
            float shoal = Math.min(1f, s / SHOAL_LENGTH) * Math.min(1f, (total - s) / SHOAL_LENGTH);
            depth[point] = 1f + (along - 1f) * shoal;
        }
        return depth;
    }

    private int groundedHead(float[] xs, float[] zs, long id, long shape) {
        float[] distance = ReachShaper.distances(xs, zs);
        float total = distance[distance.length - 1];
        float[] widths = widths(shape, distance);
        int band = config.bankBand();
        float limit = settings.seaLevel() + config.maximumBankHeight();
        int sea = settings.seaLevel();
        float[] offsets = new float[8];
        int stops = (int) Math.ceil(total / SCAN_STEP);
        int point = xs.length - 1;
        for (int stop = 0; stop <= stops; stop++) {
            float wanted = total - stop * SCAN_STEP;
            while (point > 0 && distance[point] > wanted) {
                point--;
            }
            float half = widths[point];
            offsets[0] = half + BESIDE_NEAR;
            offsets[1] = -offsets[0];
            offsets[2] = half + BESIDE_FAR;
            offsets[3] = -offsets[2];
            offsets[4] = half + 0.5f * band;
            offsets[5] = -offsets[4];
            offsets[6] = half + band;
            offsets[7] = -offsets[6];
            int before = Math.max(0, point - 1);
            int after = Math.min(xs.length - 1, point + 1);
            double length = Math.max(1e-3D, Math.hypot(xs[after] - xs[before], zs[after] - zs[before]));
            double tx = (xs[after] - xs[before]) / length;
            double tz = (zs[after] - zs[before]) / length;
            int[] columnX = new int[offsets.length + 1];
            int[] columnZ = new int[offsets.length + 1];
            for (int index = 0; index < offsets.length; index++) {
                columnX[index] = (int) Math.floor(xs[point] - offsets[index] * tz);
                columnZ[index] = (int) Math.floor(zs[point] + offsets[index] * tx);
            }
            columnX[offsets.length] = (int) Math.floor(xs[point]);
            columnZ[offsets.length] = (int) Math.floor(zs[point]);
            HydrologySampleBuffer samples = new HydrologySampleBuffer(columnX.length);
            fine.sampleColumns(columnX, columnZ, samples);
            boolean bad = false;
            for (int index = 0; index < columnX.length && !bad; index++) {
                boolean ocean = samples.ocean()[index];
                boolean near = index < 4 || index == offsets.length;
                boolean far = index >= 4 && index < offsets.length;
                float allowed = far ? limit + 0.5f * band : limit;
                bad = !ocean && (samples.height()[index] > allowed || near && total - distance[point] > MOUTH_ZONE && samples.height()[index] < sea);
            }
            if (bad) {
                float keep = distance[point] + band + widths[point];
                int head = point;
                while (head < xs.length - 1 && distance[head] < keep) {
                    head++;
                }
                return total - distance[head] >= config.length().min() ? head : -1;
            }
        }
        return 0;
    }

    private boolean besideSea(Reach reach) {
        float[] distance = ReachShaper.distances(reach.xs(), reach.zs());
        float total = distance[distance.length - 1];
        int stops = 0;
        int beside = 0;
        int point = 0;
        int directions = 8;
        int[] columnX = new int[directions];
        int[] columnZ = new int[directions];
        HydrologySampleBuffer samples = new HydrologySampleBuffer(directions);
        for (float s = 0f; s <= total; s += SCAN_STEP) {
            while (point < distance.length - 1 && distance[point] < s) {
                point++;
            }
            float radius = reach.halfWidth()[point] + BESIDE_SEA_MARGIN;
            for (int direction = 0; direction < directions; direction++) {
                double angle = 2D * Math.PI * direction / directions;
                columnX[direction] = (int) Math.floor(reach.xs()[point] + radius * Math.cos(angle));
                columnZ[direction] = (int) Math.floor(reach.zs()[point] + radius * Math.sin(angle));
            }
            fine.sampleColumns(columnX, columnZ, samples);
            boolean sea = false;
            for (int direction = 0; direction < directions; direction++) {
                sea |= samples.ocean()[direction];
            }
            stops++;
            beside += sea ? 1 : 0;
        }
        return beside > BESIDE_SEA_SHARE * stops;
    }

    private void requireWithinReach(WindowKey window, Reach reach) {
        int band = config.bankBand();
        for (int point = 0; point < reach.xs().length; point++) {
            float out = reach.halfWidth()[point] + band;
            if (reach.xs()[point] - out < (window.wx() - 1) * WindowKey.SIZE || reach.xs()[point] + out >= (window.wx() + 2) * WindowKey.SIZE
                    || reach.zs()[point] - out < (window.wz() - 1) * WindowKey.SIZE || reach.zs()[point] + out >= (window.wz() + 2) * WindowKey.SIZE) {
                throw new IllegalStateException("Sea inlet " + reach.id() + " of window " + window + " reaches past the next window at point " + point);
            }
        }
    }

    private record Land(DemGrid grid, HydrologySampleBuffer samples) {
    }

    private record Anchor(int siteX, int siteZ, WindowKey owner, double targetX, double targetZ, boolean active, int x, int z, long rank) {
    }
}
