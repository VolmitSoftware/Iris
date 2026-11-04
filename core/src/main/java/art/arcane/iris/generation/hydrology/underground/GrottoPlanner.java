package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlanner;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;
import art.arcane.iris.generation.hydrology.water.TidalPlanner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GrottoPlanner {
    private static final int CELL = WindowPlanner.CELL;
    private static final int[] SIDE_I = {1, -1, 0, 0};
    private static final int[] SIDE_J = {0, 0, 1, -1};
    private static final int MEMO_SITES = 4096;
    private static final int BUILD_ATTEMPTS = 12;
    private static final int RAYS = 8;
    private static final int ROCK_MARGIN = 6;
    private static final float STEP = 4f;
    private static final float OFFSHORE = 18f;
    private static final float OPEN_RUN = 56f;
    private static final float WALK_CLEAR = 9f;
    private static final float ROOF_KEEP = 4f;
    private static final float MIN_ROCK = 18f;
    private static final float MIN_SIDE_GROUND = 3f;
    private static final float MIN_COVERED = 32f;
    private static final float COVERED_SHARE = 0.45f;
    private static final float MIN_HALF_WIDTH = 2.8f;
    private static final float MIN_ROOF = 3f;
    private static final float CAVERN_WIDTH_MIN = 4f;
    private static final float CAVERN_WIDTH_SPAN = 6f;
    private static final float CAVERN_ROOF_MIN = 5f;
    private static final float CAVERN_ROOF_SPAN = 8f;
    private static final float CAVERN_FLOOR = 3f;
    private static final float CAVERN_REACH_MIN = 11f;
    private static final float CAVERN_REACH_SPAN = 7f;
    private static final float CAVERN_EVERY = 90f;
    private static final float FUNNEL = 26f;
    private static final float FLOOR_MIN = 3f;
    private static final float FLOOR_SPAN = 3f;
    private static final float BEACH = 14f;
    private static final float BEACH_ODDS = 0.75f;
    private static final float SHAPE_REACH = 1.35f;
    private static final float ROOF_REACH = 1.2f;
    private static final float PROFILE_WAVE = 34f;
    private static final float MEANDER_MIN = 48f;
    private static final float MEANDER_SPAN = 36f;
    private static final float MEANDER_RAMP = 22f;
    private static final float SELF_APART = 16f;
    private static final int SELF_SKIP = 10;
    private static final int SMOOTH_PASSES = 3;
    private static final float CLEAR_BAND = 32f;
    private static final float CLEAR_MARGIN = 6f;
    private static final float TERRITORY_MARGIN = 10f;
    private static final float HOLE_ODDS = 0.6f;
    private static final float HOLE_RADIUS_MIN = 1.6f;
    private static final float HOLE_RADIUS_SPAN = 1.6f;
    private static final float HOLE_COVER_MIN = 2f;
    private static final float HOLE_COVER_MAX = 16f;
    private static final float HOLE_FROM = 0.25f;
    private static final float HOLE_TO = 0.9f;
    private static final int HOLE_ABOVE = 4;
    private static final double MEANDER_ANGLE = Math.toRadians(74D);
    private static final double SECOND_ANGLE = Math.toRadians(24D);
    private static final double SECOND_WAVE = 0.43D;
    private static final double MAX_TURN = Math.toRadians(17D);
    private static final double OPTION_TURN = Math.toRadians(26D);
    private static final int OPTION_PAIRS = 4;
    private static final double RISE_WEIGHT = 0.6D;
    private static final float CROWD_SHARE = 2f;
    private static final float WALK_SHRINK = 1.15f;
    private static final float ARM_RING = 12f;
    private static final float TALL_WIDTHS = 1.5f;
    private static final float TALL_EXTRA = 1.5f;
    private static final float CUT_OFF_SHARE = 0.03f;
    private static final long SALT_RANK = 0x4752545241L;
    private static final long SALT_ID = 0x4752544944L;
    private static final long SALT_SITE = 0x4752545354L;
    private static final long SALT_DENSITY = 0x4752544454L;
    private static final long SALT_TARGET_X = 0x4752545458L;
    private static final long SALT_TARGET_Z = 0x475254545AL;
    private static final long SALT_LENGTH = 0x4752544C4EL;
    private static final long SALT_MEANDER = 0x4752544D52L;
    private static final long SALT_PROFILE = 0x4752545046L;
    private static final long SALT_CAVERN = 0x4752544356L;
    private static final long SALT_BEACH = 0x4752544245L;
    private static final long SALT_HOLE = 0x4752544F4CL;

    private final HydrologySettings settings;
    private final HydrologySettings.Grottos config;
    private final HeightLattice fine;
    private final TidalPlanner tidal;
    private final int stride;
    private final long seed;
    private final int sea;
    private final float territory;
    private final Map<Long, Anchor> anchors = new LinkedHashMap<>(MEMO_SITES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Anchor> eldest) {
            return size() > MEMO_SITES;
        }
    };

    public GrottoPlanner(HydrologySettings settings, HeightLattice fine, TidalPlanner tidal) {
        this.tidal = Objects.requireNonNull(tidal, "tidal");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.config = settings.underground().grottos();
        this.fine = Objects.requireNonNull(fine, "fine");
        this.stride = CELL / fine.cell();
        this.seed = settings.seed();
        this.sea = settings.seaLevel();
        this.territory = 2f * ((config.radius().max() + CAVERN_WIDTH_MIN + CAVERN_WIDTH_SPAN) * SHAPE_REACH) + TERRITORY_MARGIN;
    }

    public List<Grotto> plan(WindowKey window, DemGrid natural, HydrologySampleBuffer samples, ShapedNetwork network, List<Reach> arms) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(network, "network");
        if (!config.enabled() || settings.density() <= 0f) {
            return List.of();
        }
        List<Anchor> own = own(window);
        if (own.isEmpty()) {
            return List.of();
        }
        View view = new View(natural, samples, TidalPlanner.coastDistance(natural, samples.ocean()), blocked(natural, network, arms));
        List<Grotto> planned = new ArrayList<>();
        for (Anchor anchor : own) {
            Grotto grotto = site(window, view, anchor, rivals(anchor));
            if (grotto != null) {
                requireWithinWindow(window, grotto);
                planned.add(grotto);
            }
        }
        planned.sort(Comparator.comparingLong(Grotto::id));
        return List.copyOf(planned);
    }

    private List<Anchor> own(WindowKey window) {
        int spacing = config.spacing();
        int minSiteX = Math.floorDiv(window.wx() * WindowKey.SIZE, spacing) - 1;
        int maxSiteX = Math.floorDiv((window.wx() + 1) * WindowKey.SIZE - 1, spacing) + 1;
        int minSiteZ = Math.floorDiv(window.wz() * WindowKey.SIZE, spacing) - 1;
        int maxSiteZ = Math.floorDiv((window.wz() + 1) * WindowKey.SIZE - 1, spacing) + 1;
        List<Anchor> own = new ArrayList<>();
        for (int siteZ = minSiteZ; siteZ <= maxSiteZ; siteZ++) {
            for (int siteX = minSiteX; siteX <= maxSiteX; siteX++) {
                Anchor anchor = anchor(siteX, siteZ);
                if (anchor.active() && anchor.owner().equals(window) && !crowded(anchor)) {
                    own.add(anchor);
                }
            }
        }
        return own;
    }

    private boolean crowded(Anchor anchor) {
        int spacing = config.spacing();
        double apart = CROWD_SHARE * territory;
        int minSiteX = Math.floorDiv((int) Math.floor(anchor.x() - apart), spacing) - 1;
        int maxSiteX = Math.floorDiv((int) Math.ceil(anchor.x() + apart), spacing) + 1;
        int minSiteZ = Math.floorDiv((int) Math.floor(anchor.z() - apart), spacing) - 1;
        int maxSiteZ = Math.floorDiv((int) Math.ceil(anchor.z() + apart), spacing) + 1;
        long rank = rank(anchor);
        for (int siteZ = minSiteZ; siteZ <= maxSiteZ; siteZ++) {
            for (int siteX = minSiteX; siteX <= maxSiteX; siteX++) {
                Anchor other = anchor(siteX, siteZ);
                if (other.active() && (other.siteX() != anchor.siteX() || other.siteZ() != anchor.siteZ())
                        && Math.hypot(other.x() - anchor.x(), other.z() - anchor.z()) < apart && rank(other) > rank) {
                    return true;
                }
            }
        }
        return false;
    }

    private long rank(Anchor anchor) {
        return CellHash.mix(seed, SALT_RANK, anchor.siteX(), anchor.siteZ());
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
        if (settings.density() < 1f && CellHash.unit(CellHash.mix(seed, SALT_DENSITY, siteX, siteZ)) >= settings.density()) {
            return new Anchor(siteX, siteZ, WindowKey.containing((int) Math.floor(targetX), (int) Math.floor(targetZ)), targetX, targetZ, false, 0, 0);
        }
        int firstX = Math.ceilDiv(siteX * spacing, CELL) * CELL;
        int firstZ = Math.ceilDiv(siteZ * spacing, CELL) * CELL;
        int cellsX = Math.ceilDiv((siteX + 1) * spacing - firstX, CELL);
        int cellsZ = Math.ceilDiv((siteZ + 1) * spacing - firstZ, CELL);
        int margin = ROCK_MARGIN;
        View land = land(firstX - margin * CELL, firstZ - margin * CELL, cellsX + 2 * margin, cellsZ + 2 * margin);
        DemGrid grid = land.grid();
        int bestX = 0;
        int bestZ = 0;
        float bestRock = Float.NEGATIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int j = margin; j < margin + cellsZ; j++) {
            for (int i = margin; i < margin + cellsX; i++) {
                int cell = grid.index(i, j);
                if (!coastal(land, cell)) {
                    continue;
                }
                float rock = rock(land, cell);
                if (rock < MIN_ROCK) {
                    continue;
                }
                double distance = Math.hypot(grid.blockX(cell) - targetX, grid.blockZ(cell) - targetZ);
                float bucket = bucket(rock);
                if (bucket > bestRock || bucket == bestRock && distance < bestDistance) {
                    bestRock = bucket;
                    bestDistance = distance;
                    bestX = grid.blockX(cell);
                    bestZ = grid.blockZ(cell);
                }
            }
        }
        boolean active = bestRock != Float.NEGATIVE_INFINITY;
        WindowKey owner = active ? WindowKey.containing(bestX, bestZ) : WindowKey.containing((int) Math.floor(targetX), (int) Math.floor(targetZ));
        return new Anchor(siteX, siteZ, owner, targetX, targetZ, active, bestX, bestZ);
    }

    private static float bucket(float rock) {
        return (float) Math.floor(rock / 12f);
    }

    private View land(int originX, int originZ, int width, int depth) {
        HydrologySampleBuffer samples = new HydrologySampleBuffer(width * depth);
        fine.fill(Math.floorDiv(originX, fine.cell()), Math.floorDiv(originZ, fine.cell()), width, depth, stride, samples);
        DemGrid grid = new DemGrid(originX, originZ, CELL, width, depth);
        System.arraycopy(samples.height(), 0, grid.values(), 0, width * depth);
        return new View(grid, samples, null, null);
    }

    private boolean coastal(View view, int cell) {
        HydrologySampleBuffer samples = view.samples();
        return !samples.ocean()[cell] && samples.height()[cell] >= sea && (samples.permission()[cell] & HydrologySampleBuffer.ALLOW_STREAMS) != 0 && oceanSide(view, cell) >= 0;
    }

    private static int oceanSide(View view, int cell) {
        DemGrid grid = view.grid();
        int i = cell % grid.width();
        int j = cell / grid.width();
        for (int side = 0; side < SIDE_I.length; side++) {
            int ni = i + SIDE_I[side];
            int nj = j + SIDE_J[side];
            if (ni >= 0 && nj >= 0 && ni < grid.width() && nj < grid.depth() && view.samples().ocean()[grid.index(ni, nj)]) {
                return side;
            }
        }
        return -1;
    }

    private float rock(View view, int cell) {
        DemGrid grid = view.grid();
        int i = cell % grid.width();
        int j = cell / grid.width();
        float highest = Float.NEGATIVE_INFINITY;
        for (int dj = -ROCK_MARGIN; dj <= ROCK_MARGIN; dj++) {
            for (int di = -ROCK_MARGIN; di <= ROCK_MARGIN; di++) {
                int ni = i + di;
                int nj = j + dj;
                if (ni >= 0 && nj >= 0 && ni < grid.width() && nj < grid.depth() && di * di + dj * dj >= 4 && !view.samples().ocean()[grid.index(ni, nj)]) {
                    highest = Math.max(highest, view.samples().height()[grid.index(ni, nj)]);
                }
            }
        }
        return highest - sea;
    }

    private List<Anchor> rivals(Anchor anchor) {
        double reach = 2D * config.length().max() + 2D * territory;
        int spacing = config.spacing();
        int minSiteX = Math.floorDiv((int) Math.floor(anchor.x() - reach), spacing);
        int maxSiteX = Math.floorDiv((int) Math.ceil(anchor.x() + reach), spacing);
        int minSiteZ = Math.floorDiv((int) Math.floor(anchor.z() - reach), spacing);
        int maxSiteZ = Math.floorDiv((int) Math.ceil(anchor.z() + reach), spacing);
        List<Anchor> rivals = new ArrayList<>();
        for (int siteZ = minSiteZ; siteZ <= maxSiteZ; siteZ++) {
            for (int siteX = minSiteX; siteX <= maxSiteX; siteX++) {
                Anchor other = anchor(siteX, siteZ);
                if (other.active() && (other.siteX() != anchor.siteX() || other.siteZ() != anchor.siteZ()) && !crowded(other)) {
                    rivals.add(other);
                }
            }
        }
        return rivals;
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

    private boolean[] blocked(DemGrid grid, ShapedNetwork network, List<Reach> arms) {
        boolean[] blocked = new boolean[grid.values().length];
        for (Reach river : network.reaches()) {
            markReach(grid, blocked, river);
        }
        for (Reach arm : arms) {
            markReach(grid, blocked, arm);
        }
        for (Pond pond : network.ponds()) {
            PondMask mask = pond.mask();
            float radius = 1.5f * mask.step() + Math.min(settings.ponds().shore().taper(), CLEAR_BAND) + CLEAR_MARGIN;
            for (int j = 0; j < mask.depth(); j++) {
                for (int i = 0; i < mask.width(); i++) {
                    if (mask.node(i, j)) {
                        mark(grid, blocked, mask.originX() + i * mask.step(), mask.originZ() + j * mask.step(), radius);
                    }
                }
            }
        }
        return blocked;
    }

    private static void markReach(DemGrid grid, boolean[] blocked, Reach reach) {
        float band = Math.min(reach.tier().bankBand(), CLEAR_BAND) + CLEAR_MARGIN;
        for (int point = 0; point < reach.xs().length; point += 2) {
            mark(grid, blocked, reach.xs()[point], reach.zs()[point], reach.halfWidth()[point] + band);
        }
    }

    private static void mark(DemGrid grid, boolean[] blocked, float x, float z, float radius) {
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
                    blocked[j * grid.width() + i] = true;
                }
            }
        }
    }

    private Grotto site(WindowKey window, View view, Anchor anchor, List<Anchor> rivals) {
        DemGrid grid = view.grid();
        float around = config.spacing();
        int firstI = Math.floorDiv((int) Math.floor(anchor.x() - around) - grid.originX(), CELL);
        int firstJ = Math.floorDiv((int) Math.floor(anchor.z() - around) - grid.originZ(), CELL);
        int lastI = Math.floorDiv((int) Math.ceil(anchor.x() + around) - grid.originX(), CELL);
        int lastJ = Math.floorDiv((int) Math.ceil(anchor.z() + around) - grid.originZ(), CELL);
        List<Candidate> candidates = new ArrayList<>();
        int margin = ROCK_MARGIN;
        for (int j = Math.max(margin, firstJ); j <= Math.min(grid.depth() - 1 - margin, lastJ); j++) {
            for (int i = Math.max(margin, firstI); i <= Math.min(grid.width() - 1 - margin, lastI); i++) {
                int cell = grid.index(i, j);
                if (!coastal(view, cell) || view.blocked()[cell] || !window.equals(WindowKey.containing(grid.blockX(cell), grid.blockZ(cell)))
                        || !inTerritory(anchor, rivals, grid.blockX(cell), grid.blockZ(cell))) {
                    continue;
                }
                float rock = rock(view, cell);
                if (rock >= MIN_ROCK) {
                    candidates.add(new Candidate(cell, bucket(rock), Math.hypot(grid.blockX(cell) - anchor.x(), grid.blockZ(cell) - anchor.z())));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble((Candidate candidate) -> -candidate.rock()).thenComparingDouble(Candidate::distance).thenComparingInt(Candidate::cell));
        int attempts = 0;
        for (Candidate candidate : candidates) {
            Walk walk = walk(window, view, anchor, rivals, candidate.cell());
            if (walk == null) {
                continue;
            }
            Grotto grotto = build(view, anchor, rivals, walk);
            if (grotto != null) {
                return grotto;
            }
            if (++attempts >= BUILD_ATTEMPTS) {
                return null;
            }
        }
        return null;
    }

    private Walk walk(WindowKey window, View view, Anchor anchor, List<Anchor> rivals, int start) {
        DemGrid grid = view.grid();
        int side = oceanSide(view, start);
        float startX = grid.blockX(start);
        float startZ = grid.blockZ(start);
        double seaward = Math.atan2(SIDE_J[side], SIDE_I[side]);
        float x = startX + SIDE_I[side] * CELL;
        float z = startZ + SIDE_J[side] * CELL;
        if (!isOcean(x, z)) {
            return null;
        }
        for (float out = 0f; out < OFFSHORE; out += STEP) {
            float nx = x + (float) Math.cos(seaward) * STEP;
            float nz = z + (float) Math.sin(seaward) * STEP;
            if (!isOcean(nx, nz)) {
                break;
            }
            x = nx;
            z = nz;
        }
        int hashX = (int) startX;
        int hashZ = (int) startZ;
        float target = WALK_SHRINK * (config.length().min() + (config.length().max() - config.length().min()) * (float) CellHash.unit(CellHash.mix(seed, SALT_LENGTH, hashX, hashZ)));
        double wave = MEANDER_MIN + MEANDER_SPAN * CellHash.unit(CellHash.mix(seed, SALT_MEANDER, hashX, hashZ));
        double phase = 2D * Math.PI * CellHash.unit(CellHash.mix(seed, SALT_MEANDER + 1L, hashX, hashZ));
        double phaseSecond = 2D * Math.PI * CellHash.unit(CellHash.mix(seed, SALT_MEANDER + 2L, hashX, hashZ));
        List<float[]> stations = new ArrayList<>();
        stations.add(new float[]{x, z});
        double heading = seaward + Math.PI;
        float walked = 0f;
        float landed = -1f;
        float covered = -1f;
        int coast = -1;
        int coveredFrom = -1;
        while (walked < target + OFFSHORE) {
            double base = landed < 0f ? seaward + Math.PI : baseHeading(view, x, z, heading);
            float past = landed < 0f ? 0f : walked - landed;
            double ramp = Math.min(1D, past / MEANDER_RAMP);
            double meander = ramp * (MEANDER_ANGLE * StrictMath.sin(2D * Math.PI * past / wave + phase)
                    + SECOND_ANGLE * StrictMath.sin(2D * Math.PI * past / (wave * SECOND_WAVE) + phaseSecond));
            double wanted = base + meander;
            double turn = Math.clamp(normal(wanted - heading), -MAX_TURN, MAX_TURN);
            double sign = meander >= 0D ? 1D : -1D;
            boolean moved = false;
            for (int option = 0; option <= 2 * OPTION_PAIRS && !moved; option++) {
                double offset = option == 0 ? 0D : (option % 2 == 1 ? sign : -sign) * OPTION_TURN * ((option + 1) / 2);
                double next = heading + turn + offset;
                float nx = x + (float) Math.cos(next) * STEP;
                float nz = z + (float) Math.sin(next) * STEP;
                Check check = check(window, view, anchor, rivals, stations, nx, nz, landed >= 0f, covered >= 0f, walked + STEP - landed);
                if (check == Check.REFUSED) {
                    continue;
                }
                heading = next;
                x = nx;
                z = nz;
                walked += STEP;
                stations.add(new float[]{x, z});
                if (landed < 0f && check != Check.SEA) {
                    landed = walked;
                    coast = stations.size() - 1;
                }
                if (covered < 0f && check == Check.COVERED) {
                    covered = walked;
                    coveredFrom = stations.size() - 1;
                }
                moved = true;
            }
            if (!moved) {
                break;
            }
        }
        float length = walked - landed;
        float coveredLength = walked - covered;
        if (coast < 0 || covered < 0f) {
            return null;
        }
        if (length < config.length().min() || coveredLength < Math.max(MIN_COVERED, COVERED_SHARE * length)) {
            return null;
        }
        float[] xs = new float[stations.size()];
        float[] zs = new float[stations.size()];
        for (int index = 0; index < xs.length; index++) {
            xs[index] = stations.get(index)[0];
            zs[index] = stations.get(index)[1];
        }
        return new Walk(xs, zs, coast, coveredFrom, CellHash.mix(CellHash.mix(seed, SALT_ID, hashX, hashZ), SALT_SITE, (int) xs[0], (int) zs[0]));
    }

    private static double normal(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    private double baseHeading(View view, float x, float z, double fallback) {
        DemGrid grid = view.grid();
        int i = Math.floorDiv((int) Math.floor(x) - grid.originX(), CELL);
        int j = Math.floorDiv((int) Math.floor(z) - grid.originZ(), CELL);
        if (i < 2 || j < 2 || i >= grid.width() - 2 || j >= grid.depth() - 2) {
            return fallback;
        }
        float[] coast = view.coast();
        float[] height = view.samples().height();
        double coastX = coast[grid.index(i + 2, j)] - coast[grid.index(i - 2, j)];
        double coastZ = coast[grid.index(i, j + 2)] - coast[grid.index(i, j - 2)];
        double coastLength = Math.hypot(coastX, coastZ);
        double riseX = height[grid.index(i + 2, j)] - height[grid.index(i - 2, j)];
        double riseZ = height[grid.index(i, j + 2)] - height[grid.index(i, j - 2)];
        double riseLength = Math.hypot(riseX, riseZ);
        double dirX = coastLength > 0D ? coastX / coastLength : 0D;
        double dirZ = coastLength > 0D ? coastZ / coastLength : 0D;
        if (riseLength > 0D) {
            dirX += RISE_WEIGHT * riseX / riseLength;
            dirZ += RISE_WEIGHT * riseZ / riseLength;
        }
        return dirX == 0D && dirZ == 0D ? fallback : Math.atan2(dirZ, dirX);
    }

    private Check check(WindowKey window, View view, Anchor anchor, List<Anchor> rivals, List<float[]> stations, float x, float z, boolean landed, boolean covered, float sinceLand) {
        DemGrid grid = view.grid();
        int i = Math.floorDiv((int) Math.floor(x) - grid.originX(), CELL);
        int j = Math.floorDiv((int) Math.floor(z) - grid.originZ(), CELL);
        if (i < 2 || j < 2 || i >= grid.width() - 2 || j >= grid.depth() - 2 || view.blocked()[grid.index(i, j)]) {
            return Check.REFUSED;
        }
        if (!inTerritory(anchor, rivals, x, z) || foreignArmNear(window, x, z)) {
            return Check.REFUSED;
        }
        for (int index = 0; index < stations.size() - SELF_SKIP; index++) {
            float[] station = stations.get(index);
            float dx = station[0] - x;
            float dz = station[1] - z;
            if (dx * dx + dz * dz < SELF_APART * SELF_APART) {
                return Check.REFUSED;
            }
        }
        int[] columnX = new int[RAYS + 1];
        int[] columnZ = new int[RAYS + 1];
        for (int ray = 0; ray < RAYS; ray++) {
            double angle = 2D * Math.PI * ray / RAYS;
            columnX[ray] = (int) Math.floor(x + WALK_CLEAR * Math.cos(angle));
            columnZ[ray] = (int) Math.floor(z + WALK_CLEAR * Math.sin(angle));
        }
        columnX[RAYS] = (int) Math.floor(x);
        columnZ[RAYS] = (int) Math.floor(z);
        HydrologySampleBuffer samples = new HydrologySampleBuffer(RAYS + 1);
        fine.sampleColumns(columnX, columnZ, samples);
        boolean ocean = samples.ocean()[RAYS];
        if (!landed && ocean) {
            return Check.SEA;
        }
        if (landed && ocean || (samples.permission()[RAYS] & HydrologySampleBuffer.ALLOW_STREAMS) == 0 && !ocean) {
            return Check.REFUSED;
        }
        float lowest = samples.height()[RAYS];
        boolean oceanNear = false;
        for (int ray = 0; ray < RAYS; ray++) {
            oceanNear |= samples.ocean()[ray];
            lowest = Math.min(lowest, samples.height()[ray]);
        }
        float needed = sea + config.headroom().min() + ROOF_KEEP;
        boolean roofed = !oceanNear && lowest >= needed;
        if (covered) {
            return roofed ? Check.COVERED : Check.REFUSED;
        }
        if (roofed) {
            return Check.COVERED;
        }
        return sinceLand > OPEN_RUN ? Check.REFUSED : Check.OPEN;
    }

    private boolean foreignArmNear(WindowKey window, float x, float z) {
        float clearance = CLEAR_BAND + CLEAR_MARGIN + config.radius().max() + CAVERN_WIDTH_MIN;
        if (!tidal.foreignArmMayReach(window, x, z, clearance)) {
            return false;
        }
        int rings = (int) Math.ceil(clearance / ARM_RING);
        int[] columnX = new int[RAYS * rings + 1];
        int[] columnZ = new int[RAYS * rings + 1];
        for (int ray = 0; ray < RAYS; ray++) {
            double angle = 2D * Math.PI * ray / RAYS;
            for (int ring = 0; ring < rings; ring++) {
                float out = (ring + 1) * ARM_RING;
                columnX[ray * rings + ring] = (int) Math.floor(x + out * Math.cos(angle));
                columnZ[ray * rings + ring] = (int) Math.floor(z + out * Math.sin(angle));
            }
        }
        columnX[RAYS * rings] = (int) Math.floor(x);
        columnZ[RAYS * rings] = (int) Math.floor(z);
        HydrologySampleBuffer samples = new HydrologySampleBuffer(columnX.length);
        fine.sampleColumns(columnX, columnZ, samples);
        float limit = tidal.armGroundLimit();
        for (int index = 0; index < columnX.length; index++) {
            if (!samples.ocean()[index] && samples.height()[index] <= limit) {
                return true;
            }
        }
        return false;
    }

    private boolean isOcean(float x, float z) {
        int[] columnX = {(int) Math.floor(x)};
        int[] columnZ = {(int) Math.floor(z)};
        HydrologySampleBuffer samples = new HydrologySampleBuffer(1);
        fine.sampleColumns(columnX, columnZ, samples);
        return samples.ocean()[0] && samples.height()[0] < sea;
    }

    private Grotto build(View view, Anchor anchor, List<Anchor> rivals, Walk walk) {
        float[] xs = walk.xs().clone();
        float[] zs = walk.zs().clone();
        for (int pass = 0; pass < SMOOTH_PASSES; pass++) {
            xs = smoothed(xs);
            zs = smoothed(zs);
        }
        long id = walk.id();
        float roughness = config.roughness();
        float[] along = new float[xs.length];
        for (int point = 1; point < xs.length; point++) {
            along[point] = along[point - 1] + (float) Math.hypot(xs[point] - xs[point - 1], zs[point] - zs[point - 1]);
        }
        float total = along[xs.length - 1];
        float coastAt = along[walk.coast()];
        if (total - coastAt < config.length().min()) {
            return null;
        }
        float[][] caverns = caverns(id, coastAt, total);
        int rings = (int) Math.ceil(ringReach() / STEP) + 1;
        float[] room = new float[xs.length];
        float[][] lows = new float[xs.length][rings];
        for (int point = 0; point < xs.length; point++) {
            boolean walled = point >= walk.coveredFrom() && along[point] - coastAt > FUNNEL;
            room[point] = room(view, anchor, rivals, xs[point], zs[point], walled, lows[point]);
        }
        float[] halfWidth = new float[xs.length];
        float[] roof = new float[xs.length];
        float[] floor = new float[xs.length];
        float[] roofCap = new float[xs.length];
        float mouth = config.mouthWidth().min() + (config.mouthWidth().max() - config.mouthWidth().min()) * (float) CellHash.unit(CellHash.mix(id, SALT_PROFILE, 0, 0));
        float reach = (float) (1D + (GrottoShape.radialMax(roughness) - 1D) * 0.5D);
        float warp = (float) GrottoShape.warp(roughness);
        for (int point = 0; point < xs.length; point++) {
            float s = along[point];
            float width = config.radius().min() + (config.radius().max() - config.radius().min()) * slow(id, SALT_PROFILE + 1L, s);
            float high = config.headroom().min() + (config.headroom().max() - config.headroom().min()) * slow(id, SALT_PROFILE + 2L, s);
            float deep = FLOOR_MIN + FLOOR_SPAN * slow(id, SALT_PROFILE + 3L, s);
            for (float[] cavern : caverns) {
                float bump = (s - cavern[0]) / cavern[1];
                float weight = (float) StrictMath.exp(-bump * bump);
                width += cavern[2] * weight;
                high += cavern[3] * weight;
                deep += CAVERN_FLOOR * weight;
            }
            float funnel = s <= coastAt ? 1f : Math.max(0f, 1f - (s - coastAt) / FUNNEL);
            width = Math.max(width, width + (mouth - width) * funnel);
            high = Math.max(high, config.headroom().max() * (0.6f + 0.4f * funnel));
            float capped = (room[point] - warp - 1f) / reach;
            halfWidth[point] = Math.max(MIN_HALF_WIDTH, Math.min(width, capped));
            int ring = Math.min(rings - 1, (int) Math.ceil(halfWidth[point] / STEP));
            float ceiling = point >= walk.coveredFrom() ? Math.max(MIN_ROOF, (lows[point][ring] - sea - ROOF_KEEP) / ROOF_REACH) : Float.POSITIVE_INFINITY;
            roofCap[point] = ceiling;
            roof[point] = Math.min(Math.min(high, ceiling), TALL_WIDTHS * halfWidth[point] + TALL_EXTRA);
            floor[point] = deep;
        }
        halfWidth = eased(halfWidth);
        roof = eased(roof);
        floor = eased(floor);
        float[] lift = new float[xs.length];
        boolean beach = CellHash.unit(CellHash.mix(id, SALT_BEACH, 0, 0)) < BEACH_ODDS;
        for (int point = 0; point < xs.length; point++) {
            roof[point] = Math.min(roof[point], roofCap[point]);
            float out = (total - along[point]) / BEACH;
            if (beach && out < 1f) {
                float rise = 1f - out;
                lift[point] = rise * rise * (3f - 2f * rise) * (floor[point] * 1.25f);
                roof[point] = Math.max(MIN_ROOF, roof[point] - lift[point] * 0.5f);
            }
        }
        Holes holes = holes(id, xs, zs, along, walk, roof, lift, total);
        Grotto grotto = new Grotto(id, sea, roughness, walk.coast(), xs, zs, halfWidth, roof, floor, lift, holes.x(), holes.z(), holes.radius(), holes.top(), new long[0]);
        long[] plugs = cutOff(grotto);
        if (plugs == null) {
            return null;
        }
        return new Grotto(id, sea, roughness, walk.coast(), xs, zs, halfWidth, roof, floor, lift, holes.x(), holes.z(), holes.radius(), holes.top(), plugs);
    }

    private float ringReach() {
        return (config.radius().max() + CAVERN_WIDTH_MIN + CAVERN_WIDTH_SPAN) * SHAPE_REACH + 6f;
    }

    private float room(View view, Anchor anchor, List<Anchor> rivals, float x, float z, boolean roofed, float[] lows) {
        DemGrid grid = view.grid();
        int rings = lows.length;
        int[] columnX = new int[RAYS * rings + 1];
        int[] columnZ = new int[RAYS * rings + 1];
        for (int ray = 0; ray < RAYS; ray++) {
            double angle = 2D * Math.PI * ray / RAYS;
            for (int ring = 0; ring < rings; ring++) {
                float out = (ring + 1) * STEP;
                columnX[ray * rings + ring] = (int) Math.floor(x + out * Math.cos(angle));
                columnZ[ray * rings + ring] = (int) Math.floor(z + out * Math.sin(angle));
            }
        }
        columnX[RAYS * rings] = (int) Math.floor(x);
        columnZ[RAYS * rings] = (int) Math.floor(z);
        HydrologySampleBuffer samples = new HydrologySampleBuffer(columnX.length);
        fine.sampleColumns(columnX, columnZ, samples);
        Arrays.fill(lows, samples.height()[RAYS * rings]);
        float room = rings * STEP;
        for (int ray = 0; ray < RAYS; ray++) {
            boolean stopped = false;
            for (int ring = 0; ring < rings; ring++) {
                int index = ray * rings + ring;
                float out = (ring + 1) * STEP;
                if (ring + 1 < rings) {
                    lows[ring + 1] = Math.min(lows[ring + 1], samples.height()[index]);
                }
                if (stopped) {
                    continue;
                }
                int i = Math.floorDiv(columnX[index] - grid.originX(), CELL);
                int j = Math.floorDiv(columnZ[index] - grid.originZ(), CELL);
                if (i < 0 || j < 0 || i >= grid.width() || j >= grid.depth() || view.blocked()[grid.index(i, j)]
                        || !inTerritory(anchor, rivals, columnX[index], columnZ[index])) {
                    room = Math.min(room, out - STEP);
                    stopped = true;
                } else if (roofed && (samples.ocean()[index] || samples.height()[index] < sea + MIN_SIDE_GROUND)) {
                    room = Math.min(room, out);
                    stopped = true;
                }
            }
        }
        for (int ring = 1; ring < rings; ring++) {
            lows[ring] = Math.min(lows[ring], lows[ring - 1]);
        }
        return room;
    }

    private float[][] caverns(long id, float coastAt, float total) {
        float span = total - coastAt;
        int count = Math.max(1, (int) (span / CAVERN_EVERY));
        float[][] caverns = new float[count][];
        for (int index = 0; index < count; index++) {
            float place = index == count - 1 ? 0.84f : (index + 0.55f + 0.5f * (float) CellHash.unit(CellHash.mix(id, SALT_CAVERN, index, 0))) / count * 0.84f;
            float at = coastAt + span * place;
            float reach = CAVERN_REACH_MIN + CAVERN_REACH_SPAN * (float) CellHash.unit(CellHash.mix(id, SALT_CAVERN, index, 1));
            float width = CAVERN_WIDTH_MIN + CAVERN_WIDTH_SPAN * (float) CellHash.unit(CellHash.mix(id, SALT_CAVERN, index, 2));
            float high = CAVERN_ROOF_MIN + CAVERN_ROOF_SPAN * (float) CellHash.unit(CellHash.mix(id, SALT_CAVERN, index, 3));
            caverns[index] = new float[]{at, reach, width, high};
        }
        return caverns;
    }

    private static float slow(long id, long salt, float s) {
        int low = (int) Math.floor(s / PROFILE_WAVE);
        float t = s / PROFILE_WAVE - low;
        float fade = t * t * (3f - 2f * t);
        float a = (float) CellHash.unit(CellHash.mix(id, salt, low, 0));
        float b = (float) CellHash.unit(CellHash.mix(id, salt, low + 1, 0));
        return a + (b - a) * fade;
    }

    private Holes holes(long id, float[] xs, float[] zs, float[] along, Walk walk, float[] roof, float[] lift, float total) {
        List<float[]> holes = new ArrayList<>();
        if (CellHash.unit(CellHash.mix(id, SALT_HOLE, 0, 0)) < HOLE_ODDS) {
            float wanted = HOLE_FROM + (HOLE_TO - HOLE_FROM) * (float) CellHash.unit(CellHash.mix(id, SALT_HOLE, 1, 0));
            float coastAt = along[walk.coast()];
            int[] columnX = new int[1];
            int[] columnZ = new int[1];
            HydrologySampleBuffer sample = new HydrologySampleBuffer(1);
            for (int offset = 0; offset < xs.length && holes.isEmpty(); offset++) {
                int point = Math.floorMod(Math.round(xs.length * wanted) + offset, xs.length);
                float share = (along[point] - coastAt) / Math.max(1f, total - coastAt);
                if (share < HOLE_FROM || share > HOLE_TO || point < walk.coveredFrom()) {
                    continue;
                }
                columnX[0] = (int) Math.floor(xs[point]);
                columnZ[0] = (int) Math.floor(zs[point]);
                fine.sampleColumns(columnX, columnZ, sample);
                float cover = sample.height()[0] - sea - roof[point] - lift[point];
                if (cover >= HOLE_COVER_MIN && cover <= HOLE_COVER_MAX) {
                    float radius = HOLE_RADIUS_MIN + HOLE_RADIUS_SPAN * (float) CellHash.unit(CellHash.mix(id, SALT_HOLE, 2, 0));
                    holes.add(new float[]{xs[point], zs[point], radius, sample.height()[0] + HOLE_ABOVE});
                }
            }
        }
        float[] x = new float[holes.size()];
        float[] z = new float[holes.size()];
        float[] radius = new float[holes.size()];
        int[] top = new int[holes.size()];
        for (int index = 0; index < x.length; index++) {
            float[] hole = holes.get(index);
            x[index] = hole[0];
            z[index] = hole[1];
            radius[index] = hole[2];
            top[index] = (int) Math.ceil(hole[3]);
        }
        return new Holes(x, z, radius, top);
    }

    private static float[] smoothed(float[] values) {
        float[] out = values.clone();
        for (int point = 1; point < values.length - 1; point++) {
            out[point] = 0.25f * values[point - 1] + 0.5f * values[point] + 0.25f * values[point + 1];
        }
        return out;
    }

    private static float[] eased(float[] values) {
        float[] out = values;
        for (int pass = 0; pass < SMOOTH_PASSES; pass++) {
            out = smoothed(out);
        }
        for (int point = 0; point < out.length; point++) {
            out[point] = Math.min(out[point], values[point] + 1.5f);
        }
        return out;
    }

    private long[] cutOff(Grotto grotto) {
        GrottoShape shape = new GrottoShape(grotto);
        int[] bounds = grotto.bounds();
        int width = bounds[2] - bounds[0] + 1;
        int depth = bounds[3] - bounds[1] + 1;
        int bottom = shape.minY();
        int layers = sea - bottom + 1;
        BitSet wet = new BitSet(width * depth * layers);
        long total = 0L;
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                if (shape.near(bounds[0] + x, bounds[1] + z)) {
                    for (int layer = 0; layer < layers; layer++) {
                        if (shape.inside(bounds[0] + x, bottom + layer, bounds[1] + z)) {
                            wet.set((layer * depth + z) * width + x);
                            total++;
                        }
                    }
                }
            }
        }
        int startX = (int) Math.floor(grotto.xs()[0]) - bounds[0];
        int startZ = (int) Math.floor(grotto.zs()[0]) - bounds[1];
        int start = ((layers - 1) * depth + startZ) * width + startX;
        if (!wet.get(start)) {
            return null;
        }
        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        frontier.add(start);
        wet.clear(start);
        while (!frontier.isEmpty()) {
            int cell = frontier.poll();
            int x = cell % width;
            int rest = cell / width;
            int z = rest % depth;
            int layer = rest / depth;
            if (x + 1 < width) {
                flood(wet, frontier, cell + 1);
            }
            if (x > 0) {
                flood(wet, frontier, cell - 1);
            }
            if (z + 1 < depth) {
                flood(wet, frontier, cell + width);
            }
            if (z > 0) {
                flood(wet, frontier, cell - width);
            }
            if (layer + 1 < layers) {
                flood(wet, frontier, cell + width * depth);
            }
            if (layer > 0) {
                flood(wet, frontier, cell - width * depth);
            }
        }
        int left = wet.cardinality();
        if (left > CUT_OFF_SHARE * total) {
            return null;
        }
        long[] plugs = new long[left];
        int index = 0;
        for (int cell = wet.nextSetBit(0); cell >= 0; cell = wet.nextSetBit(cell + 1)) {
            int rest = cell / width;
            plugs[index++] = Grotto.plug(bounds[0] + cell % width, bottom + rest / depth, bounds[1] + rest % depth);
        }
        Arrays.sort(plugs);
        return plugs;
    }

    private static void flood(BitSet wet, ArrayDeque<Integer> frontier, int cell) {
        if (wet.get(cell)) {
            wet.clear(cell);
            frontier.add(cell);
        }
    }

    private void requireWithinWindow(WindowKey window, Grotto grotto) {
        int[] bounds = grotto.bounds();
        if (bounds[0] < (window.wx() - 1) * WindowKey.SIZE || bounds[2] >= (window.wx() + 2) * WindowKey.SIZE
                || bounds[1] < (window.wz() - 1) * WindowKey.SIZE || bounds[3] >= (window.wz() + 2) * WindowKey.SIZE) {
            throw new IllegalStateException("Grotto " + grotto.id() + " of window " + window + " reaches past the next window");
        }
    }

    private enum Check {
        SEA,
        OPEN,
        COVERED,
        REFUSED
    }

    private record View(DemGrid grid, HydrologySampleBuffer samples, float[] coast, boolean[] blocked) {
    }

    private record Candidate(int cell, float rock, double distance) {
    }

    private record Walk(float[] xs, float[] zs, int coast, int coveredFrom, long id) {
    }

    private record Holes(float[] x, float[] z, float[] radius, int[] top) {
    }

    private record Anchor(int siteX, int siteZ, WindowKey owner, double targetX, double targetZ, boolean active, int x, int z) {
    }
}
