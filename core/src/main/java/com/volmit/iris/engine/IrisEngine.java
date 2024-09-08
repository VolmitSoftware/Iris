/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.events.IrisEngineHotloadEvent;
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.core.nms.container.BlockPos;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.core.project.IrisProject;
import com.volmit.iris.core.service.PreservationSVC;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.*;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.scripting.EngineExecutionEnvironment;
import com.volmit.iris.engine.service.EnginePlayerHandlerSVC;
import com.volmit.iris.util.atomics.AtomicRollingSequence;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.io.JarScanner;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterStructurePOI;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Synchronized;
import lombok.ToString;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Data
@EqualsAndHashCode(exclude = "context")
@ToString(exclude = "context")
public class IrisEngine implements Engine {
    private static final Map<Class<? extends IrisEngineService>, Constructor<? extends IrisEngineService>> SERVICES = scanServices();
    private final KMap<Class<? extends IrisEngineService>, IrisEngineService> services;
    private final AtomicInteger bud;
    private final AtomicInteger buds;
    private final AtomicInteger generated;
    private final AtomicInteger generatedLast;
    private final AtomicDouble perSecond;
    private final AtomicLong lastGPS;
    private final EngineTarget target;
    private final IrisContext context;
    private final EngineMantle mantle;
    private final ChronoLatch perSecondLatch;
    private final ChronoLatch perSecondBudLatch;
    private final EngineMetrics metrics;
    private final boolean studio;
    private boolean headless;
    private final AtomicRollingSequence wallClock;
    private final int art;
    private final AtomicCache<IrisEngineData> engineData = new AtomicCache<>();
    private final AtomicBoolean cleaning;
    private final ChronoLatch cleanLatch;
    private final SeedManager seedManager;
    private final ReentrantLock dataLock;
    private EngineMode mode;
    private EngineExecutionEnvironment execution;
    private EngineWorldManager worldManager;
    private KList<EnginePlayer> players;
    private volatile int parallelism;
    private volatile int minHeight;
    private boolean failing;
    private boolean closed;
    private int cacheId;
    private double maxBiomeObjectDensity;
    private double maxBiomeLayerDensity;
    private double maxBiomeDecoratorDensity;
    private IrisComplex complex;

