package art.arcane.iris.integration;

import art.arcane.iris.api.pregen.IrisPregenPhase;
import art.arcane.iris.api.pregen.IrisPregenProgress;
import art.arcane.iris.api.terrain.IrisTerrainService;
import art.arcane.iris.api.terrain.IrisWorldInfo;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderSnapshot;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class IrisPapiState {
    static final long VIEW_TTL_MS = 1_000L;
    static final long POSITION_INTERVAL_MS = 1_000L;

    private final Supplier<IrisTerrainService> terrain;
    private final LongSupplier clock;
    private final PlayerSnapshotStore<IrisPapiPosition> positions = new PlayerSnapshotStore<>();
    private final PlayerSnapshotStore<IrisPapiWorldView> views = new PlayerSnapshotStore<>();
    private final PlaceholderSnapshot<IrisPapiPregenView> pregen = new PlaceholderSnapshot<>();

    public IrisPapiState(Supplier<IrisTerrainService> terrain) {
        this(terrain, System::currentTimeMillis);
    }

    public IrisPapiState(Supplier<IrisTerrainService> terrain, LongSupplier clock) {
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void trackPosition(UUID playerId, World world, int blockX, int blockZ) {
        publishPosition(playerId, world, blockX, blockZ, false);
    }

    public void trackPositionNow(UUID playerId, World world, int blockX, int blockZ) {
        publishPosition(playerId, world, blockX, blockZ, true);
    }

    private void publishPosition(UUID playerId, World world, int blockX, int blockZ, boolean immediate) {
        if (playerId == null || world == null) {
            return;
        }

        IrisPapiPosition current = positions.get(playerId);

        if (current != null && current.sameColumn(world, blockX, blockZ)) {
            return;
        }

        long now = clock.getAsLong();

        if (!immediate && current != null && current.world() == world
                && now - current.publishedAtMs() < POSITION_INTERVAL_MS) {
            return;
        }

        positions.publish(playerId, new IrisPapiPosition(world, blockX, blockZ, now));
    }

    public void release(UUID playerId) {
        positions.publish(playerId, null);
        views.publish(playerId, null);
    }

    public void publishPregen(IrisPregenPhase phase, IrisPregenProgress progress) {
        if (phase == null || progress == null) {
            return;
        }

        pregen.publish(switch (phase) {
            case STARTED, TICK, PAUSED, RESUMED, SAVING -> IrisPapiPregenView.of(progress);
            case COMPLETED, CANCELLED -> null;
        });
    }

    public void clear() {
        positions.clear();
        views.clear();
        pregen.publish(null);
    }

    public String available(UUID playerId) {
        return PlaceholderValues.bool(terrain.get() != null);
    }

    public String worldAvailable(UUID playerId) {
        IrisPapiWorldView view = viewOf(playerId);
        return view == null ? PlaceholderValues.FALSE : view.available();
    }

    public String biome(UUID playerId) {
        IrisPapiWorldView view = viewOf(playerId);
        return view == null ? PlaceholderValues.UNAVAILABLE : view.biome();
    }

    public String biomeKey(UUID playerId) {
        IrisPapiWorldView view = viewOf(playerId);
        return view == null ? PlaceholderValues.UNAVAILABLE : view.biomeKey();
    }

    public String region(UUID playerId) {
        IrisPapiWorldView view = viewOf(playerId);
        return view == null ? PlaceholderValues.UNAVAILABLE : view.region();
    }

    public String regionKey(UUID playerId) {
        IrisPapiWorldView view = viewOf(playerId);
        return view == null ? PlaceholderValues.UNAVAILABLE : view.regionKey();
    }

    public String dimension(UUID playerId) {
        IrisPapiWorldView view = viewOf(playerId);
        return view == null ? PlaceholderValues.UNAVAILABLE : view.dimension();
    }

    public String pregenAvailable(UUID playerId) {
        return pregen.available();
    }

    public String pregenWorld(UUID playerId) {
        IrisPapiPregenView view = pregen.get();
        return view == null ? PlaceholderValues.UNAVAILABLE : view.world();
    }

    public String pregenPercent(UUID playerId) {
        IrisPapiPregenView view = pregen.get();
        return view == null ? PlaceholderValues.UNAVAILABLE : view.percent();
    }

    public String pregenEta(UUID playerId) {
        IrisPapiPregenView view = pregen.get();
        return view == null ? PlaceholderValues.UNAVAILABLE : view.eta();
    }

    public String pregenEtaText(UUID playerId) {
        IrisPapiPregenView view = pregen.get();
        return view == null ? PlaceholderValues.UNAVAILABLE : view.etaText();
    }

    public String pregenChunks(UUID playerId) {
        IrisPapiPregenView view = pregen.get();
        return view == null ? PlaceholderValues.UNAVAILABLE : view.chunks();
    }

    public String pregenTotal(UUID playerId) {
        IrisPapiPregenView view = pregen.get();
        return view == null ? PlaceholderValues.UNAVAILABLE : view.total();
    }

    public String pregenChunksPerSecond(UUID playerId) {
        IrisPapiPregenView view = pregen.get();
        return view == null ? PlaceholderValues.UNAVAILABLE : view.chunksPerSecond();
    }

    public String pregenPaused(UUID playerId) {
        IrisPapiPregenView view = pregen.get();
        return view == null ? PlaceholderValues.UNAVAILABLE : view.paused();
    }

    private IrisPapiWorldView viewOf(UUID playerId) {
        IrisPapiPosition position = positions.get(playerId);

        if (position == null) {
            return null;
        }

        IrisPapiWorldView cached = views.get(playerId);
        long now = clock.getAsLong();

        if (cached != null && cached.position() == position && now - cached.builtAtMs() < VIEW_TTL_MS) {
            return cached;
        }

        IrisPapiWorldView built = build(position, now);
        views.publish(playerId, built);
        return built;
    }

    private IrisPapiWorldView build(IrisPapiPosition position, long now) {
        IrisTerrainService service = terrain.get();
        World world = position.world();

        if (service == null || !service.isIrisWorld(world)) {
            return IrisPapiWorldView.absent(position, now);
        }

        int blockX = position.blockX();
        int blockZ = position.blockZ();

        return IrisPapiWorldView.present(
                position,
                now,
                service.surfaceBiomeName(world, blockX, blockZ),
                service.surfaceBiomeKey(world, blockX, blockZ),
                service.regionName(world, blockX, blockZ),
                service.regionKey(world, blockX, blockZ),
                service.worldInfo(world).map(IrisWorldInfo::dimensionKey));
    }
}
