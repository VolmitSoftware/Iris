package art.arcane.iris.api.terrain;

import org.bukkit.World;

import java.util.Optional;
import java.util.OptionalInt;

public interface IrisTerrainService {
    boolean isIrisWorld(World world);

    Optional<IrisWorldInfo> worldInfo(World world);

    OptionalInt surfaceHeight(World world, int blockX, int blockZ);

    IrisSurfaceKind surfaceKind(World world, int blockX, int blockZ);

    Optional<String> surfaceBiomeKey(World world, int blockX, int blockZ);

    Optional<String> surfaceBiomeName(World world, int blockX, int blockZ);

    Optional<IrisBiomeInfo> surfaceBiomeInfo(World world, int blockX, int blockZ);

    Optional<String> biomeKey(World world, int blockX, int blockY, int blockZ);

    Optional<String> regionKey(World world, int blockX, int blockZ);

    Optional<String> regionName(World world, int blockX, int blockZ);

    int maxSampleColumns();

    int maxSampleChunks();

    boolean sampleColumns(World world, IrisColumnQuery query, IrisColumnSink sink);
}
