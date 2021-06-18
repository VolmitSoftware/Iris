package com.volmit.iris.scaffold.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.generator.IrisEngineCompound;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.pregen.DirectWorldWriter;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.cache.Cache;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
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
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class EngineCompositeGenerator extends ChunkGenerator implements IrisAccess {
    private EngineCompound compound = null;
    private final AtomicBoolean initialized;
    private final String dimensionQuery;
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

    public EngineCompositeGenerator(String query, boolean production) {
        super();
        chunkCache = new KMap<>();
        hotloadcd = new ChronoLatch(3500);
        mst = M.ms();
        this.production = production;
        this.dimensionQuery = query;
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

    public void prepareSpawnAsync(long seed, String worldName, World.Environment env, int radius, Consumer<Double> progress, Runnable onComplete)
    {
        // TODO: WARNING HEIGHT
        prepareSpawnAsync(256, seed, worldName, env, radius, progress, onComplete);
    }

    public void prepareSpawnAsync(int worldHeight, long seed, String worldName, World.Environment env, int radius, Consumer<Double> progress, Runnable onComplete)
    {
        FakeWorld world = new FakeWorld(worldHeight, seed, new File(worldName), env);
        world.setWorldName(worldName);
        AtomicInteger generated = new AtomicInteger();
        int total = (int) Math.pow(radius * 2, 2);
        MultiBurst.burst.lazy(() -> {
            progress.accept(0D);
            BurstExecutor burst = MultiBurst.burst.burst(total);
            new Spiraler(radius * 2, radius * 2, (x, z) -> burst.queue(() -> {
                try {
                    precache(world, x, z);
                    generated.getAndIncrement();
                }

                catch(Throwable e)
                {
                    e.printStackTrace();
                }
            })).drain();
            burst.complete();
            System.out.println("BURSTER FINISHED TOTAL IS " + total + " OF GENNED " + generated.get());
            J.sleep(5000);
            progress.accept(1D);
            onComplete.run();
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
            if (hotloader != null) {
                J.a(() -> hotloader.check());
                getComposite().clean();
            }
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
        String query = dimensionQuery;
        query = Iris.linkMultiverseCore.getWorldNameType(world.getName(), query);

        IrisDimension dim = null;

        if (query == null) {
            File iris = new File(world.getWorldFolder(), "iris");

            if(iris.exists() && iris.isDirectory())
            {
                searching:
                for (File i : iris.listFiles()) {
                    // Look for v1 location
                    if (i.isDirectory() && i.getName().equals("dimensions")) {
                        for (File j : i.listFiles()) {
                            if (j.isFile() && j.getName().endsWith(".json")) {
                                query = j.getName().replaceAll("\\Q.json\\E", "");
                                Iris.error("Found v1 install. Please create a new world, this will cause chunks to change in your existing iris worlds!");
                                throw new RuntimeException();
                            }
                        }
                    }

                    // Look for v2 location
                    else if (i.isFile() && i.getName().equals("engine-metadata.json")) {
                        EngineData metadata = EngineData.load(i);
                        query = metadata.getDimension();
                        break;
                    }
                }
            }
        }

        if (query == null) {
            Iris.error("Cannot find iris dimension data for world: " + world.getName() + "! Assuming " + IrisSettings.get().getGenerator().getDefaultWorldType() + "!");
            query = IrisSettings.get().getGenerator().getDefaultWorldType();
        }

        dim = IrisDataManager.loadAnyDimension(query);

        if (dim == null) {
            Iris.proj.downloadSearch(new MortarSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), query, false);
            dim = IrisDataManager.loadAnyDimension(query);

            if(dim == null)
            {
                throw new RuntimeException("Cannot find dimension: " + query);
            }

            else
            {
                Iris.info("Download pack: " + query);
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
                    throw new RuntimeException("Cannot find dimension: " + query);
                }
            }
        }

        return dim;
    }

    private synchronized IrisDimension getDimension(String world) {
        String query = dimensionQuery;
        IrisDimension dim = null;

        if (query == null) {
            File iris = new File(world +"/iris");

            if(iris.exists() && iris.isDirectory())
            {
                for (File i : Objects.requireNonNull(iris.listFiles())) {
                    // Look for v1 location
                    if (i.isDirectory() && i.getName().equals("dimensions")) {
                        for (File j : Objects.requireNonNull(i.listFiles())) {
                            if (j.isFile() && j.getName().endsWith(".json")) {
                                query = j.getName().replaceAll("\\Q.json\\E", "");
                                Iris.error("Found v1 install. Please create a new world, this will cause chunks to change in your existing iris worlds!");
                                throw new RuntimeException();
                            }
                        }
                    }

                    // Look for v2 location
                    else if (i.isFile() && i.getName().equals("engine-metadata.json")) {
                        EngineData metadata = EngineData.load(i);
                        query = metadata.getDimension();
                        break;
                    }
                }
            }
        }

        if (query == null) {
            Iris.error("Cannot find iris dimension data for world: " + world + "! Assuming " + IrisSettings.get().getGenerator().getDefaultWorldType() + "!");
            query = IrisSettings.get().getGenerator().getDefaultWorldType();
        }

        dim = IrisDataManager.loadAnyDimension(query);

        if (dim == null) {
            Iris.proj.downloadSearch(new MortarSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), query, false);
            dim = IrisDataManager.loadAnyDimension(query);

            if(dim == null)
            {
                throw new RuntimeException("Cannot find dimension: " + query);
            }

            else
            {
                Iris.info("Download pack: " + query);
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
                    throw new RuntimeException("Cannot find dimension: " + query);
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


        System.out.println("INIT Get Dim");
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

    public void directWriteMCA(World w, int x, int z, DirectWorldWriter writer, MultiBurst burst)
    {
        BurstExecutor e = burst.burst(1024);
        int mcaox = x << 5;
        int mcaoz = z << 5;

        for(int i = 0; i < 32; i++)
        {
            int ii = i;
            for(int j = 0; j < 32; j++)
            {
                int jj = j;
                e.queue(() -> {
                    directWriteChunk(w, ii + mcaox, jj + mcaoz, writer);
                });
            }
        }

        e.complete();
    }

    public void directWriteChunk(World w, int x, int z, DirectWorldWriter writer)
    {
        int ox = x << 4;
        int oz = z << 4;
        net.querz.mca.Chunk cc = writer.getChunk(x, z);
        generateChunkRawData(w, x, z, new TerrainChunk() {
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
                setBiome(ox + x, 0, oz + z, bio);
            }

            @Override
            public void setBiome(int x, int y, int z, Biome bio) {
                writer.setBiome((ox + x), y, oz + z, bio);
            }

            @Override
            public int getMinHeight() {
                return w.getMinHeight();
            }

            @Override
            public int getMaxHeight() {
                return w.getMaxHeight();
            }

            @Override
            public void setBlock(int x, int y, int z, BlockData blockData) {
                cc.setBlockStateAt((x+ox)&15, y, (z+oz)&15, writer.getCompound(blockData), false);
            }

            @Override
            public BlockData getBlockData(int x, int y, int z) {
                if(y > getMaxHeight())
                {
                    y = getMaxHeight();
                }

                if(y < 0)
                {
                    y = 0;
                }

                return writer.getBlockData(cc.getBlockStateAt((x+ox)&15, y, (z+oz)&15));
            }

            @Override
            public ChunkData getRaw() {
                return null;
            }

            @Override
            public void inject(BiomeGrid biome) {

            }

            @Override
            public void setBlock(int x, int y, int z, @NotNull Material material) {

            }

            @Override
            public void setBlock(int x, int y, int z, @NotNull MaterialData material) {

            }

            @Override
            public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull Material material) {

            }

            @Override
            public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull MaterialData material) {

            }

            @Override
            public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull BlockData blockData) {

            }

            @NotNull
            @Override
            public Material getType(int x, int y, int z) {
                return null;
            }

            @NotNull
            @Override
            public MaterialData getTypeAndData(int x, int y, int z) {
                return null;
            }

            @Override
            public byte getData(int x, int y, int z) {
                return 0;
            }
        }).run();

        writer.optimizeChunk(x, z);
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
        Hunk<BlockData> post = Hunk.newAtomicHunk(biomes.getWidth(), biomes.getHeight(), biomes.getDepth());
        compound.generate(x * 16, z * 16, blocks, post, biomes);
        generated++;

        return () -> blocks.insertSoftly(0,0,0, post, (b) -> b == null || B.isAirOrFluid(b));
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
        if (getCompound() == null){
            return null;
        }
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
            public int getMinHeight() {
                return getComposite().getWorld().getMinHeight();
            }

            @Override
            public int getMaxHeight() {
                return getComposite().getWorld().getMaxHeight();
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
        try {
            return getComposite().getEngine(0).getTarget();
        } catch (NullPointerException e){
            Iris.info("Failed to get composite engine. Please re-create the world in case you notice issues");
            return null;
        }
    }

    @Override
    public EngineCompound getCompound() {
        return getComposite();
    }

    @Override
    public boolean isFailing() {
        if(getComposite() == null)
        {
            return false;
        }

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