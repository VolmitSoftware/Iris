package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.hydrology.water.ConfluenceGrader;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

final class ChunkFeatures {
    private static final int CHUNK = 16;
    private static final int SEGMENT_CAPACITY = 256;

    private final List<Reach> reaches;
    private final List<Pond> ponds;
    private final List<Pond> lakes;
    private final List<SeaInlet> inlets;
    private final int[] inletBoxes;
    private final int[] lakeBoxes;
    private final ReachSegments segments = new ReachSegments(SEGMENT_CAPACITY);
    private final MaskEdges pondShores = new MaskEdges(MaskEdges.Kind.WATER);
    private final MaskEdges pondBanks = new MaskEdges(MaskEdges.Kind.BANK);
    private final MaskEdges lakeReach = new MaskEdges(MaskEdges.Kind.GROWN_WATER);
    private boolean inert;

    private ChunkFeatures(List<Reach> reaches, List<Pond> ponds, List<Pond> lakes, List<SeaInlet> inlets, int[] inletBoxes) {
        this.reaches = reaches;
        this.ponds = ponds;
        this.lakes = lakes;
        this.inlets = inlets;
        this.inletBoxes = inletBoxes;
        this.lakeBoxes = new int[4 * lakes.size()];
    }

    static ChunkFeatures gather(HydrologySettings settings, int chunkX, int chunkZ, int halo, List<IndexedWindow> windows,
                                List<RegionPlan> lakeRegionPlans, List<RegionKey> lakeRegions) {
        Links links = new Links(windowsByKey(chunkX, chunkZ, windows));
        List<Reach> reaches = new ArrayList<>();
        List<Pond> ponds = new ArrayList<>();
        List<SeaInlet> inlets = new ArrayList<>();
        int[] inletBoxes = new int[0];
        for (IndexedWindow window : windows) {
            for (SeaInlet inlet : window.plan().inlets()) {
                int[] bounds = SeaInletRasterizer.bounds(inlet);
                int padding = RiffleProfile.RADIUS + 1;
                if (bounds[0] <= chunkX * CHUNK + CHUNK - 1 + padding && bounds[2] >= chunkX * CHUNK - padding
                        && bounds[1] <= chunkZ * CHUNK + CHUNK - 1 + padding && bounds[3] >= chunkZ * CHUNK - padding) {
                    inletBoxes = Arrays.copyOf(inletBoxes, inletBoxes.length + 4);
                    System.arraycopy(bounds, 0, inletBoxes, 4 * inlets.size(), 4);
                    inlets.add(inlet);
                }
            }
            int before = reaches.size();
            window.index().candidates(chunkX, chunkZ, halo, reaches, ponds);
            for (int index = before; index < reaches.size(); index++) {
                reaches.set(index, links.resolve(reaches.get(index), window.plan().key()));
            }
        }
        List<Pond> lakes = lakes(chunkX, chunkZ, halo, lakeRegionPlans, lakeRegions);
        ChunkFeatures features = new ChunkFeatures(reaches, ponds, lakes, inlets, inletBoxes);
        features.index(settings, chunkX, chunkZ);
        return features;
    }

    boolean inert() {
        return inert;
    }

    SeaInlet openInletAt(int x, int z, int[] column) {
        for (int index = 0; index < inlets.size(); index++) {
            int box = 4 * index;
            if (x < inletBoxes[box] || z < inletBoxes[box + 1] || x > inletBoxes[box + 2] || z > inletBoxes[box + 3]) {
                continue;
            }
            SeaInlet inlet = inlets.get(index);
            if (SeaInletRasterizer.column(inlet, x, z, column) && column[1] == SeaInlet.OPEN) {
                return inlet;
            }
        }
        return null;
    }

    ReachSegments segments() {
        return segments;
    }

    boolean hasOnlyLevel(int head, int seaLevel) {
        for (Pond pond : ponds) {
            if (WaterHeads.snap(pond.level(), seaLevel) != head) {
                return false;
            }
        }
        for (Pond lake : lakes) {
            if (WaterHeads.snap(lake.level(), seaLevel) != head) {
                return false;
            }
        }
        for (SeaInlet inlet : inlets) {
            if (WaterHeads.snap(inlet.waterLevel(), seaLevel) != head) {
                return false;
            }
        }
        return segments.hasOnlyLevel(head, seaLevel);
    }

