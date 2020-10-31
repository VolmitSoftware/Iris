package com.volmit.iris.v2.scaffold.engine;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.M;
import com.volmit.iris.v2.generator.IrisEngineCompound;
import com.volmit.iris.v2.scaffold.hunk.Hunk;

public class EngineCompositeGenerator extends ChunkGenerator implements Hotloadable {
    private EngineCompound compound;
    private final AtomicBoolean initialized;
    private final String dimensionHint;
    private final boolean production;

    public EngineCompositeGenerator() {
        this(null, true);
    }

    public EngineCompositeGenerator(String hint, boolean production) {
        super();
        this.production = production;
        this.dimensionHint = hint;
        initialized = new AtomicBoolean(false);
    }

    public void hotload()
    {
        Iris.globaldata.dump();
        initialized.lazySet(false);
    }

    private synchronized IrisDimension getDimension(World world) {
        String hint = dimensionHint;
        IrisDimension dim = null;

        if (hint == null) {
            File iris = new File(world.getWorldFolder(), "iris");

            searching:
            for (File i : iris.listFiles()) {
                // Look for v1 location
                if (i.isDirectory() && i.getName().equals("dimensions")) {
                    for (File j : i.listFiles()) {
                        if (j.isFile() && j.getName().endsWith(".json")) {
                            hint = j.getName().replaceAll("\\Q.json\\E", "");
                            break searching;
                        }
                    }
                }

                // Look for v2 location
                else if (i.isFile() && i.getName().equals("engine-metadata.json")) {
                    EngineData metadata = EngineData.load(i);
                    hint = metadata.getDimension();
                    break;
                }
            }
        }

        if (hint == null) {
            throw new RuntimeException("Cannot find iris dimension data for world: " + world.getName() + "! FAILED");
        }

        dim = Iris.globaldata.preferFolder(hint).getDimensionLoader().load(hint);

        if (dim == null) {
            throw new RuntimeException("Cannot find dimension: " + hint);
        }

        if (production) {
            dim = new IrisDataManager(getDataFolder(world), true).preferFolder(dim.getLoadKey()).getDimensionLoader().load(dim.getLoadKey());

            if (dim == null) {
                throw new RuntimeException("Cannot find dimension: " + hint);
            }
        }

        return dim;
    }

    private synchronized void initialize(World world) {
        if (initialized.get()) {
            return;
        }

        IrisDataManager data = production ? new IrisDataManager(getDataFolder(world)) : Iris.globaldata.copy();
        IrisDimension dim = getDimension(world);
        data.preferFolder(dim.getLoadKey());
        compound = new IrisEngineCompound(world, dim, data, Iris.getThreadCount());
        initialized.set(true);
    }

    private File getDataFolder(World world) {
        return new File(world.getWorldFolder(), "iris/pack");
    }

    @NotNull
    @Override
    public ChunkData generateChunkData(@NotNull World world, @NotNull Random ignored, int x, int z, @NotNull BiomeGrid biome) {
        initialize(world);
        ChunkData chunk = createChunkData(world);
        Hunk<BlockData> blocks = Hunk.view(chunk);
        Hunk<Biome> biomes = Hunk.view(biome);
        long m = M.ms();
        compound.generate(x * 16, z * 16, blocks, biomes);
        System.out.println("Generated " + x + "," + z + " in " + Form.duration(M.ms() - m, 0));
        return chunk;
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return super.canSpawn(world, x, z);
    }

    @NotNull
    @Override
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return super.getDefaultPopulators(world);
    }

    @Nullable
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return super.getFixedSpawnLocation(world, random);
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    public static EngineCompositeGenerator newStudioWorld(String dimension) {
        return new EngineCompositeGenerator(dimension, false);
    }

    public static EngineCompositeGenerator newProductionWorld(String dimension) {
        return new EngineCompositeGenerator(dimension, true);
    }

    public static EngineCompositeGenerator newProductionWorld() {
        return new EngineCompositeGenerator(null, true);
    }

    public EngineCompound getComposite() {
        return compound;
    }
}