package com.volmit.iris.scaffold.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.IrisEngineCompound;
import com.volmit.iris.generator.legacy.scaffold.TerrainChunk;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.M;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class EngineCompositeGenerator extends ChunkGenerator implements IrisAccess {
    private EngineCompound compound;
    private final AtomicBoolean initialized;
    private final String dimensionHint;
    private final boolean production;
    private final KList<BlockPopulator> populators;
    private int generated = 0;

    public EngineCompositeGenerator() {
        this(null, true);
    }

    public EngineCompositeGenerator(String hint, boolean production) {
        super();
        this.production = production;
        this.dimensionHint = hint;
        initialized = new AtomicBoolean(false);
        populators = new KList<BlockPopulator>().qadd(new BlockPopulator() {
            @Override
            public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk chunk) {
                if(compound != null)
                {
                    for(BlockPopulator i : compound.getPopulators())
                    {
                        i.populate(world, random, chunk);
                    }
                }
            }
        });
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
        populators.clear();
        populators.addAll(compound.getPopulators());
    }

    private File getDataFolder(World world) {
        return new File(world.getWorldFolder(), "iris/pack");
    }

    @NotNull
    @Override
    public ChunkData generateChunkData(@NotNull World world, @NotNull Random ignored, int x, int z, @NotNull BiomeGrid biome) {
        TerrainChunk tc = TerrainChunk.create(world, biome);
        generateChunkRawData(world, ignored, x, z, tc);
        return tc.getRaw();
    }

    public void generateChunkRawData(World world, Random ignored, int x, int z, TerrainChunk tc)
    {
        initialize(world);
        Hunk<BlockData> blocks = Hunk.view((ChunkData) tc);
        Hunk<Biome> biomes = Hunk.view((BiomeGrid) tc);
        long m = M.ms();
        compound.generate(x * 16, z * 16, blocks, biomes);
        System.out.println("Generated " + x + "," + z + " in " + Form.duration(M.ms() - m, 0));
        generated++;
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return super.canSpawn(world, x, z);
    }

    @NotNull
    @Override
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return populators;
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

    @Override
    public IrisBiome getBiome(int x, int z) {
        return getBiome(x, 0, z);
    }

    @Override
    public IrisBiome getCaveBiome(int x, int z) {
        return getCaveBiome(x, 0, z);
    }

    @Override
    public int getGenerated() {
        return generated;
    }

    @Override
    public IrisBiome getBiome(int x, int y, int z) {
        // TODO: REMOVE GET ABS BIOME OR THIS ONE
        return getEngineAccess(y).getBiome(x, y-getComposite().getEngineForHeight(y).getMinHeight(), z);
    }

    @Override
    public IrisBiome getCaveBiome(int x, int y, int z) {
        return getEngineAccess(y).getCaveBiome(x, z);
    }

    @Override
    public GeneratorAccess getEngineAccess(int y) {
        return getComposite().getEngineForHeight(y);
    }

    @Override
    public IrisDataManager getData() {
        return getComposite().getData();
    }

    @Override
    public int getHeight(int x, int y, int z) {
        return getEngineAccess(y).getHeight(x, z);
    }

    @Override
    public IrisBiome getAbsoluteBiome(int x, int y, int z) {
        // TODO: REMOVE GET BIOME OR THIS ONE
        return getEngineAccess(y).getBiome(x, y-getComposite().getEngineForHeight(y).getMinHeight(), z);
    }

    @Override
    public int getThreadCount() {
        // TODO: NOT CORRECT
        return Iris.getThreadCount();
    }

    @Override
    public void changeThreadCount(int m) {
        // TODO: DO IT
    }

    @Override
    public void regenerate(int x, int z) {
        // TODO: DO IT
    }

    @Override
    public void close() {
        getComposite().close();
    }

    @Override
    public boolean isClosed() {
        return getComposite().getEngine(0).isClosed();
    }

    @Override
    public EngineTarget getTarget() {
        return getComposite().getEngine(0).getTarget();
    }

    @Override
    public EngineCompound getCompound() {
        return getComposite();
    }
}