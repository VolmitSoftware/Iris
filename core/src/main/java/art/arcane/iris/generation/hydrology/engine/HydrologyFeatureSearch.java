package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCache;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

final class HydrologyFeatureSearch {
    private static final int MAX_WINDOWS = 1089;
    private static final Set<HydrologyFeatureType> WINDOW_TYPES = EnumSet.of(HydrologyFeatureType.STREAM, HydrologyFeatureType.RIVER,
            HydrologyFeatureType.MAJOR_RIVER, HydrologyFeatureType.CASCADE, HydrologyFeatureType.WATERFALL, HydrologyFeatureType.MOUTH,
            HydrologyFeatureType.POND, HydrologyFeatureType.DAM, HydrologyFeatureType.SEA_INLET);

    private final HydrologySettings settings;
    private final HydrologyPlanCache cache;
    private final int originX;
    private final int originZ;
    private final HydrologyFeatureQuery query;
    private final Predicate<HydrologyFeatureRef> eligible;
    private final int radius;
    private final long radiusSquared;
    private HydrologyFeatureRef best;
    private long bestSquared = Long.MAX_VALUE;

    HydrologyFeatureSearch(HydrologySettings settings, HydrologyPlanCache cache, int originX, int originZ, HydrologyFeatureQuery query,
                           Predicate<HydrologyFeatureRef> eligible, int radius) {
        this.settings = settings;
        this.cache = cache;
        this.originX = originX;
        this.originZ = originZ;
        this.query = query;
        this.eligible = eligible;
        this.radius = radius;
        this.radiusSquared = (long) radius * radius;
    }

    Optional<HydrologyFeatureRef> nearest() {
        int rings = Math.ceilDiv(radius, WindowKey.SIZE) + 1;
        long windows = (2L * rings + 1L) * (2L * rings + 1L);
        if (windows > MAX_WINDOWS) {
            throw new IllegalArgumentException("Hydrology feature search radius " + radius + " covers " + windows + " windows, above "
                    + MAX_WINDOWS);
        }
        if (query.matches(HydrologyFeatureType.LAKE)) {
            searchLakes();
        }
        if (!Collections.disjoint(query.types(), WINDOW_TYPES)) {
            searchWindows(rings);
        }
        return Optional.ofNullable(best);
    }

    private void searchLakes() {
        long minX = (long) originX - radius;
        long minZ = (long) originZ - radius;
        long maxX = (long) originX + radius;
        long maxZ = (long) originZ + radius;
        Set<Long> seen = new HashSet<>();
        List<RegionKey> keys = RegionKey.lakeOwners(Math.toIntExact(minX), Math.toIntExact(minZ), Math.toIntExact(maxX), Math.toIntExact(maxZ));
        List<CompletableFuture<RegionPlan>> plans = new ArrayList<>(keys.size());
        for (RegionKey key : keys) {
            plans.add(cache.demandRegion(key));
        }
        for (CompletableFuture<RegionPlan> plan : plans) {
            for (Pond lake : cache.awaitPlan(plan).lakes()) {
                if (seen.add(lake.id())) {
                    pond(lake, HydrologyFeatureType.LAKE);
                }
            }
        }
    }

    private void searchWindows(int rings) {
        WindowKey origin = WindowKey.containing(originX, originZ);
        for (int ring = 0; ring <= rings; ring++) {
            long bound = Math.max(0L, (long) (ring - 2) * WindowKey.SIZE);
            if (best != null && bestSquared < bound * bound) {
                return;
            }
            List<CompletableFuture<IndexedWindow>> plans = new ArrayList<>();
            for (WindowKey key : ring(origin, ring)) {
                plans.add(cache.demandWindow(key));
            }
            for (CompletableFuture<IndexedWindow> plan : plans) {
                window(cache.awaitPlan(plan).plan());
            }
        }
    }