    int ponds() {
        return ponds.size();
    }

    Pond pond(int index) {
        return ponds.get(index);
    }

    int lakes() {
        return lakes.size();
    }

    Pond lake(int index) {
        return lakes.get(index);
    }

    int pondAt(int x, int z) {
        int found = -1;
        for (int index = 0; index < ponds.size(); index++) {
            Pond pond = ponds.get(index);
            if (pond.mask().contains(x, z) && (found < 0 || lower(pond, ponds.get(found)))) {
                found = index;
            }
        }
        return found;
    }

    int lakeAt(int x, int z, int natural) {
        int found = -1;
        for (int index = 0; index < lakes.size(); index++) {
            Pond lake = lakes.get(index);
            if (natural < lake.level() && inLakeBox(index, x, z) && lake.mask().grownContains(x, z)
                    && (found < 0 || lake.level() < lakes.get(found).level())) {
                found = index;
            }
        }
        return found;
    }

    boolean lakeCovers(int x, int z) {
        for (int index = 0; index < lakes.size(); index++) {
            if (inLakeBox(index, x, z) && lakes.get(index).mask().grownContains(x, z)) {
                return true;
            }
        }
        return false;
    }

    double pondShoreDistance(int pond, int x, int z) {
        return pondShores.distance(pond, x, z);
    }

    double pondBankDistance(int pond, int x, int z) {
        return pondBanks.distance(pond, x, z);
    }

    long lakeReachSquared(int lake, int x, int z) {
        return lakeReach.squared(lake, x, z);
    }

    private boolean inLakeBox(int index, int x, int z) {
        int box = 4 * index;
        return x >= lakeBoxes[box] && z >= lakeBoxes[box + 1] && x < lakeBoxes[box + 2] && z < lakeBoxes[box + 3];
    }

    private void index(HydrologySettings settings, int chunkX, int chunkZ) {
        int minX = chunkX * CHUNK;
        int minZ = chunkZ * CHUNK;
        int maxX = minX + CHUNK - 1;
        int maxZ = minZ + CHUNK - 1;
        float outward = ChunkWaterRaster.MAX_SHORE_DISTANCE + 1f;
        for (Reach reach : reaches) {
            segments.addNear(reach, minX - 1f, minZ - 1f, maxX + 1f, maxZ + 1f, outward);
        }
        int riffle = RiffleProfile.RADIUS + 1;
        segments.coverChannels(minX - riffle, minZ - riffle, maxX + riffle, maxZ + riffle);
        HydrologySettings.PondShore shore = settings.ponds().shore();
        int reach = Math.max(shore.taper(), ChunkWaterRaster.MAX_SHORE_DISTANCE) + 1;
        int shelf = shore.shelf() + 1;
        for (Pond pond : ponds) {
            pondShores.collect(pond.mask(), minX - reach, minZ - reach, maxX + reach, maxZ + reach);
            pondBanks.collect(pond.mask(), minX - shelf, minZ - shelf, maxX + shelf, maxZ + shelf);
        }
        int shoreReach = ChunkWaterRaster.MAX_SHORE_DISTANCE;
        for (int index = 0; index < lakes.size(); index++) {
            PondMask mask = lakes.get(index).mask();
            lakeReach.collect(mask, minX - shoreReach, minZ - shoreReach, maxX + shoreReach, maxZ + shoreReach);
            System.arraycopy(grownBox(mask), 0, lakeBoxes, 4 * index, 4);
        }
        inert = segments.isEmpty() && !inletsTouch(minX - 1, minZ - 1, maxX + 1, maxZ + 1)
                && !pondsTouch(minX - 1 - reach, minZ - 1 - reach, maxX + 1 + reach, maxZ + 1 + reach)
                && !lakesTouch(minX - 2 - shoreReach, minZ - 2 - shoreReach, maxX + 2 + shoreReach, maxZ + 2 + shoreReach);
    }

    private boolean inletsTouch(int minX, int minZ, int maxX, int maxZ) {
        for (int box = 0; box < inletBoxes.length; box += 4) {
            if (inletBoxes[box] <= maxX && inletBoxes[box + 2] >= minX && inletBoxes[box + 1] <= maxZ && inletBoxes[box + 3] >= minZ) {
                return true;
            }
        }
        return false;
    }

