/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.tools;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pregenerator.PregenTask;
import art.arcane.iris.core.pregenerator.PregeneratorMethod;
import art.arcane.iris.core.project.IrisProject;
import art.arcane.iris.core.pregenerator.methods.CachedPregenMethod;
import art.arcane.iris.core.pregenerator.methods.HybridPregenMethod;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.iris.util.common.plugin.VolmitSender;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Something you really want to wear if working on Iris. Shit gets pretty hectic down there.
 * Hope you packed snacks & road sodas.
 */
public class IrisToolbelt {
    @ApiStatus.Internal
    public static Map<String, Boolean> toolbeltConfiguration = new HashMap<>();
    private static final Map<String, AtomicInteger> worldMaintenanceDepth = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> worldMaintenanceMantleBypassDepth = new ConcurrentHashMap<>();
    private static final Method BUKKIT_IS_STOPPING_METHOD = resolveBukkitIsStoppingMethod();

    /**
     * Will find / download / search for the dimension or return null
     * <p>
     * - You can provide a dimenson in the packs folder by the folder name
     * - You can provide a github repo by using (assumes branch is master unless specified)
     * - GithubUsername/repository
     * - GithubUsername/repository/branch
     *
     * @param dimension the dimension id such as overworld or flat
     * @return the IrisDimension or null
     */
    public static IrisDimension getDimension(String dimension) {
        if (dimension == null) {
            return null;
        }

        String requested = dimension.trim();
        if (requested.isEmpty()) {
            return null;
        }

        File packsFolder = Iris.instance.getDataFolder("packs");
        File pack = new File(packsFolder, requested);
        if (!pack.exists()) {
            File found = findCaseInsensitivePack(packsFolder, requested);
            if (found != null) {
                pack = found;
            }
        }

        if (!pack.exists()) {
            Iris.service(StudioSVC.class).downloadSearch(new VolmitSender(Bukkit.getConsoleSender(), Iris.instance.getTag()), requested, false, false);
            File found = findCaseInsensitivePack(packsFolder, requested);
            if (found != null) {
                pack = found;
            }
        }

        if (!pack.exists()) {
            return null;
        }

        IrisData data = IrisData.get(pack);
        IrisDimension resolved = data.getDimensionLoader().load(requested, false);
        if (resolved != null) {
            return resolved;
        }

        String packName = pack.getName();
        if (!packName.equals(requested)) {
            resolved = data.getDimensionLoader().load(packName, false);
            if (resolved != null) {
                return resolved;
            }
        }

        for (String key : data.getDimensionLoader().getPossibleKeys()) {
            if (!key.equalsIgnoreCase(requested) && !key.equalsIgnoreCase(packName)) {
                continue;
            }

            resolved = data.getDimensionLoader().load(key, false);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    private static File findCaseInsensitivePack(File packsFolder, String requested) {
        File[] children = packsFolder.listFiles();
        if (children == null) {
            return null;
        }

        for (File child : children) {
            if (child.isDirectory() && child.getName().equalsIgnoreCase(requested)) {
                return child;
            }
        }

        return null;
    }

    /**
     * Create a world with plenty of options
     *
     * @return the creator builder
     */
    public static IrisCreator createWorld() {
        return new IrisCreator();
    }

    /**
     * Checks if the given world is an Iris World (same as access(world) != null)
     *
     * @param world the world
     * @return true if it is an Iris Access world
     */
    public static boolean isIrisWorld(World world) {
        if (world == null) {
            return false;
        }

        if (world.getGenerator() instanceof PlatformChunkGenerator f) {
            f.touch(world);
            return true;
        }

        return false;
    }

    public static boolean isIrisStudioWorld(World world) {
        return isIrisWorld(world) && access(world).isStudio();
    }

    /**
     * Get the Iris generator for the given world
     *
     * @param world the given world
     * @return the IrisAccess or null if it's not an Iris World
     */
    public static PlatformChunkGenerator access(World world) {
        if (world == null) {
            return null;
        }

        if (isIrisWorld(world)) {
            return ((PlatformChunkGenerator) world.getGenerator());
        }

        StudioSVC studioService = Iris.service(StudioSVC.class);
        if (studioService != null && studioService.isProjectOpen()) {
            IrisProject activeProject = studioService.getActiveProject();
            if (activeProject != null) {
                PlatformChunkGenerator activeProvider = activeProject.getActiveProvider();
                if (activeProvider != null) {
                    World activeWorld = activeProvider.getTarget().getWorld().realWorld();
                    if (activeWorld != null && activeWorld.getName().equals(world.getName())) {
                        activeProvider.touch(world);
                        return activeProvider;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Start a pregenerator task
     *
     * @param task   the scheduled task
     * @param method the method to execute the task
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, PregeneratorMethod method, Engine engine) {
        return pregenerate(task, method, engine, IrisSettings.get().getPregen().useCacheByDefault);
    }

    /**
     * Start a pregenerator task
     *
     * @param task   the scheduled task
     * @param method the method to execute the task
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, PregeneratorMethod method, Engine engine, boolean cached) {
        boolean useCachedWrapper = cached && engine != null && !J.isFolia();
        return new PregeneratorJob(task, useCachedWrapper ? new CachedPregenMethod(method, engine.getWorld().name()) : method, engine);
    }

    /**
     * Start a pregenerator task. If the supplied generator is headless, headless mode is used,
     * otherwise Hybrid mode is used.
     *
     * @param task the scheduled task
     * @param gen  the Iris Generator
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, PlatformChunkGenerator gen) {
        return pregenerate(task, new HybridPregenMethod(gen.getEngine().getWorld().realWorld(),
                IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism())), gen.getEngine());
    }

    /**
     * Start a pregenerator task. If the supplied generator is headless, headless mode is used,
     * otherwise Hybrid mode is used.
     *
     * @param task  the scheduled task
     * @param world the World
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, World world) {
        if (isIrisWorld(world)) {
            return pregenerate(task, access(world));
        }

        return pregenerate(task, new HybridPregenMethod(world, IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism())), null);
    }

    /**
     * Evacuate all players from the world into literally any other world.
     * If there are no other worlds, kick them! Not the best but what's mine is mine sometimes...
     *
     * @param world the world to evac
     */
    public static boolean evacuate(World world) {
        if (world == null || isServerStopping()) {
            return false;
        }

        for (World i : Bukkit.getWorlds()) {
            if (!i.getName().equals(world.getName())) {
                for (Player j : new ArrayList<>(world.getPlayers())) {
                    new VolmitSender(j, Iris.instance.getTag()).sendMessage("You have been evacuated from this world.");
                    Location target = i.getSpawnLocation();
                    Runnable teleportTask = () -> teleportAsyncSafely(j, target);
                    if (!J.runEntity(j, teleportTask)) {
                        teleportTask.run();
                    }
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Evacuate all players from the world
     *
     * @param world the world to leave
     * @param m     the message
     * @return true if it was evacuated.
     */
    public static boolean evacuate(World world, String m) {
        if (world == null || isServerStopping()) {
            return false;
        }

        for (World i : Bukkit.getWorlds()) {
            if (!i.getName().equals(world.getName())) {
                for (Player j : new ArrayList<>(world.getPlayers())) {
                    new VolmitSender(j, Iris.instance.getTag()).sendMessage("You have been evacuated from this world. " + m);
                    Location target = i.getSpawnLocation();
                    Runnable teleportTask = () -> teleportAsyncSafely(j, target);
                    if (!J.runEntity(j, teleportTask)) {
                        teleportTask.run();
                    }
                }
                return true;
            }
        }

        return false;
    }

    public static boolean isStudio(World i) {
        if (!isIrisWorld(i)) {
            return false;
        }

        PlatformChunkGenerator generator = access(i);
        return generator != null && generator.isStudio();
    }

    private static void teleportAsyncSafely(Player player, Location target) {
        if (player == null || target == null || isServerStopping()) {
            return;
        }

        try {
            CompletableFuture<Boolean> teleportFuture = PaperLib.teleportAsync(player, target);
            if (teleportFuture != null) {
                teleportFuture.exceptionally(throwable -> {
                    if (!isServerStopping()) {
                        Iris.reportError(throwable);
                    }
                    return false;
                });
            }
        } catch (Throwable throwable) {
            if (!isServerStopping()) {
                Iris.reportError(throwable);
            }
        }
    }

    public static boolean isServerStopping() {
        Method method = BUKKIT_IS_STOPPING_METHOD;
        if (method != null) {
            try {
                Object value = method.invoke(null);
                if (value instanceof Boolean) {
                    return (Boolean) value;
                }
            } catch (Throwable ignored) {
            }
        }

        Iris iris = Iris.instance;
        return iris == null || !iris.isEnabled();
    }

    private static Method resolveBukkitIsStoppingMethod() {
        try {
            return Bukkit.class.getMethod("isStopping");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void beginWorldMaintenance(World world, String reason) {
        beginWorldMaintenance(world, reason, false);
    }

    public static void beginWorldMaintenance(World world, String reason, boolean bypassMantleStages) {
        if (world == null) {
            return;
        }

        String name = world.getName();
        int depth = worldMaintenanceDepth.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
        if (bypassMantleStages) {
            worldMaintenanceMantleBypassDepth.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
        }
        Iris.info("World maintenance enter: " + name + " reason=" + reason + " depth=" + depth + " bypassMantle=" + bypassMantleStages);
    }

    public static void endWorldMaintenance(World world, String reason) {
        if (world == null) {
            return;
        }

        String name = world.getName();
        AtomicInteger depthCounter = worldMaintenanceDepth.get(name);
        if (depthCounter == null) {
            return;
        }

        int depth = depthCounter.decrementAndGet();
        if (depth <= 0) {
            worldMaintenanceDepth.remove(name, depthCounter);
            depth = 0;
        }

        AtomicInteger bypassCounter = worldMaintenanceMantleBypassDepth.get(name);
        int bypassDepth = 0;
        if (bypassCounter != null) {
            bypassDepth = bypassCounter.decrementAndGet();
            if (bypassDepth <= 0) {
                worldMaintenanceMantleBypassDepth.remove(name, bypassCounter);
                bypassDepth = 0;
            }
        }

        Iris.info("World maintenance exit: " + name + " reason=" + reason + " depth=" + depth + " bypassMantleDepth=" + bypassDepth);
    }

    public static boolean isWorldMaintenanceActive(World world) {
        if (world == null) {
            return false;
        }

        AtomicInteger counter = worldMaintenanceDepth.get(world.getName());
        return counter != null && counter.get() > 0;
    }

    public static boolean isWorldMaintenanceBypassingMantleStages(World world) {
        if (world == null) {
            return false;
        }

        AtomicInteger counter = worldMaintenanceMantleBypassDepth.get(world.getName());
        return counter != null && counter.get() > 0;
    }

    public static void retainMantleDataForSlice(String className) {
        toolbeltConfiguration.put("retain.mantle." + className, Boolean.TRUE);
    }

    public static boolean isRetainingMantleDataForSlice(String className) {
        return !toolbeltConfiguration.isEmpty() && toolbeltConfiguration.get("retain.mantle." + className) == Boolean.TRUE;
    }

    public static <T> T getMantleData(World world, int x, int y, int z, Class<T> of) {
        PlatformChunkGenerator e = access(world);
        if (e == null) {
            return null;
        }
        return e.getEngine().getMantle().getMantle().get(x, y - world.getMinHeight(), z, of);
    }

    public static <T> void deleteMantleData(World world, int x, int y, int z, Class<T> of) {
        PlatformChunkGenerator e = access(world);
        if (e == null) {
            return;
        }
        e.getEngine().getMantle().getMantle().remove(x, y - world.getMinHeight(), z, of);
    }

    public static boolean removeWorld(World world) throws IOException {
        return IrisCreator.removeFromBukkitYml(world.getName());
    }
}