    private static List<WindowKey> ring(WindowKey origin, int ring) {
        if (ring == 0) {
            return List.of(origin);
        }
        List<WindowKey> keys = new ArrayList<>(8 * ring);
        for (int dz = -ring; dz <= ring; dz++) {
            for (int dx = -ring; dx <= ring; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                    keys.add(new WindowKey(origin.wx() + dx, origin.wz() + dz));
                }
            }
        }
        return keys;
    }

    private void window(WindowPlan plan) {
        for (Reach reach : plan.reaches()) {
            reach(reach);
        }
        for (Pond pond : plan.ponds()) {
            HydrologyFeatureType type = pondType(pond);
            if (type != null) {
                pond(pond, type);
            }
        }
        if (query.matches(HydrologyFeatureType.SEA_INLET)) {
            for (SeaInlet inlet : plan.inlets()) {
                inlet(inlet);
            }
        }
    }

    private void inlet(SeaInlet inlet) {
        for (int station = 0; station < inlet.stations(); station++) {
            offer(inlet.id(), HydrologyFeatureType.SEA_INLET, (int) Math.floor(inlet.xs()[station]), inlet.waterLevel(),
                    (int) Math.floor(inlet.zs()[station]));
        }
    }

    private HydrologyFeatureType pondType(Pond pond) {
        if (pond.kind() == PondKind.LAKE) {
            return query.matches(HydrologyFeatureType.LAKE) ? HydrologyFeatureType.LAKE : null;
        }
        if (pond.damRaise() > 0 && query.matches(HydrologyFeatureType.DAM)) {
            return HydrologyFeatureType.DAM;
        }
        return query.matches(HydrologyFeatureType.POND) ? HydrologyFeatureType.POND : null;
    }

    private void reach(Reach reach) {
        HydrologyFeatureType channel = channelType(settings, reach.tier());
        boolean whole = query.matches(channel);
        float[] xs = reach.xs();
        float[] zs = reach.zs();
        for (int index = 0; index < xs.length; index++) {
            HydrologyFeatureType point = pointType(reach.style()[index]);
            HydrologyFeatureType type = point != null && query.matches(point) ? point : whole ? channel : null;
            if (type != null) {
                offer(reach.id(), type, (int) Math.floor(xs[index]), reach.grade()[index], (int) Math.floor(zs[index]));
            }
        }
    }

    private void pond(Pond pond, HydrologyFeatureType type) {
        PondMask mask = pond.mask();
        for (int j = 0; j < mask.depth(); j++) {
            for (int i = 0; i < mask.width(); i++) {
                if (mask.node(i, j)) {
                    offer(pond.id(), type, mask.originX() + i * mask.step(), pond.level(), mask.originZ() + j * mask.step());
                }
            }
        }
    }

    static Set<HydrologyFeatureType> standingTypes(Pond pond) {
        if (pond.kind() == PondKind.LAKE) {
            return EnumSet.of(HydrologyFeatureType.LAKE);
        }
        return pond.damRaise() > 0 ? EnumSet.of(HydrologyFeatureType.POND, HydrologyFeatureType.DAM) : EnumSet.of(HydrologyFeatureType.POND);
    }

    static HydrologyFeatureType channelType(HydrologySettings settings, HydrologyTier tier) {
        List<HydrologyTier> tiers = settings.tiers();
        int index = tiers.indexOf(tier);
        if (index < 0) {
            throw new IllegalStateException("Reach tier '" + tier.name() + "' is not one of the hydrology tiers");
        }
        if (tier.equals(settings.majorTier())) {
            return HydrologyFeatureType.MAJOR_RIVER;
        }
        return index == 0 ? HydrologyFeatureType.STREAM : HydrologyFeatureType.RIVER;
    }

    private static HydrologyFeatureType pointType(ReachStyle style) {
        return switch (style) {
            case CASCADE -> HydrologyFeatureType.CASCADE;
            case WATERFALL -> HydrologyFeatureType.WATERFALL;
            case MOUTH -> HydrologyFeatureType.MOUTH;
            case RUN, RIFFLE, POND_OUTLET -> null;
        };
    }

    private void offer(long id, HydrologyFeatureType type, int x, int y, int z) {
        long dx = (long) x - originX;
        long dz = (long) z - originZ;
        long squared = dx * dx + dz * dz;
        if (squared > radiusSquared || squared > bestSquared) {
            return;
        }
        if (squared == bestSquared && !before(id, type, x, z)) {
            return;
        }
        HydrologyFeatureRef candidate = new HydrologyFeatureRef(id, type, x, y, z);
        if (!eligible.test(candidate)) {
            return;
        }
        best = candidate;
        bestSquared = squared;
    }

    private boolean before(long id, HydrologyFeatureType type, int x, int z) {
        if (id != best.id()) {
            return id < best.id();
        }
        if (type != best.type()) {
            return type.ordinal() < best.type().ordinal();
        }
        return x != best.x() ? x < best.x() : z < best.z();
    }
}