    private boolean pondsTouch(int minX, int minZ, int maxX, int maxZ) {
        for (Pond pond : ponds) {
            PondMask mask = pond.mask();
            int fromX = mask.originX() - (mask.step() >> 1);
            int fromZ = mask.originZ() - (mask.step() >> 1);
            if (fromX <= maxX && fromX + mask.width() * mask.step() > minX && fromZ <= maxZ && fromZ + mask.depth() * mask.step() > minZ) {
                return true;
            }
        }
        return false;
    }

    private boolean lakesTouch(int minX, int minZ, int maxX, int maxZ) {
        for (int box = 0; box < lakeBoxes.length; box += 4) {
            if (lakeBoxes[box] <= maxX && lakeBoxes[box + 2] > minX && lakeBoxes[box + 1] <= maxZ && lakeBoxes[box + 3] > minZ) {
                return true;
            }
        }
        return false;
    }

    private static Map<WindowKey, IndexedWindow> windowsByKey(int chunkX, int chunkZ, List<IndexedWindow> windows) {
        Objects.requireNonNull(windows, "windows");
        Map<WindowKey, IndexedWindow> byKey = new HashMap<>();
        for (IndexedWindow window : windows) {
            if (byKey.put(window.plan().key(), window) != null) {
                throw new IllegalArgumentException("Chunk " + chunkX + "," + chunkZ + " got two plans for window " + window.plan().key());
            }
        }
        WindowKey own = WindowKey.containing(chunkX * CHUNK, chunkZ * CHUNK);
        if (!byKey.containsKey(own)) {
            throw new IllegalArgumentException("Chunk " + chunkX + "," + chunkZ + " needs its own window plan " + own + ", got " + byKey.keySet());
        }
        return byKey;
    }

