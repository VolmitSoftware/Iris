/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.framework;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.BiomeBaseInjector;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.IrisEngineCompound;
import com.volmit.iris.engine.IrisWorlds;
import com.volmit.iris.engine.cache.Cache;
import com.volmit.iris.engine.data.B;
import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.engine.data.mca.NBTWorld;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.parallel.BurstExecutor;
import com.volmit.iris.engine.parallel.MultiBurst;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.ReactiveFolder;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.reflect.V;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import io.netty.util.internal.ConcurrentSet;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class EngineCompositeGenerator extends ChunkGenerator implements IrisAccess {
    private final AtomicReference<EngineCompound> compound = new AtomicReference<>();
    private final AtomicBoolean initialized;
    private final String dimensionQuery;
    private final boolean production;
    private final KList<BlockPopulator> populators;
    private long mst = 0;
    private int generated = 0;
    private int lgenerated = 0;
    private final ChronoLatch hotloadcd;
    @Getter
    private double generatedPerSecond = 0;
    private final int art;
    private ReactiveFolder hotloader = null;

    public EngineCompositeGenerator() {
        this(null, true);
    }

    public EngineCompositeGenerator(String query, boolean production) {
        super();
        hotloadcd = new ChronoLatch(3500);
        mst = M.ms();
        this.production = production;
        this.dimensionQuery = query;
        initialized = new AtomicBoolean(false);
        art = J.ar(this::tick, 100);
        populators = new KList<BlockPopulator>().qadd(new BlockPopulator() {
            @Override
            public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk chunk) {
                if (compound.get() != null) {
                    for (BlockPopulator i : compound.get().getPopulators()) {
                        i.populate(world, random, chunk);
                    }
                }
            }
        });
    }

    @Override
    public void hotload() {
        if (isStudio()) {
            Iris.proj.updateWorkspace();
            getData().dump();
            J.s(() -> {
                try {
                    for (Player i : getTarget().getWorld().getPlayers()) {
                        new VolmitSender(i, Iris.instance.getTag()).sendMessage("Dimension Hotloaded");
                        i.playSound(i.getLocation(), Sound.BLOCK_COPPER_PLACE, 1f, 1.25f);
                    }
                } catch (Throwable e) {
                    Iris.reportError(e);

                }
            });
            initialized.lazySet(false);
        }
    }

    public void tick() {
        if (isClosed()) {
            return;
        }

        if (!initialized.get()) {
            return;
        }

        try {
            if (hotloader != null) {
                J.a(() -> hotloader.check());
                getComposite().clean();
            }
        } catch (Throwable e) {
            Iris.reportError(e);

        }

        if (M.ms() - mst > 1000) {
            generatedPerSecond = (double) (generated - lgenerated) / ((double) (M.ms() - mst) / 1000D);
            mst = M.ms();
            lgenerated = generated;
        }
    }

    private synchronized IrisDimension getDimension(IrisWorld world) {
        String query = dimensionQuery;
        query = Iris.linkMultiverseCore.getWorldNameType(world.name(), query);

        IrisDimension dim = null;

        if (query == null) {
            File iris = new File(world.worldFolder(), "iris");

            if (iris.exists() && iris.isDirectory()) {
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
            Iris.error("Cannot find iris dimension data for world: " + world.name() + "! Assuming " + IrisSettings.get().getGenerator().getDefaultWorldType() + "!");
            query = IrisSettings.get().getGenerator().getDefaultWorldType();
        }

        dim = IrisDataManager.loadAnyDimension(query);

        if (dim == null) {
            Iris.proj.downloadSearch(new VolmitSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), query, false);
            dim = IrisDataManager.loadAnyDimension(query);

            if (dim == null) {
                throw new RuntimeException("Cannot find dimension: " + query);
            } else {
                Iris.info("Download pack: " + query);
            }
        }

        if (production) {
            IrisDimension od = dim;
            dim = new IrisDataManager(getDataFolder(world)).getDimensionLoader().load(od.getLoadKey());

            if (dim == null) {
                Iris.info("Installing Iris pack " + od.getName() + " into world " + world.name() + "...");
                Iris.proj.installIntoWorld(new VolmitSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), od.getLoadKey(), world.worldFolder());
                dim = new IrisDataManager(getDataFolder(world)).getDimensionLoader().load(od.getLoadKey());

                if (dim == null) {
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
            File iris = new File(world + "/iris");

            if (iris.exists() && iris.isDirectory()) {
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
            Iris.proj.downloadSearch(new VolmitSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), query, false);
            dim = IrisDataManager.loadAnyDimension(query);

            if (dim == null) {
                throw new RuntimeException("Cannot find dimension: " + query);
            } else {
                Iris.info("Download pack: " + query);
            }
        }

        if (production) {
            IrisDimension od = dim;
            dim = new IrisDataManager(getDataFolder(world)).getDimensionLoader().load(od.getLoadKey());

            if (dim == null) {
                Iris.info("Installing Iris pack " + od.getName() + " into world " + world + "...");
                Iris.proj.installIntoWorld(new VolmitSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), od.getLoadKey(), new File(world));
                dim = new IrisDataManager(getDataFolder(world)).getDimensionLoader().load(od.getLoadKey());

                if (dim == null) {
                    throw new RuntimeException("Cannot find dimension: " + query);
                }
            }
        }

        Iris.info(world + " is configured to generate " + dim.getName() + "!");

        return dim;
    }

    public synchronized void initialize(IrisWorld world) {
        if (initialized.get()) {
            return;
        }

        try {
            initialized.set(true);
            IrisDimension dim = getDimension(world);
            IrisDataManager data = production ? new IrisDataManager(getDataFolder(world)) : dim.getLoader().copy();
            compound.set(new IrisEngineCompound(world, dim, data, Iris.getThreadCount()));
            compound.get().setStudio(!production);
            populators.clear();
            populators.addAll(compound.get().getPopulators());
            hotloader = new ReactiveFolder(data.getDataFolder(), (a, c, d) -> hotload());
            dim.installDataPack(() -> data, Iris.instance.getDatapacksFolder());
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            Iris.error("FAILED TO INITIALIZE DIMENSION FROM " + world.toString());
        }
    }

    /**
     * Place strongholds in the world
     */
    public void placeStrongholds(World world) {
        EngineData metadata = getComposite().getEngineMetadata();
        // TODO: In nms class, not here. Also it doesnt work
        if (metadata.getStrongholdPositions() == null || metadata.getStrongholdPositions().size() == 0) {

            List<IrisPosition> strongholds = new ArrayList<>();
            Object nmsWorld = new V(world).invoke("getHandle");
            Object chunkProvider = new V(nmsWorld).invoke("getChunkProvider");
            Object chunkGenerator = new V(chunkProvider).invoke("getChunkGenerator");
            try {
                Class<?> clazz = Class.forName("net.minecraft.world.level.chunk.ChunkGenerator");
                Class<?> clazzSG = Class.forName("net.minecraft.world.level.levelgen.feature.StructureGenerator");
                Class<?> clazzBP = Class.forName("net.minecraft.core.BlockPosition");
                @SuppressWarnings("rawtypes") Constructor bpCon = clazzBP.getConstructor(int.class, int.class, int.class);

                //By default, we place 9 strongholds. One near 0,0 and 8 all around it at about 10_000 blocks out
                int[][] coords = {{0, 0}, {7000, -7000}, {10000, 0}, {7000, 7000}, {0, 10000}, {-7000, 7000}, {-10000, 0}, {-7000, -7000}, {0, -10000}};

                //Set of stronghold locations so we don't place 2 strongholds at the same location
                Set<Long> existing = new ConcurrentSet<>();
                Set<CompletableFuture<Object>> futures = new HashSet<>();
                for (int[] currCoords : coords) {
                    //Create a NMS BlockPosition
                    Object blockPosToTest = bpCon.newInstance(currCoords[0], 0, currCoords[1]);
                    //Create a CompletableFuture so we can track once the sync code is complete

                    CompletableFuture<Object> future = new CompletableFuture<>();
                    futures.add(future);

                    //We have to run this code synchronously because it uses NMS
                    J.s(() -> {
                        try {
                            Object o = getBP(clazz, clazzSG, clazzBP, nmsWorld, blockPosToTest, chunkGenerator);
                            future.complete(o);
                        } catch (Exception e) {
                            Iris.reportError(e);
                            e.printStackTrace();
                            future.complete(e);
                        }
                    });
                }

                CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                all.thenAccept((_void) -> { //Once all futures for all 9 strongholds have completed
                    for (CompletableFuture<Object> future : futures) {
                        try {
                            Object pos = future.getNow(null);
                            if (pos != null) {
                                IrisPosition ipos = new IrisPosition((int) new V(pos, false).invoke("getX"), (int) new V(pos,
                                        false).invoke("getY"), (int) new V(pos, false).invoke("getZ"));
                                long xz = (((long) ipos.getX()) << 32) | (ipos.getZ() & 0xffffffffL);
                                if (existing.contains(xz)) return; //Make sure we don't double up on stronghold locs
                                existing.add(xz);
                                strongholds.add(ipos);

                            }
                        } catch (Exception e) {
                            Iris.reportError(e);
                            e.printStackTrace();
                        }
                    }

                    StringBuilder positions = new StringBuilder();
                    for (IrisPosition pos : strongholds) {
                        positions.append("(").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ()).append(") ");
                    }
                    Iris.info("Strongholds (" + strongholds.size() + ") found at [" + positions + "]");

                    metadata.setStrongholdPositions(strongholds);
                    getComposite().saveEngineMetadata();
                });

            } catch (Exception e) {
                Iris.reportError(e);
                strongholds.add(new IrisPosition(1337, 32, -1337));
                metadata.setStrongholdPositions(strongholds);
                Iris.warn("Couldn't properly find the stronghold position for this world. Is this headless mode? Are you not using 1.16 or higher?");
                Iris.warn("  -> Setting default stronghold position");
                e.printStackTrace();
                StringBuilder positions = new StringBuilder();
                for (IrisPosition pos : strongholds) {
                    positions.append("(").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ()).append(") ");
                }
                Iris.info("Strongholds (" + metadata.getStrongholdPositions().size() + ") found at [" + positions + "]");
            }

        }
    }


    /**
     * Get BlockPosition for nearest stronghold from the provided position
     */
    private Object getBP(Class<?> clazz, Class<?> clazzSG, Class<?> clazzBP, Object nmsWorld, Object pos, Object chunkGenerator) throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final String stronghold = "k"; //1.17_01 mapping

        Object structureGeneratorStronghold = clazzSG.getDeclaredField(stronghold).get(null);
        Method getNearestGeneratedFeature = clazz.getDeclaredMethod("findNearestMapFeature",
                nmsWorld.getClass(),
                clazzSG,
                clazzBP,
                int.class,
                boolean.class
        );
        return getNearestGeneratedFeature.invoke(chunkGenerator,
                nmsWorld,
                structureGeneratorStronghold,
                pos,
                100,
                false
        );
    }

    private File getDataFolder(IrisWorld world) {
        return new File(world.worldFolder(), "iris/pack");
    }

    private File getDataFolder(String world) {
        return new File(world + "/iris/pack");
    }

    @NotNull
    @Override
    public ChunkData generateChunkData(@NotNull World world, @NotNull Random ignored, int x, int z, @NotNull BiomeGrid biome) {
        PrecisionStopwatch ps = PrecisionStopwatch.start();
        TerrainChunk tc = TerrainChunk.create(world, biome);

        if(!getComposite().getWorld().hasRealWorld())
        {
            getComposite().getWorld().bind(world);
        }

        generateChunkRawData(getComposite().getWorld(), x, z, tc).run();
        generated++;
        ps.end();

        if (IrisSettings.get().getGeneral().isDebug()) {
            Iris.debug("Chunk " + C.GREEN + x + "," + z + C.LIGHT_PURPLE + " in " + C.YELLOW + Form.duration(ps.getMillis(), 2) + C.LIGHT_PURPLE + " Rate: " + C.BLUE + Form.f(getGeneratedPerSecond(), 0) + "/s");
        }

        return tc.getRaw();
    }

    @Override
    public void directWriteMCA(IrisWorld w, int x, int z, NBTWorld writer, MultiBurst burst) {
        BurstExecutor e = burst.burst(1024);
        int mcaox = x << 5;
        int mcaoz = z << 5;

        for (int i = 0; i < 32; i++) {
            int ii = i;
            for (int j = 0; j < 32; j++) {
                int jj = j;
                e.queue(() -> directWriteChunk(w, ii + mcaox, jj + mcaoz, writer));
            }
        }

        e.complete();
    }

    @Override
    public void directWriteChunk(IrisWorld w, int x, int z, NBTWorld writer) {
        int ox = x << 4;
        int oz = z << 4;
        com.volmit.iris.engine.data.mca.Chunk cc = writer.getChunk(x, z);
        BiomeBaseInjector injector = (xx, yy, zz, biomeBase) -> cc.setBiomeAt(ox + xx, yy, oz + zz, INMS.get().getTrueBiomeBaseId(biomeBase));
        //noinspection deprecation
        generateChunkRawData(w, x, z, new TerrainChunk() {
            @Override
            public BiomeBaseInjector getBiomeBaseInjector() {
                return injector;
            }

            @Override
            public void setRaw(ChunkData data) {

            }

            @NotNull
            @Override
            public Biome getBiome(int x, int z) {
                return Biome.THE_VOID;
            }

            @NotNull
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
                writer.setBiome(ox + x, y, oz + z, bio);
            }

            @Override
            public int getMinHeight() {
                return w.minHeight();
            }

            @Override
            public int getMaxHeight() {
                return w.maxHeight();
            }

            @Override
            public void setBlock(int x, int y, int z, BlockData blockData) {
                int xx = (x + ox) & 15;
                int zz = (z + oz) & 15;

                if (y > 255 || y < 0) {
                    return;
                }

                cc.setBlockStateAt(xx, y, zz, NBTWorld.getCompound(blockData), false);
            }

            @NotNull
            @Override
            public BlockData getBlockData(int x, int y, int z) {
                if (y > getMaxHeight()) {
                    y = getMaxHeight();
                }

                if (y < 0) {
                    y = 0;
                }

                return NBTWorld.getBlockData(cc.getBlockStateAt((x + ox) & 15, y, (z + oz) & 15));
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
    }

    public Runnable generateChunkRawData(IrisWorld world, int x, int z, TerrainChunk tc) {
        initialize(world);
        Hunk<BlockData> blocks = Hunk.view((ChunkData) tc);
        Hunk<Biome> biomes = Hunk.view((BiomeGrid) tc);
        Hunk<BlockData> post = Hunk.newAtomicHunk(biomes.getWidth(), biomes.getHeight(), biomes.getDepth());
        compound.get().generate(x * 16, z * 16, blocks, post, biomes);

        return () -> blocks.insertSoftly(0, 0, 0, post, (b) -> b == null || B.isAirOrFluid(b));
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
        return compound.get();
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
        return getEngineAccess(y).getBiome(x, y - getComposite().getEngineForHeight(y).getMinHeight(), z);
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
        if (getCompound() == null) {
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
    public void close() {
        J.car(art);
        if (getComposite() != null) {
            getComposite().close();

            if (isStudio() && getComposite().getWorld().hasRealWorld()) {
                getComposite().getWorld().evacuate();
                Bukkit.unloadWorld(getComposite().getWorld().realWorld(), !isStudio());
            }
        }
    }

    @Override
    public boolean isClosed() {
        try {
            return getComposite().getEngine(0).isClosed();
        } catch (Throwable e) {
            Iris.reportError(e);
            return false;
        }
    }

    @Override
    public EngineTarget getTarget() {
        try {
            return getComposite().getEngine(0).getTarget();
        } catch (NullPointerException e) {
            Iris.reportError(e);
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
        if (getComposite() == null) {
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
        if (getComposite() != null) {
            return getComposite().getAllBiomes();
        } else {
            KMap<String, IrisBiome> v = new KMap<>();
            IrisDimension dim = getDimension(worldName);
            dim.getAllAnyBiomes().forEach((i) -> v.put(i.getLoadKey(), i));

            try {
                dim.getDimensionalComposite().forEach((m) -> IrisDataManager.loadAnyDimension(m.getDimension()).getAllAnyBiomes().forEach((i) -> v.put(i.getLoadKey(), i)));
            } catch (Throwable ignored) {
                Iris.reportError(ignored);

            }

            Iris.info("Injecting " + v.size() + " biomes into the NMS World Chunk Provider (Iris)");

            return v.v();
        }
    }
}