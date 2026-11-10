package art.arcane.iris.core.modules;

import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.config.PackSettings;
import com.volmit.host.ModuleLogs;
import com.volmit.item.ItemRegistry;
import com.volmit.noise.NoiseFactory;
import com.volmit.object.PackObjects;
import com.volmit.placement.Placements;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The world a fit has outside any world: a start fitted with its memo needs nothing of a world but the noise its
 * terrain carving draws on, which comes from plain styles. Everything else a fit could ask of a world fails, saying why.
 */
final class FitlessWorld implements PackWorld, PackResources {
    private static final String OUTSIDE_A_WORLD = "The built-in modules fit outside a world here: a start needs its memo"
            + " and a fit without palettes";
    private static final ResourceId PACK = ResourceId.parse("iris:fitless");

    private final UUID snapshot = UUID.randomUUID();
    private final NoiseFactory noise = IrisNoiseFactory.standalone();

    @Override
    public long seed() {
        throw outsideAWorld();
    }

    @Override
    public PackResources resources() {
        return this;
    }

    @Override
    public PackSettings settings() {
        throw outsideAWorld();
    }

    @Override
    public int minWorldY() {
        throw outsideAWorld();
    }

    @Override
    public Optional<RunningWorld> running() {
        return Optional.empty();
    }

    @Override
    public <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create) {
        throw outsideAWorld();
    }

    @Override
    public Placements placements() {
        throw outsideAWorld();
    }

    @Override
    public ModuleLog log() {
        return ModuleLogs.discarding();
    }

    @Override
    public <S> List<S> services(Class<S> service) {
        throw outsideAWorld();
    }

    @Override
    public ResourceId packId() {
        return PACK;
    }

    @Override
    public UUID snapshotId() {
        return snapshot;
    }

    @Override
    public BlockRegistry blocks() {
        throw outsideAWorld();
    }

    @Override
    public ItemRegistry items() {
        throw outsideAWorld();
    }

    @Override
    public NoiseFactory noise() {
        return noise;
    }

    @Override
    public PackObjects objects() {
        throw outsideAWorld();
    }

    @Override
    public <T> Optional<T> find(ResourceKind<T> kind, ResourceId id) {
        throw outsideAWorld();
    }

    @Override
    public List<ResourceId> ids(ResourceKind<?> kind) {
        throw outsideAWorld();
    }

    private static IllegalStateException outsideAWorld() {
        return new IllegalStateException(OUTSIDE_A_WORLD);
    }
}
