package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.ArrayList;
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
    private final int[] lakeBoxes;
    private final ReachSegments segments = new ReachSegments(SEGMENT_CAPACITY);
    private final MaskEdges pondShores = new MaskEdges(MaskEdges.Kind.WATER);
    private final MaskEdges pondBanks = new MaskEdges(MaskEdges.Kind.BANK);
    private final MaskEdges lakeReach = new MaskEdges(MaskEdges.Kind.GROWN_WATER);

    private ChunkFeatures(List<Reach> reaches, List<Pond> ponds, List<Pond> lakes, List<SeaInlet> inlets) {
        this.reaches = reaches;
        this.ponds = ponds;
        this.lakes = lakes;
        this.inlets = inlets;
        this.lakeBoxes = new int[4 * lakes.size()];
    }

    static ChunkFeatures gather(HydrologySettings settings, int chunkX, int chunkZ, int halo, List<IndexedWindow> windows,
                                List<RegionPlan> lakeRegionPlans, List<RegionKey> lakeRegions) {
        Links links = new Links(windowsByKey(chunkX, chunkZ, windows));
        List<Reach> reaches = new ArrayList<>();
        List<Pond> ponds = new ArrayList<>();
        List<SeaInlet> inlets = new ArrayList<>();
        for (IndexedWindow window : windows) {
            for (SeaInlet inlet : window.plan().inlets()) {
                if (SeaInletRasterizer.touches(inlet, chunkX, chunkZ)) {
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
        ChunkFeatures features = new ChunkFeatures(reaches, ponds, lakes, inlets);
        features.index(settings, chunkX, chunkZ);
        return features;
    }

    boolean empty() {
        return reaches.isEmpty() && ponds.isEmpty() && lakes.isEmpty() && inlets.isEmpty();
    }

    SeaInlet openInletAt(int x, int z, int[] column) {
        for (SeaInlet inlet : inlets) {
            if (SeaInletRasterizer.column(inlet, x, z, column) && column[1] == SeaInlet.OPEN) {
                return inlet;
            }
        }
        return null;
    }

    ReachSegments segments() {
        return segments;
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
        private final Map<Long, Integer> outletLevels = new HashMap<>();
        private final Map<Long, Reach> resolved = new HashMap<>();
        private final Set<Long> resolving = new HashSet<>();

        private Links(Map<WindowKey, IndexedWindow> windows) {
            this.windows = windows;
            for (IndexedWindow window : windows.values()) {
                for (Pond pond : window.plan().ponds()) {
                    if (pond.outletReachId() != 0L) {
                        outletLevels.merge(pond.outletReachId(), pond.level(), Math::min);
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

        private Reach link(Reach downstream, WindowKey owner) {
            Reach linked = feederLink(downstream, owner);
            Integer pondLevel = outletLevels.get(linked.id());
            return pondLevel == null ? linked : LinkBlend.clamp(linked, pondLevel);
        }

        private Reach feederLink(Reach downstream, WindowKey owner) {
            WindowKey link = WindowKey.containing((int) Math.floor(downstream.xs()[0]), (int) Math.floor(downstream.zs()[0]));
            IndexedWindow upstream = windows.get(link);
            List<Reach> owned = upstream == null ? List.of() : upstream.index().feeders(downstream.id());
            if (owned.isEmpty()) {
                return downstream;
            }
            long block = cornerBlock(downstream);
            List<Reach> feeders = new ArrayList<>(owned.size());
            for (Reach reach : owned) {
                feeders.add(cornerBlock(reach) == block ? resolve(reach, link) : reach);
            }
            return link.equals(owner) ? LinkBlend.clamp(downstream, feeders) : LinkBlend.blend(downstream, feeders);
        }

        private static long cornerBlock(Reach reach) {
            int half = WindowKey.SIZE >> 1;
            long cornerX = Math.floorDiv((int) Math.floor(reach.xs()[0]) + half, WindowKey.SIZE);
            long cornerZ = Math.floorDiv((int) Math.floor(reach.zs()[0]) + half, WindowKey.SIZE);
            return (cornerX << 32) | (cornerZ & 0xffffffffL);
        }
    }
}
