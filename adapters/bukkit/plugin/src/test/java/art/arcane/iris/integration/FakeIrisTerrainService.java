package art.arcane.iris.integration;

import art.arcane.iris.api.terrain.IrisColumnQuery;
import art.arcane.iris.api.terrain.IrisColumnSink;
import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.api.terrain.IrisTerrainService;
import art.arcane.iris.api.terrain.IrisWorldInfo;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

final class FakeIrisTerrainService implements IrisTerrainService {
    private final Set<World> irisWorlds = new HashSet<>();

    private String dimensionKey = "overworld";
    private String biomeName = "Hot Desert Dunes";
    private String biomeKey = "desert/hot-dunes";
    private String regionName = "Scorched Expanse";
    private String regionKey = "scorched";
    private int builds;
    private int lastBlockX;
    private int lastBlockZ;

    void addIrisWorld(World world) {
        irisWorlds.add(world);
    }

    void describe(String dimensionKey, String biomeName, String biomeKey, String regionName, String regionKey) {
        this.dimensionKey = dimensionKey;
        this.biomeName = biomeName;
        this.biomeKey = biomeKey;
        this.regionName = regionName;
        this.regionKey = regionKey;
    }

    int builds() {
        return builds;
    }

    String sampledColumn() {
        return lastBlockX + "," + lastBlockZ;
    }

    @Override
    public boolean isIrisWorld(World world) {
        return irisWorlds.contains(world);
    }

    @Override
    public Optional<IrisWorldInfo> worldInfo(World world) {
        if (!isIrisWorld(world)) {
            return Optional.empty();
        }

        return Optional.of(new IrisWorldInfo(dimensionKey, "identity", 42L, -64, 320, 63, false));
    }

    @Override
    public OptionalInt surfaceHeight(World world, int blockX, int blockZ) {
        throw new AssertionError("a placeholder must never sample terrain height");
    }

    @Override
    public IrisSurfaceKind surfaceKind(World world, int blockX, int blockZ) {
        throw new AssertionError("a placeholder must never classify the surface");
    }

    @Override
    public Optional<String> surfaceBiomeKey(World world, int blockX, int blockZ) {
        return isIrisWorld(world) ? Optional.ofNullable(biomeKey) : Optional.empty();
    }

    @Override
    public Optional<String> surfaceBiomeName(World world, int blockX, int blockZ) {
        if (!isIrisWorld(world)) {
            return Optional.empty();
        }

        builds++;
        lastBlockX = blockX;
        lastBlockZ = blockZ;
        return Optional.ofNullable(biomeName);
    }

    @Override
    public Optional<String> biomeKey(World world, int blockX, int blockY, int blockZ) {
        throw new AssertionError("a placeholder must never resolve a three dimensional biome");
    }

    @Override
    public Optional<String> regionKey(World world, int blockX, int blockZ) {
        return isIrisWorld(world) ? Optional.ofNullable(regionKey) : Optional.empty();
    }

    @Override
    public Optional<String> regionName(World world, int blockX, int blockZ) {
        return isIrisWorld(world) ? Optional.ofNullable(regionName) : Optional.empty();
    }

    @Override
    public int maxSampleColumns() {
        return 0;
    }

    @Override
    public int maxSampleChunks() {
        return 0;
    }

    @Override
    public boolean sampleColumns(World world, IrisColumnQuery query, IrisColumnSink sink) {
        throw new AssertionError("a placeholder must never run a column sample");
    }
}
