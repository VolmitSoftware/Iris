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
import art.arcane.iris.generation.hydrology.query.HydrologySearchTimeoutException;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

final class HydrologyFeatureSearch {
    private static final int MAX_WINDOWS = 1089;
    private static final Set<HydrologyFeatureType> WINDOW_TYPES = EnumSet.of(HydrologyFeatureType.STREAM, HydrologyFeatureType.RIVER,
            HydrologyFeatureType.MAJOR_RIVER, HydrologyFeatureType.CASCADE, HydrologyFeatureType.WATERFALL, HydrologyFeatureType.MOUTH,
            HydrologyFeatureType.POND, HydrologyFeatureType.DAM, HydrologyFeatureType.SEA_INLET);
    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(50L);
    static final String OFF = "hydrology is off";
    private static final String PONDS_OFF = "hydrology.ponds.natural and hydrology.ponds.seeded are disabled";

    private final HydrologySettings settings;
    private final HydrologyPlanCache cache;
    private final int originX;
    private final int originZ;
    private final Set<HydrologyFeatureType> types;
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
        this.types = enabledTypes(settings, query);
        this.eligible = eligible;
        this.radius = radius;
        this.radiusSquared = (long) radius * radius;
    }

    Optional<HydrologyFeatureRef> nearest(Duration timeout, BooleanSupplier running) throws HydrologySearchTimeoutException {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(running, "running");
        int rings = Math.ceilDiv(radius, WindowKey.SIZE) + 1;
        long windows = (2L * rings + 1L) * (2L * rings + 1L);
        if (windows > MAX_WINDOWS) {
            throw new IllegalArgumentException("Hydrology feature search radius " + radius + " covers " + windows + " windows, above "
                    + MAX_WINDOWS);
        }
        if (types.isEmpty()) {
            return Optional.empty();
        }
        Budget budget = new Budget(System.nanoTime() + timeout.toNanos(), timeout, running);
        if (types.contains(HydrologyFeatureType.LAKE)) {
            searchLakes(budget);
        }
        if (!Collections.disjoint(types, WINDOW_TYPES)) {
            searchWindows(rings, budget);
        }
        return Optional.ofNullable(best);
    }

    static Optional<String> disabledReason(HydrologySettings settings, HydrologyFeatureType type) {
        HydrologySettings.Ponds ponds = settings.ponds();
        boolean anyPonds = ponds.natural().enabled() || ponds.seeded().enabled();
        String reason = switch (type) {
            case STREAM, RIVER, MAJOR_RIVER -> hasTier(settings, type) ? null : "it has no " + type.key().replace('_', ' ') + " tier";
            case CASCADE, WATERFALL, MOUTH -> null;
            case POND -> anyPonds ? null : PONDS_OFF;
            case DAM -> !anyPonds ? PONDS_OFF : ponds.dams().enabled() ? null : "hydrology.ponds.dams is disabled";
            case LAKE -> settings.lakes().enabled() ? null : "hydrology.lakes is disabled";
            case SEA_INLET -> settings.underground().seaInlets().enabled() ? null : "hydrology.underground.seaInlets is disabled";
            case CAVE_POOL -> settings.underground().cavePools().enabled() ? null : "hydrology.underground.cavePools is disabled";
        };
        return Optional.ofNullable(reason);
    }

    private static Set<HydrologyFeatureType> enabledTypes(HydrologySettings settings, HydrologyFeatureQuery query) {
        Set<HydrologyFeatureType> enabled = EnumSet.noneOf(HydrologyFeatureType.class);
        for (HydrologyFeatureType type : query.types()) {
            if (disabledReason(settings, type).isEmpty()) {
                enabled.add(type);
            }
        }
        return enabled;
    }

    private static boolean hasTier(HydrologySettings settings, HydrologyFeatureType type) {
        List<HydrologyTier> tiers = settings.tiers();
        for (int index = 0; index < tiers.size(); index++) {
            if (channelType(tiers.get(index).name(), index, tiers.size()) == type) {
                return true;
            }
        }
        return false;
    }

    private void searchLakes(Budget budget) throws HydrologySearchTimeoutException {
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
            for (Pond lake : await(plan, budget).lakes()) {
                if (seen.add(lake.id())) {
                    pond(lake, HydrologyFeatureType.LAKE);
                }
            }
        }
    }

    private void searchWindows(int rings, Budget budget) throws HydrologySearchTimeoutException {
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
                window(await(plan, budget).plan());
            }
        }
    }

    private <T> T await(CompletableFuture<T> plan, Budget budget) throws HydrologySearchTimeoutException {
        CountDownLatch settled = new CountDownLatch(1);
        plan.whenComplete((value, failure) -> settled.countDown());
        boolean ready = false;
        while (!ready) {
            ready = settle(settled, slice(budget));
        }
        return cache.awaitPlan(plan);
    }

    private long slice(Budget budget) throws HydrologySearchTimeoutException {
        if (!budget.running().getAsBoolean()) {
            throw new CancellationException("The hydrology feature search was cancelled.");
        }
        long remaining = budget.deadline() - System.nanoTime();
        if (remaining <= 0L) {
            throw new HydrologySearchTimeoutException(budget.timeout(), best);
        }
        return Math.min(remaining, POLL_NANOS);
    }

    private static boolean settle(CountDownLatch settled, long nanos) {
        try {
            return settled.await(nanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            CancellationException cancelled = new CancellationException("The hydrology feature search was interrupted.");
            cancelled.initCause(interruption);
            throw cancelled;
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
    }

    private HydrologyFeatureType pondType(Pond pond) {
        if (pond.kind() == PondKind.LAKE) {
            return types.contains(HydrologyFeatureType.LAKE) ? HydrologyFeatureType.LAKE : null;
        }
        if (pond.damRaise() > 0 && types.contains(HydrologyFeatureType.DAM)) {
            return HydrologyFeatureType.DAM;
        }
        return types.contains(HydrologyFeatureType.POND) ? HydrologyFeatureType.POND : null;
    }

    private void reach(Reach reach) {
        if (reach.style()[0] == ReachStyle.TIDAL) {
            if (types.contains(HydrologyFeatureType.SEA_INLET)) {
                tidal(reach);
            }
            return;
        }
        HydrologyFeatureType channel = channelType(settings, reach.tier());
        boolean whole = types.contains(channel);
        float[] xs = reach.xs();
        float[] zs = reach.zs();
        for (int index = 0; index < xs.length; index++) {
            HydrologyFeatureType point = pointType(reach.style()[index]);
            HydrologyFeatureType type = point != null && types.contains(point) ? point : whole ? channel : null;
            if (type != null) {
                offer(reach.id(), type, (int) Math.floor(xs[index]), reach.grade()[index], (int) Math.floor(zs[index]));
            }
        }
    }

    private void tidal(Reach reach) {
        for (int index = 0; index < reach.xs().length; index++) {
            offer(reach.id(), HydrologyFeatureType.SEA_INLET, (int) Math.floor(reach.xs()[index]), reach.grade()[index], (int) Math.floor(reach.zs()[index]));
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
        return channelType(tier.name(), index, tiers.size());
    }

    private static HydrologyFeatureType channelType(String name, int index, int count) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "stream" -> HydrologyFeatureType.STREAM;
            case "river" -> HydrologyFeatureType.RIVER;
            case "major", "major_river" -> HydrologyFeatureType.MAJOR_RIVER;
            default -> rankType(index, count);
        };
    }

    private static HydrologyFeatureType rankType(int index, int count) {
        if (index == 0) {
            return HydrologyFeatureType.STREAM;
        }
        return index == count - 1 ? HydrologyFeatureType.MAJOR_RIVER : HydrologyFeatureType.RIVER;
    }

    private static HydrologyFeatureType pointType(ReachStyle style) {
        return switch (style) {
            case CASCADE -> HydrologyFeatureType.CASCADE;
            case WATERFALL -> HydrologyFeatureType.WATERFALL;
            case MOUTH -> HydrologyFeatureType.MOUTH;
            case RUN, RIFFLE, POND_OUTLET, TIDAL -> null;
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

    private record Budget(long deadline, Duration timeout, BooleanSupplier running) {
    }
}
