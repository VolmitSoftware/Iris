package com.volmit.iris.scaffold.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.generator.IrisEngineCompound;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.*;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;
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
    private long mst = 0;
    private int generated = 0;
    private int lgenerated = 0;
    private final KMap<Long, PregeneratedData> chunkCache;
    private ChronoLatch hotloadcd;
    @Getter
    private double generatedPerSecond = 0;
    private int art;
    private ReactiveFolder hotloader = null;

    public EngineCompositeGenerator() {
        this(null, true);
    }

    public EngineCompositeGenerator(String hint, boolean production) {
        super();
        chunkCache = new KMap<>();
        hotloadcd = new ChronoLatch(3500);
        mst = M.ms();
        this.production = production;
        this.dimensionHint = hint;
        initialized = new AtomicBoolean(false);
        art = J.ar(this::tick, 100);
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
        if(isStudio())
        {
            Iris.proj.updateWorkspace();
            getData().dump();
            J.s(() -> {
                try
                {
                    for(Player i : getTarget().getWorld().getPlayers())
                    {
                        new MortarSender(i, Iris.instance.getTag()).sendMessage("Dimension Hotloaded");
                        i.playSound(i.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1.25f);
                    }
                }

                catch(Throwable e)
                {

                }
            });
            initialized.lazySet(false);
        }
    }

    public void tick()
    {
        if(isClosed())
        {
            return;
        }

        if(!initialized.get())
        {
            return;
        }

        try
        {
            J.a(() -> hotloader.check());
            getComposite().clean();
        }

        catch(Throwable e)
        {

        }

        if(M.ms() - mst > 1000)
        {
            generatedPerSecond = (double)(generated - lgenerated) / ((double)(M.ms() - mst) / 1000D);
            mst = M.ms();
            lgenerated = generated;
        }
    }

    private synchronized IrisDimension getDimension(World world) {
        String hint = dimensionHint;
        IrisDimension dim = null;

        if (hint == null) {
            File iris = new File(world.getWorldFolder(), "iris");

            if(iris.exists() && iris.isDirectory())
            {
                searching:
                for (File i : iris.listFiles()) {
                    // Look for v1 location
                    if (i.isDirectory() && i.getName().equals("dimensions")) {
                        for (File j : i.listFiles()) {
                            if (j.isFile() && j.getName().endsWith(".json")) {
                                hint = j.getName().replaceAll("\\Q.json\\E", "");
                                Iris.error("Found v1 install. Please create a new world, this will cause chunks to change in your existing iris worlds!");
                                throw new RuntimeException();
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
        }

        if (hint == null) {
            Iris.error("Cannot find iris dimension data for world: " + world.getName() + "! Assuming " + IrisSettings.get().getDefaultWorldType() + "!");
            hint = IrisSettings.get().getDefaultWorldType();
        }

        dim = IrisDataManager.loadAnyDimension(hint);

        if (dim == null) {
            Iris.proj.downloadSearch(new MortarSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), hint, false);
            dim = IrisDataManager.loadAnyDimension(hint);

            if(dim == null)
            {
                throw new RuntimeException("Cannot find dimension: " + hint);
            }

            else
            {
                Iris.info("Download pack: " + hint);
            }
        }

        if (production) {
            IrisDimension od = dim;
            dim = new IrisDataManager(getDataFolder(world)).getDimensionLoader().load(od.getLoadKey());

            if (dim == null) {
                Iris.info("Installing Iris pack " + od.getName() + " into world " + world.getName() + "...");
                Iris.proj.installIntoWorld(new MortarSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), od.getLoadKey(), world.getWorldFolder());
                dim = new IrisDataManager(getDataFolder(world)).getDimensionLoader().load(od.getLoadKey());

                if(dim == null)
                {
                    throw new RuntimeException("Cannot find dimension: " + hint);
                }
            }
        }

        Iris.info(world.getName() + " is configured to generate " + dim.getName() + "!");

        return dim;
    }

    private synchronized IrisDimension getDimension(String world) {
        String hint = dimensionHint;
        IrisDimension dim = null;

        if (hint == null) {
            File iris = new File(world +"/iris");

            if(iris.exists() && iris.isDirectory())
            {
                searching:
                for (File i : iris.listFiles()) {
                    // Look for v1 location
                    if (i.isDirectory() && i.getName().equals("dimensions")) {
                        for (File j : i.listFiles()) {
                            if (j.isFile() && j.getName().endsWith(".json")) {
                                hint = j.getName().replaceAll("\\Q.json\\E", "");
                                Iris.error("Found v1 install. Please create a new world, this will cause chunks to change in your existing iris worlds!");
                                throw new RuntimeException();
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
        }

        if (hint == null) {
            Iris.error("Cannot find iris dimension data for world: " + world + "! Assuming " + IrisSettings.get().getDefaultWorldType() + "!");
            hint = IrisSettings.get().getDefaultWorldType();
        }

        dim = IrisDataManager.loadAnyDimension(hint);

        if (dim == null) {
            Iris.proj.downloadSearch(new MortarSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), hint, false);
            dim = IrisDataManager.loadAnyDimension(hint);

            if(dim == null)
            {
                throw new RuntimeException("Cannot find dimension: " + hint);
            }

            else
            {
                Iris.info("Download pack: " + hint);
            }
        }

        if (production) {
            IrisDimension od = dim;
            dim = new IrisDataManager(getDataFolder(world)).getDimensionLoader().load(od.getLoadKey());

            if (dim == null) {
                Iris.info("Installing Iris pack " + od.getName() + " into world " + world + "...");
                Iris.proj.installIntoWorld(new MortarSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), od.getLoadKey(), new File(world));
                dim = new IrisDataManager(getDataFolder(world)).getDimensionLoader().load(od.getLoadKey());

                if(dim == null)
                {
                    throw new RuntimeException("Cannot find dimension: " + hint);
                }
            }
        }

        Iris.info(world + " is configured to generate " + dim.getName() + "!");

        return dim;
    }

    private synchronized void initialize(World world) {
        if (initialized.get()) {
            return;
        }

        IrisDimension dim = getDimension(world);
        IrisDataManager data = production ? new IrisDataManager(getDataFolder(world)) : dim.getLoader().copy();
        compound = new IrisEngineCompound(world, dim, data, Iris.getThreadCount());
        compound.setStudio(!production);
        initialized.set(true);
        populators.clear();
        populators.addAll(compound.getPopulators());
        hotloader = new ReactiveFolder(data.getDataFolder(), (a, c, d) -> hotload());
    }

    private File getDataFolder(World world) {
        return new File(world.getWorldFolder(), "iris/pack");
    }

    private File getDataFolder(String world) {
        return new File(world + "/iris/pack");
    }

    @NotNull
    @Override
    public ChunkData generateChunkData(@NotNull World world, @NotNull Random ignored, int x, int z, @NotNull BiomeGrid biome) {
        TerrainChunk tc = TerrainChunk.create(world, biome);
        generateChunkRawData(world, x, z, tc).run();
        return tc.getRaw();
    }

    public Chunk generatePaper(World world, int x, int z)
    {
        precache(world, x, z);
        Chunk c = PaperLib.getChunkAtAsync(world, x, z, true).join();
        chunkCache.remove(Cache.key(x, z));
        return c;
    }

    public void precache(World world, int x, int z)
    {
        synchronized (this)
        {
            initialize(world);
        }

        synchronized (chunkCache)
        {
            if(chunkCache.containsKey(Cache.key(x, z)))
            {
                return;
            }
        }

        PregeneratedData data = new PregeneratedData(getComposite().getHeight()-1);
        compound.generate(x * 16, z * 16, data.getBlocks(), data.getPost(), data.getBiomes());
        synchronized (chunkCache)
        {
            chunkCache.put(Cache.key(x, z), data);
        }
    }

    @Override
    public int getPrecacheSize() {
        return chunkCache.size();
    }

    public int getCachedChunks()
    {
        return chunkCache.size();
    }

    public Runnable generateChunkRawData(World world, int x, int z, TerrainChunk tc)
    {
        initialize(world);

        synchronized (chunkCache)
        {
            long g = Cache.key(x, z);
            if(chunkCache.containsKey(g))
            {
                generated++;
                return chunkCache.remove(g).inject(tc);
            }
        }

        Hunk<BlockData> blocks = Hunk.view((ChunkData) tc);
        Hunk<Biome> biomes = Hunk.view((BiomeGrid) tc);
        AtomicBoolean postMod = new AtomicBoolean(false);
        Hunk<BlockData> trk = Hunk.newAtomicHunk(biomes.getWidth(), biomes.getHeight(), biomes.getDepth());
        Hunk<BlockData> post = trk.trackWrite(postMod);
        compound.generate(x * 16, z * 16, blocks, post, biomes);
        generated++;

        return postMod.get() ? () -> blocks.insertSoftly(0,0,0, post, (b) -> b == null || B.isAirOrFluid(b)) : () -> {};
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
        try
        {
            return getComposite().getDefaultEngine().getDimension().isVanillaStructures();
        }

        catch(Throwable e)
        {
            return false;
        }
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
    public void printMetrics(CommandSender sender) {
        getComposite().printMetrics(sender);
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
    public int getThreadCount() {
        return getComposite().getThreadCount();
    }

    @Override
    public void changeThreadCount(int m) {
        // TODO: DO IT
    }

    @Override
    public void clearRegeneratedLists(int x, int z) {
        for(int i = 0; i < getComposite().getSize(); i++)
        {
            getComposite().getEngine(i).getParallax().delete(x, z);
        }
    }

    @Override
    public void regenerate(int x, int z) {
        clearRegeneratedLists(x, z);
        int xx = x*16;
        int zz = z*16;
        generateChunkRawData(getComposite().getWorld(), x, z, new TerrainChunk() {
            @Override
            public void setRaw(ChunkData data) {

            }

            @Override
            public Biome getBiome(int x, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public Biome getBiome(int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public void setBiome(int x, int z, Biome bio) {

            }

            @Override
            public void setBiome(int x, int y, int z, Biome bio) {

            }

            @Override
            public int getMaxHeight() {
                return 256;
            }

            @Override
            public void setBlock(int x, int y, int z, BlockData blockData) {
                if(!getBlockData(x,y,z).matches(blockData))
                {
                    Iris.edit.set(compound.getWorld(), x+xx, y, z+zz, blockData);
                }
            }

            @Override
            public BlockData getBlockData(int x, int y, int z) {
                return Iris.edit.get(compound.getWorld(), x+xx, y, z+zz);
            }

            @Override
            public ChunkData getRaw() {
                return null;
            }

            @Override
            public void inject(BiomeGrid biome) {

            }

            @Override
            public void setBlock(int i, int i1, int i2, @NotNull Material material) {
                setBlock(i, i1, i2, material.createBlockData());
            }

            @Override
            public void setBlock(int i, int i1, int i2, @NotNull MaterialData materialData) {
                setBlock(i, i1, i2, materialData.getItemType());
            }

            @Override
            public void setRegion(int i, int i1, int i2, int i3, int i4, int i5, @NotNull Material material) {

            }

            @Override
            public void setRegion(int i, int i1, int i2, int i3, int i4, int i5, @NotNull MaterialData materialData) {

            }

            @Override
            public void setRegion(int i, int i1, int i2, int i3, int i4, int i5, @NotNull BlockData blockData) {

            }

            @NotNull
            @Override
            public Material getType(int i, int i1, int i2) {
                return getBlockData(i, i1, i2).getMaterial();
            }

            @NotNull
            @Override
            public MaterialData getTypeAndData(int i, int i1, int i2) {
                return null;
            }

            @Override
            public byte getData(int i, int i1, int i2) {
                return 0;
            }
        });

        Iris.edit.flushNow();

    }


    @Override
    public void close() {
        J.car(art);
        if (getComposite() != null) {
            getComposite().close();


            if (isStudio()) {
                IrisWorlds.evacuate(getComposite().getWorld());
                Bukkit.unloadWorld(getComposite().getWorld(), !isStudio());
            }
        }
    }

    @Override
    public boolean isClosed() {
        try
        {
            return getComposite().getEngine(0).isClosed();
        }
        catch(Throwable e)
        {
            return false;
        }
    }

    @Override
    public EngineTarget getTarget() {
        return getComposite().getEngine(0).getTarget();
    }

    @Override
    public EngineCompound getCompound() {
        return getComposite();
    }

    @Override
    public boolean isFailing() {
        return getComposite().isFailing();
    }

    @Override
    public boolean isStudio() {
        return !production;
    }

    public boolean isVanillaCaves() {
        return false;
    }

    public KList<IrisBiome> getAllBiomes(String worldName) {
        if(getComposite() != null)
        {
            return getComposite().getAllBiomes();
        }

        else
        {
            KMap<String, IrisBiome> v = new KMap<>();
            IrisDimension dim = getDimension(worldName);
            dim.getAllAnyBiomes().forEach((i) -> v.put(i.getLoadKey(), i));

            try
            {
                dim.getDimensionalComposite().forEach((m) -> IrisDataManager.loadAnyDimension(m.getDimension()).getAllAnyBiomes().forEach((i) -> v.put(i.getLoadKey(), i)));
            }

            catch(Throwable e)
            {

            }

            Iris.info("Injecting " + v.size() + " biomes into the NMS World Chunk Provider (Iris)");

            return v.v();
        }
    }
}