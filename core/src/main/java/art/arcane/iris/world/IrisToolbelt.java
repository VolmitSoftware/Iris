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

package art.arcane.iris.world;

import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.world.pregen.PregenPerformanceProfile;
import art.arcane.iris.world.pregen.PregenTask;
import art.arcane.iris.world.pregen.PregeneratorMethod;
import art.arcane.iris.world.pregen.PregenCache;
import art.arcane.iris.world.pregen.PregenSavedChunkStatus;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.studio.workspace.IrisProject;
import art.arcane.iris.world.pregen.CachedPregenMethod;
import art.arcane.iris.world.pregen.HybridPregenMethod;
import art.arcane.iris.generation.runtime.GlobalCacheSVC;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineLifecycleTasks;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.world.task.J;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
/**
 * Something you really want to wear if working on Iris. Shit gets pretty hectic down there.
 * Hope you packed snacks & road sodas.
 */
public class IrisToolbelt {
    @ApiStatus.Internal
    public static Map<String, Boolean> toolbeltConfiguration = new HashMap<>();

    /**
     * Finds an installed dimension or returns null.
     *
     * @param dimension the dimension id such as overworld or flat
     * @return the IrisDimension or null
     */
    public static IrisDimension getDimension(String dimension) {
        if (dimension == null) {
            return null;
        }

        PackReference reference = parsePackReference(dimension);
        if (reference == null) {
            return null;
        }

        File packsFolder = IrisPlatforms.get().packsFolder();
        File pack = PackDirectoryResolver.resolveExisting(packsFolder, reference.pack());
        if (pack == null) {
            File found = findCaseInsensitivePack(packsFolder, reference.pack());
            if (found != null) {
                pack = found;
            }
        }

        if (pack == null) {
            IrisLogging.warn("Iris pack '" + reference.pack()
                    + "' is not installed. Install it with " + PackDownloader.downloadCommandFor(reference.pack())
                    + " and restart the server.");
            return null;
        }

        IrisData data = IrisData.get(pack);
        IrisDimension resolved = data.getDimensionLoader().load(reference.dimension(), false);
        if (resolved != null) {
            return resolved;
        }
        if (reference.explicitDimension()) {
            return null;
        }

        String packName = pack.getName();
        if (!packName.equals(reference.pack())) {
            resolved = data.getDimensionLoader().load(packName, false);
            if (resolved != null) {
                return resolved;
            }
        }

        for (String key : data.getDimensionLoader().getPossibleKeys()) {
            if (!key.equalsIgnoreCase(reference.pack()) && !key.equalsIgnoreCase(packName)) {
                continue;
            }

            resolved = data.getDimensionLoader().load(key, false);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    static PackReference parsePackReference(String value) {
        if (value == null) {
            return null;
        }
        String requested = value.trim();
        if (requested.isEmpty()) {
            return null;
        }
        int separator = requested.indexOf(':');
        if (separator < 0) {
            if (!isSafePackDescriptor(requested)) {
                return null;
            }
            return new PackReference(requested, installedPackName(requested), false);
        }
        String pack = requested.substring(0, separator).trim();
        String dimension = requested.substring(separator + 1).trim();
        if (!isSafePackDescriptor(pack)
                || !isSafeDimensionKey(dimension)
                || pack.equalsIgnoreCase(dimension)) {
            return null;
        }
        return new PackReference(pack, dimension, true);
    }

    private static boolean isSafePackDescriptor(String value) {
        String[] segments = value.split("/", -1);
        if (segments.length < 1 || segments.length > 3) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty()
                    || segment.equals(".")
                    || segment.equals("..")
                    || segment.startsWith(".")
                    || !segment.matches("[A-Za-z0-9_-]+")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeDimensionKey(String value) {
        if (value.isEmpty() || value.length() > 256) {
            return false;
        }
        String[] segments = value.split("/", -1);
        if (segments.length > 16) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty()
                    || segment.equals(".")
                    || segment.equals("..")
                    || segment.startsWith(".")
                    || !segment.matches("[A-Za-z0-9_-]+")) {
                return false;
            }
        }
        return true;
    }

    private static String installedPackName(String descriptor) {
        String[] segments = descriptor.split("/");
        return segments.length > 1 ? segments[1] : segments[0];
    }

    private static File findCaseInsensitivePack(File packsFolder, String requested) {
        for (File child : PackDirectoryResolver.listVisiblePackDirectories(packsFolder)) {
            if (child.getName().equalsIgnoreCase(requested)) {
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
            if (f instanceof BukkitChunkGenerator bukkit) {
                bukkit.touch(world);
            }
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

        StudioSVC studioService = IrisServices.getOrNull(StudioSVC.class);
        if (studioService != null && studioService.isProjectOpen()) {
            IrisProject activeProject = studioService.getActiveProject();
            if (activeProject != null) {
                PlatformChunkGenerator activeProvider = activeProject.getActiveProvider();
                if (activeProvider != null) {
                    World activeWorld = BukkitWorldBinding.world(activeProvider.getTarget().getWorld());
                    if (activeWorld != null && WorldIdentity.key(activeWorld).equals(WorldIdentity.key(world))) {
                        if (activeProvider instanceof BukkitChunkGenerator bukkit) {
                            bukkit.touch(world);
                        }
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
        return pregenerate(task, method, engine, true);
    }

    /**
     * Start a pregenerator task
     *
     * @param task   the scheduled task
     * @param method the method to execute the task
     * @return the pregenerator job (already started)
     */
    public static PregeneratorJob pregenerate(PregenTask task, PregeneratorMethod method, Engine engine, boolean cached) {
        // Match the modded adapter's contract: a running job is a rejection, never a silent
        // kill whose teardown (60s drain + 120s flush) overlaps the new job's generation.
        // Callers that genuinely want replacement must stop the old job first
        // (PregeneratorJob.shutdownAndWait).
        if (PregeneratorJob.getInstance() != null) {
            throw new IllegalStateException("An Iris pregeneration job is already running; stop it first with /iris pregen stop.");
        }
        boolean useCachedWrapper = false;
        if (cached && engine != null) {
            IrisRuntimeSchedulerMode runtimeSchedulerMode = IrisRuntimeSchedulerMode.resolve(IrisSettings.get().getPregen());
            useCachedWrapper = runtimeSchedulerMode != IrisRuntimeSchedulerMode.FOLIA;
        }
        PregeneratorMethod activeMethod = useCachedWrapper
                ? new CachedPregenMethod(new CachedPregenMethod.Configuration(method, resolvePregenCache(engine.getWorld()), task,
                        PregenSavedChunkStatus.fromWorld(engine.getWorld().worldFolder().toPath())))
                : method;
        return new PregeneratorJob(new PregeneratorJob.Configuration(
                task,
                activeMethod,
                engine,
                () -> applyPregenPerformanceProfile(engine)));
    }

    private static PregenCache resolvePregenCache(IrisWorld world) {
        String worldIdentity = world.identity();
        if (worldIdentity == null) {
            throw new IllegalStateException("Pregeneration requires a namespaced world identity.");
        }
        PregenCache cache = IrisServices.get(GlobalCacheSVC.class).get(worldIdentity);
        if (cache == null) {
            IrisLogging.debug("Could not find existing cache for " + worldIdentity + " creating fallback");
            cache = GlobalCacheSVC.createDefault(worldIdentity);
        }
        return cache;
    }

    public static boolean applyPregenPerformanceProfile() {
        return PregenPerformanceProfile.apply();
    }

    public static void applyPregenPerformanceProfile(Engine engine) {
        PlatformChunkGenerator generator = resolvePlatformGenerator(engine);
        if (generator != null) {
            PregenPerformanceProfile.applyToGenerator(generator);
            return;
        }
        PregenPerformanceProfile.apply(engine);
    }

    private static PlatformChunkGenerator resolvePlatformGenerator(Engine engine) {
        if (engine == null) {
            return null;
        }
        World world = BukkitWorldBinding.world(engine.getWorld());
        if (world == null) {
            return null;
        }
        if (!(world.getGenerator() instanceof PlatformChunkGenerator generator)) {
            throw new IllegalStateException("Live Iris engine is not bound to its platform chunk generator.");
        }
        if (generator.getEngine() != engine) {
            throw new IllegalStateException("Live Iris engine does not match its platform chunk generator runtime.");
        }
        return generator;
    }

    public static boolean supportsStrictSerialPregeneration() {
        return PaperLib.isPaper();
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
        World world = BukkitWorldBinding.world(gen.getEngine().getWorld());
        int threads = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism());
        PregeneratorMethod method = new HybridPregenMethod(world, threads);
        return pregenerate(task, method, gen.getEngine());
    }

    public static PregeneratorJob pregenerateSerial(PregenTask task, PlatformChunkGenerator gen) {
        World world = BukkitWorldBinding.world(gen.getEngine().getWorld());
        PregeneratorMethod method = HybridPregenMethod.strictSerial(world);
        return pregenerate(task, method, gen.getEngine());
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

        int threads = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism());
        PregeneratorMethod method = new HybridPregenMethod(world, threads);
        return pregenerate(task, method, null);
    }

    public static PregeneratorJob pregenerateSerial(PregenTask task, World world) {
        if (isIrisWorld(world)) {
            return pregenerateSerial(task, access(world));
        }

        PregeneratorMethod method = HybridPregenMethod.strictSerial(world);
        return pregenerate(task, method, null);
    }

    /**
     * Evacuate all players from the world into literally any other world.
     * If there are no other worlds, kick them! Not the best but what's mine is mine sometimes...
     *
     * @param world the world to evac
     */
    public static boolean evacuate(World world) {
        return beginEvacuation(evacuateAsync(world));
    }

    public static CompletableFuture<Boolean> evacuateAsync(World world) {
        return evacuateAsync(world, null, false);
    }

    /**
     * Evacuate all players from the world
     *
     * @param world the world to leave
     * @param m     the message
     * @return true if it was evacuated.
     */
    public static boolean evacuate(World world, String m) {
        return beginEvacuation(evacuateAsync(world, m));
    }

    public static CompletableFuture<Boolean> evacuateAsync(World world, String message) {
        return evacuateAsync(world, message, true);
    }

    public static boolean isStudio(World i) {
        if (!isIrisWorld(i)) {
            return false;
        }

        PlatformChunkGenerator generator = access(i);
        return generator != null && generator.isStudio();
    }

    static CompletableFuture<Boolean> settleEvacuations(List<CompletableFuture<Boolean>> evacuations) {
        List<CompletableFuture<Boolean>> pending = List.copyOf(evacuations);
        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        return false;
                    }
                    for (CompletableFuture<Boolean> evacuation : pending) {
                        if (!Boolean.TRUE.equals(evacuation.getNow(false))) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    private static CompletableFuture<Boolean> evacuateAsync(World world, String message, boolean customMessage) {
        if (world == null || isServerStopping()) {
            return CompletableFuture.completedFuture(false);
        }

        ArrayList<Player> players = new ArrayList<>(world.getPlayers());
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        World targetWorld = null;
        for (World candidate : Bukkit.getWorlds()) {
            if (!WorldIdentity.key(candidate).equals(WorldIdentity.key(world))) {
                targetWorld = candidate;
                break;
            }
        }
        if (targetWorld == null) {
            return CompletableFuture.completedFuture(false);
        }

        Location target = targetWorld.getSpawnLocation();
        ArrayList<CompletableFuture<Boolean>> evacuations = new ArrayList<>(players.size());
        for (Player player : players) {
            CompletableFuture<Boolean> evacuation = new CompletableFuture<>();
            Runnable teleportTask = () -> {
                try {
                    if (customMessage) {
                        new VolmitSender(player, BukkitPlatform.volmitPlugin().getTag()).sendMessage(IrisLanguage.text(
                                BukkitRuntimeMessages.IRIS_TOOLBELT_YOU_HAVE_BEEN_EVACUATED_FROM_THIS_WORLD_2,
                                MessageArgument.untrusted("m", String.valueOf(message))));
                    } else {
                        new VolmitSender(player, BukkitPlatform.volmitPlugin().getTag()).sendMessage(IrisLanguage.text(
                                BukkitRuntimeMessages.IRIS_TOOLBELT_YOU_HAVE_BEEN_EVACUATED_FROM_THIS_WORLD));
                    }
                    teleportAsyncSafely(player, target).whenComplete((teleported, throwable) -> {
                        if (throwable == null) {
                            evacuation.complete(Boolean.TRUE.equals(teleported));
                        } else {
                            evacuation.completeExceptionally(throwable);
                        }
                    });
                } catch (Throwable failure) {
                    evacuation.completeExceptionally(failure);
                }
            };
            try {
                if (!J.runEntity(player, teleportTask)) {
                    teleportTask.run();
                }
            } catch (Throwable failure) {
                evacuation.completeExceptionally(failure);
            }
            evacuations.add(evacuation);
        }
        return settleEvacuations(evacuations);
    }

    private static boolean beginEvacuation(CompletableFuture<Boolean> evacuation) {
        if (evacuation.isDone()) {
            try {
                return Boolean.TRUE.equals(evacuation.getNow(false));
            } catch (Throwable failure) {
                if (!isServerStopping()) {
                    IrisLogging.reportError(failure);
                }
                return false;
            }
        }
        evacuation.exceptionally(throwable -> {
            if (!isServerStopping()) {
                IrisLogging.reportError(throwable);
            }
            return false;
        });
        return true;
    }

    private static CompletableFuture<Boolean> teleportAsyncSafely(Player player, Location target) {
        if (player == null || target == null || isServerStopping()) {
            return CompletableFuture.completedFuture(false);
        }

        try {
            CompletableFuture<Boolean> teleportFuture = PaperLib.teleportAsync(player, target);
            if (teleportFuture == null) {
                return CompletableFuture.completedFuture(false);
            }
            return teleportFuture.exceptionally(throwable -> {
                if (!isServerStopping()) {
                    IrisLogging.reportError(throwable);
                }
                return false;
            });
        } catch (Throwable throwable) {
            if (!isServerStopping()) {
                IrisLogging.reportError(throwable);
            }
            return CompletableFuture.completedFuture(false);
        }
    }

    public static boolean isServerStopping() {
        return INMS.isBound() && INMS.get().isServerStopping();
    }

    public static void beginWorldMaintenance(World world, String reason) {
        beginWorldMaintenance(world, reason, false);
    }

    public static void beginWorldMaintenance(World world, String reason, boolean bypassMantleStages) {
        if (world == null) {
            return;
        }

        beginWorldMaintenance(WorldIdentity.serialize(world), reason, bypassMantleStages);
    }

    public static void beginWorldMaintenance(String worldName, String reason) {
        beginWorldMaintenance(worldName, reason, false);
    }

    public static void beginWorldMaintenance(String worldName, String reason, boolean bypassMantleStages) {
        WorldMaintenance.beginWorldMaintenance(worldName, reason, bypassMantleStages);
    }

    public static void endWorldMaintenance(World world, String reason) {
        endWorldMaintenance(world, reason, false);
    }

    public static void endWorldMaintenance(World world, String reason, boolean bypassMantleStages) {
        if (world == null) {
            return;
        }

        endWorldMaintenance(WorldIdentity.serialize(world), reason, bypassMantleStages);
    }

    public static void endWorldMaintenance(String worldName, String reason) {
        WorldMaintenance.endWorldMaintenance(worldName, reason);
    }

    public static void endWorldMaintenance(String worldName, String reason, boolean bypassMantleStages) {
        WorldMaintenance.endWorldMaintenance(worldName, reason, bypassMantleStages);
    }

    public static boolean isWorldMaintenanceActive(World world) {
        return world != null && isWorldMaintenanceActive(WorldIdentity.serialize(world));
    }

    public static boolean isWorldMaintenanceActive(String worldName) {
        return WorldMaintenance.isWorldMaintenanceActive(worldName);
    }

    public static boolean isWorldMaintenanceBypassingMantleStages(World world) {
        return world != null && isWorldMaintenanceBypassingMantleStages(WorldIdentity.serialize(world));
    }

    public static boolean isWorldMaintenanceBypassingMantleStages(String worldName) {
        return WorldMaintenance.isWorldMaintenanceBypassingMantleStages(worldName);
    }

    public static void retainMantleDataForSlice(String className) {
        WorldMaintenance.retainMantleDataForSlice(className);
    }

    public static boolean isRetainingMantleDataForSlice(String className) {
        return WorldMaintenance.isRetainingMantleDataForSlice(className);
    }

    public static <T> T getMantleData(World world, int x, int y, int z, Class<T> of) {
        PlatformChunkGenerator e = access(world);
        if (e == null) {
            return null;
        }
        // Lease-gated so the engine shutdown drain covers these public accessors; a mantle
        // mid-close refuses the lease instead of racing the region flush.
        return EngineLifecycleTasks.call(e.getEngine(), "api_mantle_get",
                () -> e.getEngine().getMantle().getMantle().get(x, y - world.getMinHeight(), z, of), null);
    }

    public static <T> void deleteMantleData(World world, int x, int y, int z, Class<T> of) {
        PlatformChunkGenerator e = access(world);
        if (e == null) {
            return;
        }
        EngineLifecycleTasks.run(e.getEngine(), "api_mantle_delete",
                () -> e.getEngine().getMantle().getMantle().remove(x, y - world.getMinHeight(), z, of));
    }

    public static <T> void setMantleData(World world, int x, int y, int z, T data) {
        PlatformChunkGenerator e = access(world);
        if (e == null || data == null) {
            return;
        }
        EngineLifecycleTasks.run(e.getEngine(), "api_mantle_set",
                () -> e.getEngine().getMantle().getMantle().set(x, y - world.getMinHeight(), z, data));
    }

    public static boolean removeWorld(World world) throws IOException {
        return IrisCreator.removeFromBukkitYml(WorldIdentity.key(world));
    }

    record PackReference(String pack, String dimension, boolean explicitDimension) {
    }
}