    public IrisEngine(EngineTarget target, boolean studio) {
        this.studio = studio;
        this.target = target;
        this.players = new KList<>();
        getEngineData();
        verifySeed();
        this.seedManager = new SeedManager(target.getWorld().getRawWorldSeed());
        services = new KMap<>();
        dataLock = new ReentrantLock();
        bud = new AtomicInteger(0);
        buds = new AtomicInteger(0);
        metrics = new EngineMetrics(32);
        cleanLatch = new ChronoLatch(10000);
        generatedLast = new AtomicInteger(0);
        perSecond = new AtomicDouble(0);
        perSecondLatch = new ChronoLatch(1000, false);
        perSecondBudLatch = new ChronoLatch(1000, false);
        wallClock = new AtomicRollingSequence(32);
        lastGPS = new AtomicLong(M.ms());
        generated = new AtomicInteger(0);
        mantle = new IrisEngineMantle(this);
        context = new IrisContext(this);
        cleaning = new AtomicBoolean(false);
        context.touch();
        getData().setEngine(this);
        getData().loadPrefetch(this);
        Iris.info("Initializing Engine: " + target.getWorld().name() + "/" + target.getDimension().getLoadKey() + " (" + target.getDimension().getDimensionHeight() + " height) Seed: " + getSeedManager().getSeed());
        minHeight = 0;
        failing = false;
        closed = false;
        art = J.ar(this::tickRandomPlayer, 0);
        setupEngine();
        Iris.debug("Engine Initialized " + getCacheID());
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<? extends IrisEngineService>, Constructor<? extends IrisEngineService>> scanServices() {
        JarScanner js = new JarScanner(Iris.instance.getJarFile(), "com.volmit.iris.engine.service");
        J.attempt(js::scan);
        KMap<Class<? extends IrisEngineService>, Constructor<? extends IrisEngineService>> map = new KMap<>();
        js.getClasses()
                .stream()
                .filter(IrisEngineService.class::isAssignableFrom)
                .map(c -> (Class<? extends IrisEngineService>) c)
                .forEach(c -> {
                    try {
                        map.put(c, c.getConstructor(Engine.class));
                    } catch (NoSuchMethodException e) {
                        Iris.warn("Failed to load service " + c.getName() + " due to missing constructor");
                    }
                });

        return Collections.unmodifiableMap(map);
    }

    private void verifySeed() {
        if (getEngineData().getSeed() != null && getEngineData().getSeed() != target.getWorld().getRawWorldSeed()) {
            target.getWorld().setRawWorldSeed(getEngineData().getSeed());
        }
    }

    private void tickRandomPlayer() {
        recycle();
        if (perSecondBudLatch.flip()) {
            buds.set(bud.get());
            bud.set(0);
        }

        var effects = getService(EnginePlayerHandlerSVC.class);
        if (effects != null) effects.tickRandomPlayer();
    }

    @Override
    public EnginePlayer getEnginePlayer(UUID uuid) {
        return getPlayer(uuid);
    }

    @Override
    public KList<EnginePlayer> getEnginePlayers() {
        return players;
    }

    private EnginePlayer getPlayer(UUID uuid) {
        for (EnginePlayer player : players) {
            if (player.getPlayer().getUniqueId().equals(uuid)) return player;
        }
        return null;
    }

    private void prehotload() {
        worldManager.close();
        complex.close();
        execution.close();
        mode.close();
        services.values().forEach(s -> s.onDisable(true));
        services.values().forEach(Iris.instance::unregisterListener);

        J.a(() -> new IrisProject(getData().getDataFolder()).updateWorkspace());
    }

    private void setupEngine() {
        try {
            Iris.debug("Setup Engine " + getCacheID());
            cacheId = RNG.r.nextInt();
            boolean hotload = true;
            if (services.isEmpty()) {
                SERVICES.forEach((s, c) -> {
                    try {
                        services.put(s, c.newInstance(this));
                    } catch (InstantiationException | IllegalAccessException |
                             InvocationTargetException e) {
                        Iris.error("Failed to create service " + s.getName());
                        e.printStackTrace();
                    }
                });
                hotload = false;
            }
            for (var service : services.values()) {
                service.onEnable(hotload);
                Iris.instance.registerListener(service);
            }
            worldManager = new IrisWorldManager(this);
            complex = new IrisComplex(this);
            execution = new IrisExecutionEnvironment(this);
            setupMode();
            J.a(this::computeBiomeMaxes);
        } catch (Throwable e) {
            Iris.error("FAILED TO SETUP ENGINE!");
            e.printStackTrace();
        }

        Iris.debug("Engine Setup Complete " + getCacheID());
    }

    private void setupMode() {
        if (mode != null) {
            mode.close();
        }

        mode = getDimension().getMode().getType().create(this);
    }

    @Override
    public void generateMatter(int x, int z, boolean multicore, ChunkContext context) {
        getMantle().generateMatter(x, z, multicore, context);
    }

    @Override
    public Set<String> getObjectsAt(int x, int z) {
        return getMantle().getObjectComponent().guess(x, z);
    }

    @Override
    public Set<Pair<String, BlockPos>> getPOIsAt(int chunkX, int chunkY) {
        Set<Pair<String, BlockPos>> pois = new HashSet<>();
        getMantle().getMantle().iterateChunk(chunkX, chunkY, MatterStructurePOI.class, (x, y, z, d) -> pois.add(new Pair<>(d.getType(), new BlockPos(x, y, z))));
        return pois;
    }

    @Override
    public IrisJigsawStructure getStructureAt(int x, int z) {
        return getMantle().getJigsawComponent().guess(x, z);
    }

    private void warmupChunk(int x, int z) {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int xx = x + (i << 4);
                int zz = z + (z << 4);
                getComplex().getTrueBiomeStream().get(xx, zz);
                getComplex().getHeightStream().get(xx, zz);
            }
        }
    }

    @Override
    public void hotload() {
        hotloadSilently();
        Iris.callEvent(new IrisEngineHotloadEvent(this));
        if (isStudio()) {
            for (Player player : target.getWorld().getPlayers()) {
                VolmitSender sender = new VolmitSender(player);
                sender.sendMessage(C.GREEN + "Hotloaded");
                sender.playSound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
            }

        }
    }

    public void hotloadComplex() {
        complex.close();
        complex = new IrisComplex(this);
    }

    public void hotloadSilently() {
        getData().dump();
        getData().clearLists();
        getTarget().setDimension(getData().getDimensionLoader().load(getDimension().getLoadKey()));
        prehotload();
        setupEngine();
    }

    @Override
    public IrisEngineData getEngineData() {
        return engineData.aquire(() -> {
            //TODO: Method this file
            File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + ".json");
            IrisEngineData data = null;

            if (f.exists()) {
                try {
                    data = new Gson().fromJson(IO.readAll(f), IrisEngineData.class);
                    if (data == null) {
                        Iris.error("Failed to read Engine Data! Corrupted File? recreating...");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (data == null) {
                data = new IrisEngineData();
                data.getStatistics().setIrisCreationVersion(Iris.instance.getIrisVersion());
                data.getStatistics().setMCVersion(Iris.instance.getMCVersion());
                data.getStatistics().setIrisToUpgradedVersion(Iris.instance.getIrisVersion());
                if (data.getStatistics().getIrisCreationVersion() == -1 || data.getStatistics().getMCVersion() == -1) {
                    Iris.error("Failed to setup Engine Data!");
                }

                if (f.getParentFile().exists() || f.getParentFile().mkdirs()) {
                    try {
                        IO.writeAll(f, new Gson().toJson(data));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Iris.error("Failed to setup Engine Data!");
                }
            }

            return data;
        });
    }

    @Override
    public void saveEngineData() {
        //TODO: Method this file
        if (dataLock.tryLock()) {
            try {
                File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + ".json");
                f.getParentFile().mkdirs();
                try {
                    IO.writeAll(f, new Gson().toJson(getEngineData()));
                    Iris.debug("Saved Engine Data");
                } catch (IOException e) {
                    Iris.error("Failed to save Engine Data");
                    e.printStackTrace();
                }
            } finally {
                dataLock.unlock();
            }
        }
    }

    @Override
    public int getGenerated() {
        return generated.get();
    }

    // todo: eghum no.
    @Override
    public void addGenerated(int x, int z) {
        try {
            File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + ".json");
            if (generated.incrementAndGet() == 661) {
                J.a(() -> getData().savePrefetch(this));
            }
        } catch (Exception e) {
            Iris.error("Failed to add generated chunk!");
            e.printStackTrace();
        }
    }

    @Override
    public double getGeneratedPerSecond() {
        if (perSecondLatch.flip()) {
            double g = generated.get() - generatedLast.get();
            generatedLast.set(generated.get());

            if (g == 0) {
                return 0;
            }

            long dur = M.ms() - lastGPS.get();
            lastGPS.set(M.ms());
            perSecond.set(g / ((double) (dur) / 1000D));
        }

        return perSecond.get();
    }

    @Override
    public boolean isStudio() {
        return studio;
    }

    private void computeBiomeMaxes() {
        for (IrisBiome i : getDimension().getAllBiomes(this)) {
            double density = 0;

            for (IrisObjectPlacement j : i.getObjects()) {
                density += j.getDensity() * j.getChance();
            }

            maxBiomeObjectDensity = Math.max(maxBiomeObjectDensity, density);
            density = 0;

            for (IrisDecorator j : i.getDecorators()) {
                density += Math.max(j.getStackMax(), 1) * j.getChance();
            }

            maxBiomeDecoratorDensity = Math.max(maxBiomeDecoratorDensity, density);
            density = 0;

            for (IrisBiomePaletteLayer j : i.getLayers()) {
                density++;
            }

            maxBiomeLayerDensity = Math.max(maxBiomeLayerDensity, density);
        }
    }

    @Override
    public int getBlockUpdatesPerSecond() {
        return buds.get();
    }

    public void printMetrics(CommandSender sender) {
        KMap<String, Double> totals = new KMap<>();
        KMap<String, Double> weights = new KMap<>();
        double masterWallClock = wallClock.getAverage();
        KMap<String, Double> timings = getMetrics().pull();
        double totalWeight = 0;
        double wallClock = getMetrics().getTotal().getAverage();

        for (double j : timings.values()) {
            totalWeight += j;
        }

        for (String j : timings.k()) {
            weights.put(getName() + "." + j, (wallClock / totalWeight) * timings.get(j));
        }

        totals.put(getName(), wallClock);

        double mtotals = 0;

        for (double i : totals.values()) {
            mtotals += i;
        }

        for (String i : totals.k()) {
            totals.put(i, (masterWallClock / mtotals) * totals.get(i));
        }

        double v = 0;

        for (double i : weights.values()) {
            v += i;
        }

        for (String i : weights.k()) {
            weights.put(i, weights.get(i) / v);
        }

        sender.sendMessage("Total: " + C.BOLD + C.WHITE + Form.duration(masterWallClock, 0));

        for (String i : totals.k()) {
            sender.sendMessage("  Engine " + C.UNDERLINE + C.GREEN + i + C.RESET + ": " + C.BOLD + C.WHITE + Form.duration(totals.get(i), 0));
        }

        sender.sendMessage("Details: ");

        for (String i : weights.sortKNumber().reverse()) {
            String befb = C.UNDERLINE + "" + C.GREEN + "" + i.split("\\Q[\\E")[0] + C.RESET + C.GRAY + "[";
            String num = C.GOLD + i.split("\\Q[\\E")[1].split("]")[0] + C.RESET + C.GRAY + "].";
            String afb = C.ITALIC + "" + C.AQUA + i.split("\\Q]\\E")[1].substring(1) + C.RESET + C.GRAY;

            sender.sendMessage("  " + befb + num + afb + ": " + C.BOLD + C.WHITE + Form.pc(weights.get(i), 0));
        }
    }

    @Synchronized
    public boolean setEngineHeadless() {
        if(null != this.getWorld().realWorld()) {
            J.s(() -> Bukkit.unloadWorld(getWorld().realWorld().getName(), true));
            headless = true;
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        if (headless) return;
        PregeneratorJob.shutdownInstance();
        closed = true;
        J.car(art);
        try {
            if (getWorld().hasHeadless()) getWorld().headless().close();
        } catch (IOException e) {
            Iris.reportError(e);
        }
        services.values().forEach(s -> s.onDisable(false));
        getWorldManager().close();
        getTarget().close();
        saveEngineData();
        getMantle().close();
        getComplex().close();
        mode.close();
        getData().dump();
        getData().clearLists();
        Iris.service(PreservationSVC.class).dereference();
        Iris.debug("Engine Fully Shutdown!");
        complex = null;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void recycle() {
        if (!cleanLatch.flip()) {
            return;
        }

        if (cleaning.get()) {
            cleanLatch.flipDown();
            return;
        }

        cleaning.set(true);

        J.a(() -> {
            try {
                getData().getObjectLoader().clean();
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Cleanup failed! Enable debug to see stacktrace.");
            }

            cleaning.lazySet(false);
        });
    }

    @BlockCoordinates
    @Override
    public void generate(int x, int z, Hunk<BlockData> vblocks, Hunk<Biome> vbiomes, boolean multicore) throws WrongEngineBroException {
        if (closed) {
            throw new WrongEngineBroException();
        }

        context.touch();
        getEngineData().getStatistics().generatedChunk();
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            Hunk<BlockData> blocks = vblocks.listen((xx, y, zz, t) -> catchBlockUpdates(x + xx, y + getMinHeight(), z + zz, t));

            if (getDimension().isDebugChunkCrossSections() && ((x >> 4) % getDimension().getDebugCrossSectionsMod() == 0 || (z >> 4) % getDimension().getDebugCrossSectionsMod() == 0)) {
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        blocks.set(i, 0, j, Material.CRYING_OBSIDIAN.createBlockData());
                    }
                }
            } else {
                mode.generate(x, z, blocks, vbiomes, multicore);
            }

            getMantle().getMantle().flag(x >> 4, z >> 4, MantleFlag.REAL, true);
            getMetrics().getTotal().put(p.getMilliseconds());
            addGenerated(x, z);
        } catch (Throwable e) {
            Iris.reportError(e);
            fail("Failed to generate " + x + ", " + z, e);
        }
    }

    @Override
    public void blockUpdatedMetric() {
        bud.incrementAndGet();
    }

    @Override
    public IrisBiome getFocus() {
        if (getDimension().getFocus() == null || getDimension().getFocus().trim().isEmpty()) {
            return null;
        }

        return getData().getBiomeLoader().load(getDimension().getFocus());
    }

    @Override
    public IrisRegion getFocusRegion() {
        if (getDimension().getFocusRegion() == null || getDimension().getFocusRegion().trim().isEmpty()) {
            return null;
        }

        return getData().getRegionLoader().load(getDimension().getFocusRegion());
    }

    @Override
    public void fail(String error, Throwable e) {
        failing = true;
        Iris.error(error);
        e.printStackTrace();
    }

    @Override
    public boolean hasFailed() {
        return failing;
    }

    @Override
    public int getCacheID() {
        return cacheId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IrisEngineService> T getService(Class<T> clazz) {
        return (T) services.get(clazz);
    }

    private boolean EngineSafe() {
        // Todo: this has potential if done right
        int EngineMCVersion = getEngineData().getStatistics().getMCVersion();
        int EngineIrisVersion = getEngineData().getStatistics().getIrisCreationVersion();
        int MinecraftVersion = Iris.instance.getMCVersion();
        int IrisVersion = Iris.instance.getIrisVersion();
        if (EngineIrisVersion != IrisVersion) {
            return false;
        }
        if (EngineMCVersion != MinecraftVersion) {
            return false;
        }
        return true;
    }
}
