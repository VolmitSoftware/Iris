package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import lombok.Getter;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Caller inputs for one placement. Modules keep derived values in PlacementState. */
@Getter
public final class PlacementContext {
    public record TransformedBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}
    public record WorldBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    final IrisObjectPlacement config;
    final IrisData rdata;
    final IObjectPlacer placer;
    final CarveResult caveResult;
    final RNG rng;
    final IrisObject self;
    final int x;
    final int z;
    final int yv;
    final BiConsumer<BlockPosition, PlatformBlockState> listener;

    public PlacementContext(IrisObjectPlacement config, IrisData rdata, IObjectPlacer placer,
                            CarveResult caveResult, RNG rng, IrisObject self, int x, int z, int yv,
                            BiConsumer<BlockPosition, PlatformBlockState> listener) {
        this.placer = Objects.requireNonNull(placer, "Object placer is required.");
        this.config = Objects.requireNonNull(config, "Object placement config is required.");
        this.rng = Objects.requireNonNull(rng, "Object placement RNG is required.");
        this.rdata = Objects.requireNonNull(rdata, "Object placement data is required.");
        this.self = Objects.requireNonNull(self, "Object is required.");
        this.caveResult = caveResult;
        this.x = x;
        this.z = z;
        this.yv = yv;
        this.listener = listener;
    }
}