    private static List<Pond> lakes(int chunkX, int chunkZ, int halo, List<RegionPlan> plans, List<RegionKey> required) {
        Objects.requireNonNull(plans, "lakeRegionPlans");
        Set<RegionKey> given = new HashSet<>();
        for (RegionPlan plan : plans) {
            given.add(plan.key());
        }
        if (!given.containsAll(required)) {
            throw new IllegalArgumentException("Chunk " + chunkX + "," + chunkZ + " needs the lake region plans " + required + ", got " + given);
        }
        int minX = chunkX * CHUNK - halo;
        int minZ = chunkZ * CHUNK - halo;
        int endX = chunkX * CHUNK + CHUNK + halo;
        int endZ = chunkZ * CHUNK + CHUNK + halo;
        TreeMap<Long, Pond> byId = new TreeMap<>();
        for (RegionPlan plan : plans) {
            for (Pond lake : plan.lakes()) {
                int[] box = grownBox(lake.mask());
                if (box[0] < endX && box[2] > minX && box[1] < endZ && box[3] > minZ) {
                    byId.putIfAbsent(lake.id(), lake);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static int[] grownBox(PondMask mask) {
        int step = mask.step();
        int minX = mask.originX() - (step >> 1) - step;
        int minZ = mask.originZ() - (step >> 1) - step;
        return new int[]{minX, minZ, minX + (mask.width() + 2) * step, minZ + (mask.depth() + 2) * step};
    }

    private static boolean lower(Pond candidate, Pond current) {
        return candidate.level() < current.level() || candidate.level() == current.level() && candidate.id() < current.id();
    }

    private static final class Links {
        private final Map<WindowKey, IndexedWindow> windows;
        private final Map<Long, Reach> owned = new HashMap<>();
        private final Map<Long, List<Pond>> outletPonds = new HashMap<>();
        private final Map<Long, Reach> resolved = new HashMap<>();
        private final Set<Long> resolving = new HashSet<>();

        private Links(Map<WindowKey, IndexedWindow> windows) {
            this.windows = windows;
            for (IndexedWindow window : windows.values()) {
                for (Reach reach : window.plan().reaches()) {
                    Reach other = owned.putIfAbsent(reach.id(), reach);
                    if (other != null) {
                        throw new IllegalStateException("Reach " + reach.id() + " is owned by window " + window.plan().key()
                                + " and by window " + windowAt(other, other.xs().length - 1));
                    }
                }
                for (Pond pond : window.plan().ponds()) {
                    if (pond.outletReachId() != 0L) {
                        outletPonds.computeIfAbsent(pond.outletReachId(), ignored -> new ArrayList<>()).add(pond);
                    }
                }
            }
        }

        private Reach resolve(Reach reach, WindowKey owner) {
            Reach known = resolved.get(reach.id());
            if (known != null) {
                return known;
            }
            if (!resolving.add(reach.id())) {
                return reach;
            }
            Reach linked = link(reach, owner);
            resolving.remove(reach.id());
            resolved.put(reach.id(), linked);
            return linked;
        }

        private Reach link(Reach reach, WindowKey owner) {
            Reach linked = feederLink(reach, owner);
            Integer pondLevel = outletLevel(reach);
            return settle(reach, pondLevel == null ? linked : LinkBlend.clamp(linked, pondLevel), owner);
        }

        private Reach settle(Reach feeder, Reach linked, WindowKey owner) {
            Reach downstream = owned.get(feeder.downstreamId());
            if (downstream == null || owner.equals(windowAt(downstream, downstream.xs().length - 1))) {
                return linked;
            }
            int level = downstream.grade()[0];
            Integer pondLevel = outletLevel(downstream);
            if (pondLevel != null) {
                level = Math.min(level, pondLevel);
            }
            List<ConfluenceGrader.Footprint> receiving = new ArrayList<>();
            receiving.add(LinkBlend.footprint(downstream));
            for (Reach sibling : feeders(owner, downstream.id())) {
                level = Math.min(level, sibling.grade()[sibling.grade().length - 1]);
                if (sibling.id() != feeder.id()) {
                    receiving.add(LinkBlend.footprint(sibling));
                }
            }
            return LinkBlend.settleInto(linked, level, receiving);
        }

        private Reach feederLink(Reach downstream, WindowKey owner) {
            int junction = junction(downstream);
            WindowKey link = windowAt(downstream, junction);
            List<Reach> owned = feeders(link, downstream.id());
            if (owned.isEmpty() && junction > 0) {
                link = windowAt(downstream, 0);
                owned = feeders(link, downstream.id());
            }
            if (owned.isEmpty()) {
                return downstream;
            }
            long block = cornerBlock(downstream);
            List<Reach> feeders = new ArrayList<>(owned.size());
            for (Reach reach : owned) {
                feeders.add(cornerBlock(reach) == block ? resolve(reach, link) : reach);
            }
            return link.equals(owner) ? LinkBlend.clamp(downstream, feeders) : LinkBlend.blend(downstream, feeders, junction);
        }

        private List<Reach> feeders(WindowKey key, long downstreamId) {
            IndexedWindow window = windows.get(key);
            return window == null ? List.of() : window.index().feeders(downstreamId);
        }

        private int junction(Reach reach) {
            return source(reach) == null ? 0 : 1;
        }

        private Integer outletLevel(Reach reach) {
            List<Pond> ponds = outletPonds.get(reach.id());
            if (ponds == null) {
                return null;
            }
            Pond source = source(reach);
            if (source != null) {
                return source.level();
            }
            int level = Integer.MAX_VALUE;
            for (Pond pond : ponds) {
                level = Math.min(level, pond.level());
            }
            return level;
        }

        private Pond source(Reach reach) {
            List<Pond> ponds = outletPonds.get(reach.id());
            if (ponds == null) {
                return null;
            }
            int x = Math.round(reach.xs()[0]);
            int z = Math.round(reach.zs()[0]);
            Pond source = null;
            for (Pond pond : ponds) {
                if (pond.mask().contains(x, z) && (source == null || lower(pond, source))) {
                    source = pond;
                }
            }
            return source;
        }

        private long cornerBlock(Reach reach) {
            int junction = junction(reach);
            int half = WindowKey.SIZE >> 1;
            long cornerX = Math.floorDiv((int) Math.floor(reach.xs()[junction]) + half, WindowKey.SIZE);
            long cornerZ = Math.floorDiv((int) Math.floor(reach.zs()[junction]) + half, WindowKey.SIZE);
            return (cornerX << 32) | (cornerZ & 0xffffffffL);
        }

        private static WindowKey windowAt(Reach reach, int point) {
            return WindowKey.containing((int) Math.floor(reach.xs()[point]), (int) Math.floor(reach.zs()[point]));
        }
    }
}
