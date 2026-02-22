package art.arcane.iris.util.project.context;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.util.common.parallel.MultiBurst;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ChunkContext {
    private final int x;
    private final int z;
    private final ChunkedDataCache<Double> height;
    private final ChunkedDataCache<IrisBiome> biome;
    private final ChunkedDataCache<IrisBiome> cave;
    private final ChunkedDataCache<BlockData> rock;
    private final ChunkedDataCache<BlockData> fluid;
    private final ChunkedDataCache<IrisRegion> region;

    public ChunkContext(int x, int z, IrisComplex complex) {
        this(x, z, complex, true);
    }

    public ChunkContext(int x, int z, IrisComplex complex, boolean cache) {
        this.x = x;
        this.z = z;
        this.height = new ChunkedDataCache<>(complex.getHeightStream(), x, z, cache);
        this.biome = new ChunkedDataCache<>(complex.getTrueBiomeStream(), x, z, cache);
        this.cave = new ChunkedDataCache<>(complex.getCaveBiomeStream(), x, z, cache);
        this.rock = new ChunkedDataCache<>(complex.getRockStream(), x, z, cache);
        this.fluid = new ChunkedDataCache<>(complex.getFluidStream(), x, z, cache);
        this.region = new ChunkedDataCache<>(complex.getRegionStream(), x, z, cache);

        if (cache) {
            Executor executor = MultiBurst.burst;
            List<CompletableFuture<Void>> tasks = new ArrayList<>(6);
            tasks.add(CompletableFuture.runAsync(() -> height.fill(executor), executor));
            tasks.add(CompletableFuture.runAsync(() -> biome.fill(executor), executor));
            tasks.add(CompletableFuture.runAsync(() -> cave.fill(executor), executor));
            tasks.add(CompletableFuture.runAsync(() -> rock.fill(executor), executor));
            tasks.add(CompletableFuture.runAsync(() -> fluid.fill(executor), executor));
            tasks.add(CompletableFuture.runAsync(() -> region.fill(executor), executor));
            for (CompletableFuture<Void> task : tasks) {
                task.join();
            }
        }
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public ChunkedDataCache<Double> getHeight() {
        return height;
    }

    public ChunkedDataCache<IrisBiome> getBiome() {
        return biome;
    }

    public ChunkedDataCache<IrisBiome> getCave() {
        return cave;
    }

    public ChunkedDataCache<BlockData> getRock() {
        return rock;
    }

    public ChunkedDataCache<BlockData> getFluid() {
        return fluid;
    }

    public ChunkedDataCache<IrisRegion> getRegion() {
        return region;
    }
}
