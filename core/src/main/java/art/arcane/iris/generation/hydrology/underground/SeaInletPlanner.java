package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class SeaInletPlanner {
    private static final int ROOF_MARGIN = 2;
    private static final int OPEN_LAND_STATIONS = 2;
    private static final float NOISE_STEP = 32f;
    private static final int CONTAINMENT = 2;
    private static final float WET_SLACK = CONTAINMENT + 1 + 6f;
    private static final int[] SIDE_I = {1, -1, 0, 0};
    private static final int[] SIDE_J = {0, 0, 1, -1};
    private static final long SALT_ID = 0x5345414e4c4554L;
    private static final long SALT_SITE_X = 0x53454153495458L;
    private static final long SALT_SITE_Z = 0x5345415349545aL;
    private static final long SALT_LENGTH = 0x5345414c454e47L;
    private static final long SALT_RADIUS = 0x53454152414449L;
    private static final long SALT_HEADROOM = 0x53454148454144L;
    private static final long SALT_DEPTH = 0x53454144455054L;

    private final HydrologySettings settings;

    public SeaInletPlanner(HydrologySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public List<SeaInlet> plan(DemGrid raw, HydrologySampleBuffer samples, int[] receivers, float[] accumulation, ShapedNetwork network,
                               long seed) {
        HydrologySettings.SeaInlets config = settings.underground().seaInlets();
        if (!config.enabled()) {
            return List.of();
        }
        int count = raw.values().length;
        if (samples.height().length < count || receivers.length != count || accumulation.length != count) {
            throw new IllegalArgumentException("Sea inlet planning needs " + count + " cells, got samples=" + samples.height().length
                    + " receivers=" + receivers.length + " accumulation=" + accumulation.length);
        }
        Terrain terrain = new Terrain(raw, samples, receivers, accumulation, cover(raw, samples.height(), network), seed);
        List<SeaInlet> inlets = new ArrayList<>();
        for (Map.Entry<Long, List<Integer>> site : sites(terrain, config).entrySet()) {
            SeaInlet inlet = site(terrain, config, site.getKey(), site.getValue());
            if (inlet != null) {
                inlets.add(inlet);
            }
        }
        inlets.sort(Comparator.comparingLong(SeaInlet::id).thenComparingDouble(inlet -> inlet.xs()[0])
                .thenComparingDouble(inlet -> inlet.zs()[0]));
        return separated(inlets);
    }

    private static List<SeaInlet> separated(List<SeaInlet> inlets) {
        List<SeaInletFootprint> footprints = new ArrayList<>(inlets.size());
        for (SeaInlet inlet : inlets) {
            footprints.add(SeaInletFootprint.of(inlet));
        }
        List<SeaInlet> kept = new ArrayList<>(inlets.size());
        for (int index = 0; index < inlets.size(); index++) {
            boolean yields = false;
            for (int earlier = 0; earlier < index && !yields; earlier++) {
                yields = footprints.get(earlier).conflicts(footprints.get(index));
            }
            if (!yields) {
                kept.add(inlets.get(index));
            }
        }
        return List.copyOf(kept);
    }

    private Map<Long, List<Integer>> sites(Terrain terrain, HydrologySettings.SeaInlets config) {
        Map<Long, List<Integer>> sites = new TreeMap<>();
        DemGrid grid = terrain.grid;
        float cliff = settings.seaLevel() + config.minimumCliffHeight();
        for (int j = 1; j < grid.depth() - 1; j++) {
            for (int i = 1; i < grid.width() - 1; i++) {
                int cell = grid.index(i, j);
                if (terrain.ocean(cell) || terrain.natural(cell) < cliff || mouth(terrain, cell) < 0) {
                    continue;
                }
                long key = siteKey(Math.floorDiv(grid.blockX(cell), config.spacing()), Math.floorDiv(grid.blockZ(cell), config.spacing()));
                sites.computeIfAbsent(key, ignored -> new ArrayList<>()).add(cell);
            }
        }
        return sites;
    }

    private SeaInlet site(Terrain terrain, HydrologySettings.SeaInlets config, long key, List<Integer> candidates) {
        int siteX = (int) (key >> 32);
        int siteZ = (int) key;
        double targetX = (siteX + CellHash.unit(CellHash.mix(terrain.seed, SALT_SITE_X, siteX, siteZ))) * config.spacing();
        double targetZ = (siteZ + CellHash.unit(CellHash.mix(terrain.seed, SALT_SITE_Z, siteX, siteZ))) * config.spacing();
        DemGrid grid = terrain.grid;
        WindowKey owner = WindowKey.containing((int) Math.floor(targetX), (int) Math.floor(targetZ));
        List<Integer> ordered = new ArrayList<>(candidates.size());
        for (int candidate : candidates) {
            if (owner.equals(WindowKey.containing(grid.blockX(candidate), grid.blockZ(candidate)))) {
                ordered.add(candidate);
            }
        }
        ordered.sort(Comparator.comparingDouble((Integer cell) -> distanceSquared(grid, cell, targetX, targetZ)).thenComparingInt(cell -> cell));
        for (int candidate : ordered) {
            SeaInlet inlet = trace(terrain, config, candidate);
            if (inlet != null) {
                return inlet;
            }
        }
        return null;
    }

    private SeaInlet trace(Terrain terrain, HydrologySettings.SeaInlets config, int land) {
        DemGrid grid = terrain.grid;
        int mouth = mouth(terrain, land);
        if (!openable(terrain, mouth)) {
            return null;
        }
        float target = lerp(config.length().min(), config.length().max(), unit(terrain, SALT_LENGTH, land));
        List<Integer> path = new ArrayList<>();
        List<Boolean> open = new ArrayList<>();
        path.add(mouth);
        open.add(true);
        int current = land;
        float length = 0f;
        int openStations = 0;
        boolean covered = false;
        while (current >= 0 && length < target) {
            if (canCover(terrain, config, current)) {
                covered = true;
            } else if (covered || openStations >= OPEN_LAND_STATIONS || !openable(terrain, current)) {
                break;
            } else {
                openStations++;
            }
            length += step(grid, path.get(path.size() - 1), current);
            path.add(current);
            open.add(!covered);
            int next = upstream(terrain, mouth, current, path);
            current = next >= 0 ? next : inland(terrain, config, mouth, current, path);
        }
        while (covered && path.size() > 2 && !open.get(path.size() - 1)) {
            if (pathLength(grid, path) < config.length().min()) {
                return null;
            }
            SeaInlet inlet = build(terrain, config, path, open);
            if (inlet != null) {
                return inlet;
            }
            path.remove(path.size() - 1);
            open.remove(open.size() - 1);
        }
        return null;
    }

    private SeaInlet build(Terrain terrain, HydrologySettings.SeaInlets config, List<Integer> path, List<Boolean> open) {
        DemGrid grid = terrain.grid;
        int stations = path.size();
        int mouth = path.get(0);
        int land = path.get(1);
        int end = path.get(stations - 1);
        int water = settings.seaLevel();
        float chamberCapacity = Float.NaN;
        int radius = 0;
        int preferred = Math.round(lerp(config.chamberRadius().min(), config.chamberRadius().max(), unit(terrain, SALT_RADIUS, end)));
        for (int candidate : new int[]{preferred, config.chamberRadius().min()}) {
            float capacity = capacity(terrain, config, end, (candidate + grid.cell() - 1) / grid.cell() + 1);
            if (capacity - water >= config.headroom().min()) {
                radius = candidate;
                chamberCapacity = capacity;
                break;
            }
        }
        if (radius == 0) {
            return null;
        }
        float[] capacity = new float[stations];
        for (int station = 0; station < stations; station++) {
            capacity[station] = open.get(station) ? Float.POSITIVE_INFINITY
                    : station == stations - 1 ? chamberCapacity : capacity(terrain, config, path.get(station), 1);
        }
        float total = pathLength(grid, path);
        float[] xs = new float[stations];
        float[] zs = new float[stations];
        float[] halfWidth = new float[stations];
        int[] floor = new int[stations];
        int[] ceiling = new int[stations];
        float travelled = 0f;
        HydrologySettings.FloatRange widths = config.halfWidth();
        for (int station = 0; station < stations; station++) {
            int cell = path.get(station);
            if (station > 0) {
                travelled += step(grid, path.get(station - 1), cell);
            }
            xs[station] = grid.blockX(cell);
            zs[station] = grid.blockZ(cell);
            halfWidth[station] = widths.max() - (widths.max() - widths.min()) * (travelled / total);
            floor[station] = water - Math.round(lerp(config.depth().min(), config.depth().max(), smooth(terrain, SALT_DEPTH, land, travelled)));
            if (open.get(station)) {
                ceiling[station] = SeaInlet.OPEN;
                continue;
            }
            float room = capacity[station];
            if (station > 0) {
                room = Math.min(room, capacity[station - 1]);
            }
            if (station + 1 < stations) {
                room = Math.min(room, capacity[station + 1]);
            }
            float headroom = lerp(config.headroom().min(), config.headroom().max(), smooth(terrain, SALT_HEADROOM, land, travelled));
            ceiling[station] = water + (int) Math.floor(Math.min(headroom, room - water));
        }
        long id = CellHash.mix(CellHash.mix(terrain.seed, SALT_ID, grid.blockX(mouth), grid.blockZ(mouth)), SALT_ID, grid.blockX(land),
                grid.blockZ(land));
        return new SeaInlet(id, xs, zs, halfWidth, water, floor, ceiling, grid.blockX(end), grid.blockZ(end), radius);
    }

    private static double smooth(Terrain terrain, long salt, int land, float distance) {
        float position = distance / NOISE_STEP;
        int knot = (int) Math.floor(position);
        double fraction = position - knot;
        double eased = fraction * fraction * (3D - 2D * fraction);
        long base = CellHash.mix(terrain.seed, salt, terrain.grid.blockX(land), terrain.grid.blockZ(land));
        double from = CellHash.unit(CellHash.mix(base, salt, knot, 0));
        double to = CellHash.unit(CellHash.mix(base, salt, knot + 1, 0));
        return from + (to - from) * eased;
    }

    private boolean canCover(Terrain terrain, HydrologySettings.SeaInlets config, int cell) {
        return capacity(terrain, config, cell, 1) - settings.seaLevel() >= config.headroom().min();
    }

    private static float capacity(Terrain terrain, HydrologySettings.SeaInlets config, int cell, int ring) {
        DemGrid grid = terrain.grid;
        int i = cell % grid.width();
        int j = cell / grid.width();
        float lowest = Float.POSITIVE_INFINITY;
        for (int dj = -ring; dj <= ring; dj++) {
            for (int di = -ring; di <= ring; di++) {
                int ni = i + di;
                int nj = j + dj;
                if (ni < 0 || nj < 0 || ni >= grid.width() || nj >= grid.depth()) {
                    return Float.NEGATIVE_INFINITY;
                }
                int neighbour = grid.index(ni, nj);
                if (terrain.ocean(neighbour)) {
                    return Float.NEGATIVE_INFINITY;
                }
                lowest = Math.min(lowest, terrain.cover[neighbour]);
            }
        }
        return lowest - config.roofClearance() - ROOF_MARGIN;
    }

    private static boolean openable(Terrain terrain, int cell) {
        DemGrid grid = terrain.grid;
        int i = cell % grid.width();
        int j = cell / grid.width();
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                int ni = i + di;
                int nj = j + dj;
                if (ni < 0 || nj < 0 || ni >= grid.width() || nj >= grid.depth() || terrain.wet(grid.index(ni, nj))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int mouth(Terrain terrain, int land) {
        int receiver = terrain.receivers[land];
        if (receiver >= 0 && terrain.ocean(receiver)) {
            return receiver;
        }
        DemGrid grid = terrain.grid;
        int i = land % grid.width();
        int j = land / grid.width();
        for (int side = 0; side < SIDE_I.length; side++) {
            int ni = i + SIDE_I[side];
            int nj = j + SIDE_J[side];
            if (ni >= 0 && nj >= 0 && ni < grid.width() && nj < grid.depth() && terrain.ocean(grid.index(ni, nj))) {
                return grid.index(ni, nj);
            }
        }
        return -1;
    }

    private static int upstream(Terrain terrain, int mouth, int cell, List<Integer> path) {
        DemGrid grid = terrain.grid;
        int i = cell % grid.width();
        int j = cell / grid.width();
        double away = distanceSquared(grid, cell, grid.blockX(mouth), grid.blockZ(mouth));
        int best = -1;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                int ni = i + di;
                int nj = j + dj;
                if ((di == 0 && dj == 0) || ni <= 0 || nj <= 0 || ni >= grid.width() - 1 || nj >= grid.depth() - 1) {
                    continue;
                }
                int neighbour = grid.index(ni, nj);
                if (terrain.receivers[neighbour] != cell || terrain.ocean(neighbour) || path.contains(neighbour)
                        || distanceSquared(grid, neighbour, grid.blockX(mouth), grid.blockZ(mouth)) <= away) {
                    continue;
                }
                if (best < 0 || terrain.accumulation[neighbour] > terrain.accumulation[best]
                        || terrain.accumulation[neighbour] == terrain.accumulation[best] && neighbour < best) {
                    best = neighbour;
                }
            }
        }
        return best;
    }

    private int inland(Terrain terrain, HydrologySettings.SeaInlets config, int mouth, int cell, List<Integer> path) {
        DemGrid grid = terrain.grid;
        int i = cell % grid.width();
        int j = cell / grid.width();
        double away = distanceSquared(grid, cell, grid.blockX(mouth), grid.blockZ(mouth));
        int best = -1;
        boolean bestCovers = false;
        double bestAway = 0D;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                int ni = i + di;
                int nj = j + dj;
                if ((di == 0 && dj == 0) || ni <= 0 || nj <= 0 || ni >= grid.width() - 1 || nj >= grid.depth() - 1) {
                    continue;
                }
                int neighbour = grid.index(ni, nj);
                double distance = distanceSquared(grid, neighbour, grid.blockX(mouth), grid.blockZ(mouth));
                if (terrain.ocean(neighbour) || path.contains(neighbour) || distance <= away) {
                    continue;
                }
                boolean covers = canCover(terrain, config, neighbour);
                if (best < 0 || covers && !bestCovers || covers == bestCovers && (distance > bestAway
                        || distance == bestAway && terrain.cover[neighbour] > terrain.cover[best])) {
                    best = neighbour;
                    bestCovers = covers;
                    bestAway = distance;
                }
            }
        }
        return best;
    }

    private Cover cover(DemGrid grid, float[] natural, ShapedNetwork network) {
        int count = grid.values().length;
        float[] cover = new float[count];
        System.arraycopy(natural, 0, cover, 0, count);
        boolean[] wet = new boolean[count];
        for (Reach reach : network.reaches()) {
            int band = reach.tier().bankBand() + CONTAINMENT + grid.cell();
            for (int point = 0; point + 1 < reach.xs().length; point++) {
                float bed = Math.min(reach.grade()[point] - reach.depth()[point], reach.grade()[point + 1] - reach.depth()[point + 1]) - 1f;
                float water = Math.max(reach.halfWidth()[point], reach.halfWidth()[point + 1]);
                splat(grid, cover, wet, reach.xs()[point], reach.zs()[point], reach.xs()[point + 1], reach.zs()[point + 1], water + band,
                        water + WET_SLACK, bed);
            }
        }
        HydrologySettings.PondShore shore = settings.ponds().shore();
        for (Pond pond : network.ponds()) {
            PondMask mask = pond.mask();
            float bed = Math.min(pond.floor(), pond.level() - shore.maxDepth()) - 1f;
            float radius = 2f * mask.step() + shore.taper() + CONTAINMENT + grid.cell();
            float water = 1.5f * mask.step() + WET_SLACK;
            for (int nj = 0; nj < mask.depth(); nj++) {
                for (int ni = 0; ni < mask.width(); ni++) {
                    if (mask.node(ni, nj)) {
                        float x = mask.originX() + ni * mask.step();
                        float z = mask.originZ() + nj * mask.step();
                        splat(grid, cover, wet, x, z, x, z, radius, water, bed);
                    }
                }
            }
        }
        return new Cover(cover, wet);
    }

    private static void splat(DemGrid grid, float[] cover, boolean[] wet, float ax, float az, float bx, float bz, float radius, float wetRadius,
                              float bed) {
        int cell = grid.cell();
        int minI = Math.max(0, (int) Math.floor((Math.min(ax, bx) - radius - grid.originX()) / cell));
        int maxI = Math.min(grid.width() - 1, (int) Math.ceil((Math.max(ax, bx) + radius - grid.originX()) / cell));
        int minJ = Math.max(0, (int) Math.floor((Math.min(az, bz) - radius - grid.originZ()) / cell));
        int maxJ = Math.min(grid.depth() - 1, (int) Math.ceil((Math.max(az, bz) + radius - grid.originZ()) / cell));
        double dx = bx - ax;
        double dz = bz - az;
        double span = dx * dx + dz * dz;
        for (int j = minJ; j <= maxJ; j++) {
            double pz = grid.originZ() + (double) j * cell;
            for (int i = minI; i <= maxI; i++) {
                double px = grid.originX() + (double) i * cell;
                double t = span <= 0D ? 0D : Math.clamp(((px - ax) * dx + (pz - az) * dz) / span, 0D, 1D);
                double distance = Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
                if (distance > radius) {
                    continue;
                }
                int index = j * grid.width() + i;
                cover[index] = Math.min(cover[index], bed);
                wet[index] |= distance <= wetRadius;
            }
        }
    }

    private static float pathLength(DemGrid grid, List<Integer> path) {
        float length = 0f;
        for (int station = 1; station < path.size(); station++) {
            length += step(grid, path.get(station - 1), path.get(station));
        }
        return length;
    }

    private static float step(DemGrid grid, int from, int to) {
        return (float) Math.hypot(grid.blockX(to) - grid.blockX(from), grid.blockZ(to) - grid.blockZ(from));
    }

    private static double distanceSquared(DemGrid grid, int cell, double x, double z) {
        double dx = grid.blockX(cell) - x;
        double dz = grid.blockZ(cell) - z;
        return dx * dx + dz * dz;
    }

    private static double unit(Terrain terrain, long salt, int cell) {
        return CellHash.unit(CellHash.mix(terrain.seed, salt, terrain.grid.blockX(cell), terrain.grid.blockZ(cell)));
    }

    private static float lerp(float min, float max, double unit) {
        return (float) (min + (max - min) * unit);
    }

    private static long siteKey(int siteX, int siteZ) {
        return ((long) siteX << 32) | (siteZ & 0xFFFFFFFFL);
    }

    private record Cover(float[] cover, boolean[] wet) {
    }

    private static final class Terrain {
        private final DemGrid grid;
        private final HydrologySampleBuffer samples;
        private final int[] receivers;
        private final float[] accumulation;
        private final float[] cover;
        private final boolean[] wet;
        private final long seed;

        private Terrain(DemGrid grid, HydrologySampleBuffer samples, int[] receivers, float[] accumulation, Cover cover, long seed) {
            this.grid = grid;
            this.samples = samples;
            this.receivers = receivers;
            this.accumulation = accumulation;
            this.cover = cover.cover();
            this.wet = cover.wet();
            this.seed = seed;
        }

        private boolean ocean(int cell) {
            return samples.ocean()[cell];
        }

        private float natural(int cell) {
            return samples.height()[cell];
        }

        private boolean wet(int cell) {
            return wet[cell];
        }
    }
}
