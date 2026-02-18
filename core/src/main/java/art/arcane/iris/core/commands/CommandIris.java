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

package art.arcane.iris.core.commands;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.IrisWorlds;
import art.arcane.iris.core.link.FoliaWorldsLink;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.director.DirectorContext;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.iris.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.iris.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.iris.util.format.C;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.iris.util.parallel.SyncExecutor;
import art.arcane.iris.util.misc.ServerProperties;
import art.arcane.iris.util.misc.RegenRuntime;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.iris.util.matter.TileWrapper;
import art.arcane.iris.util.plugin.VolmitSender;
import art.arcane.iris.util.scheduling.J;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;

import java.io.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.iris.core.service.EditSVC.deletingWorld;
import static art.arcane.iris.util.misc.ServerProperties.BUKKIT_YML;
import static org.bukkit.Bukkit.getServer;

@Director(name = "iris", aliases = {"ir", "irs"}, description = "Basic Command")
public class CommandIris implements DirectorExecutor {
    private static final long REGEN_HEARTBEAT_MS = 5000L;
    private static final int REGEN_MAX_ATTEMPTS = 2;
    private static final int REGEN_STACK_LIMIT = 20;
    private static final int REGEN_STALL_DUMP_HEARTBEATS = 3;
    private static final int REGEN_STALL_ABORT_HEARTBEATS = 24;
    private static final long REGEN_MAX_RESET_CHUNKS = 65536L;
    private static final int REGEN_RESET_PROGRESS_STEP = 128;
    private static final long REGEN_RESET_DELETE_ABORT_MS = 60000L;
    private static final int REGEN_PROGRESS_BAR_WIDTH = 44;
    private static final long REGEN_PROGRESS_UPDATE_MS = 200L;
    private static final int REGEN_ACTION_PULSE_TICKS = 20;
    private static final int REGEN_DISPLAY_FINAL_TICKS = 60;
    private CommandUpdater updater;
    private CommandStudio studio;
    private CommandPregen pregen;
    private CommandSettings settings;
    private CommandObject object;
    private CommandJigsaw jigsaw;
    private CommandWhat what;
    private CommandEdit edit;
    private CommandFind find;
    private CommandDeveloper developer;
    public static boolean worldCreation = false;
    private static final AtomicReference<Thread> mainWorld = new AtomicReference<>();
    String WorldEngine;
    String worldNameToCheck = "YourWorldName";
    VolmitSender sender = Iris.getSender();

    @Director(description = "Create a new world", aliases = {"+", "c"})
    public void create(
            @Param(aliases = "world-name", description = "The name of the world to create")
            String name,
            @Param(
                    aliases = {"dimension", "pack"},
                    description = "The dimension/pack to create the world with",
                    defaultValue = "default",
                    customHandler = PackDimensionTypeHandler.class
            )
            String type,
            @Param(description = "The seed to generate the world with", defaultValue = "1337")
            long seed,
            @Param(aliases = "main-world", description = "Whether or not to automatically use this world as the main world", defaultValue = "false")
            boolean main,
            @Param(aliases = {"remove-others", "removeothers"}, description = "When main-world is true, remove other Iris worlds from bukkit.yml and queue deletion on startup", defaultValue = "false")
            boolean removeOthers,
            @Param(aliases = {"remove-worlds", "removeworlds"}, description = "Comma-separated world names to remove from Iris control and delete on next startup (main-world only)", defaultValue = "none")
            String removeWorlds
    ) {
        if (name.equalsIgnoreCase("iris")) {
            sender().sendMessage(C.RED + "You cannot use the world name \"iris\" for creating worlds as Iris uses this directory for studio worlds.");
            sender().sendMessage(C.RED + "May we suggest the name \"IrisWorld\" instead?");
            return;
        }

        if (name.equalsIgnoreCase("benchmark")) {
            sender().sendMessage(C.RED + "You cannot use the world name \"benchmark\" for creating worlds as Iris uses this directory for Benchmarking Packs.");
            sender().sendMessage(C.RED + "May we suggest the name \"IrisWorld\" instead?");
            return;
        }

        if (new File(Bukkit.getWorldContainer(), name).exists()) {
            sender().sendMessage(C.RED + "That folder already exists!");
            return;
        }

        String resolvedType = type.equalsIgnoreCase("default")
                ? IrisSettings.get().getGenerator().getDefaultWorldType()
                : type;

        IrisDimension dimension = IrisToolbelt.getDimension(resolvedType);
        if (dimension == null) {
            sender().sendMessage(C.RED + "Could not find or download dimension \"" + resolvedType + "\".");
            sender().sendMessage(C.YELLOW + "Try one of: overworld, vanilla, flat, theend");
            sender().sendMessage(C.YELLOW + "Or download manually: /iris download IrisDimensions/" + resolvedType);
            return;
        }

        if (!main && (removeOthers || hasExplicitCleanupWorlds(removeWorlds))) {
            sender().sendMessage(C.YELLOW + "remove-others/remove-worlds only apply when main-world=true. Ignoring cleanup options.");
            removeOthers = false;
            removeWorlds = "none";
        }

        if (J.isFolia()) {
            if (stageFoliaWorldCreation(name, dimension, seed, main, removeOthers, removeWorlds)) {
                sender().sendMessage(C.GREEN + "World staging completed. Restart the server to generate/load \"" + name + "\".");
            }
            return;
        }

        try {
            worldCreation = true;
            IrisToolbelt.createWorld()
                    .dimension(dimension.getLoadKey())
                    .name(name)
                    .seed(seed)
                    .sender(sender())
                    .studio(false)
                    .create();
            if (main) {
                Runtime.getRuntime().addShutdownHook(mainWorld.updateAndGet(old -> {
                    if (old != null) Runtime.getRuntime().removeShutdownHook(old);
                    return new Thread(() -> updateMainWorld(name));
                }));
            }
        } catch (Throwable e) {
            sender().sendMessage(C.RED + "Exception raised during creation. See the console for more details.");
            Iris.error("Exception raised during world creation: " + e.getMessage());
            Iris.reportError(e);
            worldCreation = false;
            return;
        }

        if (main && !applyMainWorldCleanup(name, removeOthers, removeWorlds)) {
            worldCreation = false;
            return;
        }

        worldCreation = false;
        sender().sendMessage(C.GREEN + "Successfully created your world!");
        if (main) sender().sendMessage(C.GREEN + "Your world will automatically be set as the main world when the server restarts.");
    }

    private boolean updateMainWorld(String newName) {
        try {
            File worlds = Bukkit.getWorldContainer();
            var data = ServerProperties.DATA;
            try (var in = new FileInputStream(ServerProperties.SERVER_PROPERTIES)) {
                data.load(in);
            }

            File oldWorldFolder = new File(worlds, ServerProperties.LEVEL_NAME);
            File newWorldFolder = new File(worlds, newName);
            if (!newWorldFolder.exists() && !newWorldFolder.mkdirs()) {
                Iris.warn("Could not create target main world folder: " + newWorldFolder.getAbsolutePath());
            }

            for (String sub : List.of("datapacks", "playerdata", "advancements", "stats")) {
                File source = new File(oldWorldFolder, sub);
                if (!source.exists()) {
                    continue;
                }

                IO.copyDirectory(source.toPath(), new File(newWorldFolder, sub).toPath());
            }

            data.setProperty("level-name", newName);
            try (var out = new FileOutputStream(ServerProperties.SERVER_PROPERTIES)) {
                data.store(out, null);
            }
            return true;
        } catch (Throwable e) {
            Iris.error("Failed to update server.properties main world to \"" + newName + "\"");
            Iris.reportError(e);
            return false;
        }
    }

    private boolean stageFoliaWorldCreation(String name, IrisDimension dimension, long seed, boolean main, boolean removeOthers, String removeWorlds) {
        sender().sendMessage(C.YELLOW + "Runtime world creation is disabled on Folia.");
        sender().sendMessage(C.YELLOW + "Preparing world files and bukkit.yml for next startup...");

        File worldFolder = new File(Bukkit.getWorldContainer(), name);
        IrisDimension installed = Iris.service(StudioSVC.class).installIntoWorld(sender(), dimension.getLoadKey(), worldFolder);
        if (installed == null) {
            sender().sendMessage(C.RED + "Failed to stage world files for dimension \"" + dimension.getLoadKey() + "\".");
            return false;
        }

        if (!registerWorldInBukkitYml(name, dimension.getLoadKey(), seed)) {
            return false;
        }

        if (main) {
            if (updateMainWorld(name)) {
                sender().sendMessage(C.GREEN + "Updated server.properties level-name to \"" + name + "\".");
            } else {
                sender().sendMessage(C.RED + "World was staged, but failed to update server.properties main world.");
                return false;
            }

            if (!applyMainWorldCleanup(name, removeOthers, removeWorlds)) {
                sender().sendMessage(C.RED + "World was staged, but failed to apply main-world cleanup options.");
                return false;
            }
        }

        sender().sendMessage(C.GREEN + "Staged Iris world \"" + name + "\" with generator Iris:" + dimension.getLoadKey() + " and seed " + seed + ".");
        if (main) {
            sender().sendMessage(C.GREEN + "This world is now configured as main for next restart.");
        }
        return true;
    }

    private boolean registerWorldInBukkitYml(String worldName, String dimension, Long seed) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(BUKKIT_YML);
        ConfigurationSection worlds = yml.getConfigurationSection("worlds");
        if (worlds == null) {
            worlds = yml.createSection("worlds");
        }
        ConfigurationSection worldSection = worlds.getConfigurationSection(worldName);
        if (worldSection == null) {
            worldSection = worlds.createSection(worldName);
        }

        String generator = "Iris:" + dimension;
        worldSection.set("generator", generator);
        if (seed != null) {
            worldSection.set("seed", seed);
        }

        try {
            yml.save(BUKKIT_YML);
            Iris.info("Registered \"" + worldName + "\" in bukkit.yml");
            return true;
        } catch (IOException e) {
            sender().sendMessage(C.RED + "Failed to update bukkit.yml: " + e.getMessage());
            Iris.error("Failed to update bukkit.yml!");
            Iris.reportError(e);
            return false;
        }
    }

    private boolean applyMainWorldCleanup(String mainWorld, boolean removeOthers, String removeWorlds) {
        Set<String> targets = resolveCleanupTargets(mainWorld, removeOthers, removeWorlds);
        if (targets.isEmpty()) {
            return true;
        }

        sender().sendMessage(C.YELLOW + "Applying main-world cleanup for " + targets.size() + " world(s).");

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(BUKKIT_YML);
        ConfigurationSection worlds = yml.getConfigurationSection("worlds");

        Set<String> removedFromBukkit = new LinkedHashSet<>();
        Set<String> notRemoved = new LinkedHashSet<>();
        for (String target : targets) {
            String key = findWorldKeyIgnoreCase(worlds, target);
            if (key == null) {
                notRemoved.add(target);
                continue;
            }

            String generator = worlds.getString(key + ".generator");
            if (generator == null || !(generator.equalsIgnoreCase("iris") || generator.startsWith("Iris:"))) {
                notRemoved.add(key);
                continue;
            }

            worlds.set(key, null);
            removedFromBukkit.add(key);
        }

        try {
            if (worlds != null && worlds.getKeys(false).isEmpty()) {
                yml.set("worlds", null);
            }

            if (!removedFromBukkit.isEmpty()) {
                yml.save(BUKKIT_YML);
            }
        } catch (IOException e) {
            sender().sendMessage(C.RED + "Failed to update bukkit.yml while applying cleanup: " + e.getMessage());
            Iris.reportError(e);
            return false;
        }

        try {
            int queued = Iris.queueWorldDeletionOnStartup(targets);
            if (queued > 0) {
                sender().sendMessage(C.GREEN + "Queued " + queued + " world folder(s) for deletion on next startup.");
            } else {
                sender().sendMessage(C.YELLOW + "Cleanup queue already contained the requested world folder(s).");
            }
        } catch (IOException e) {
            sender().sendMessage(C.RED + "Failed to queue startup world deletions: " + e.getMessage());
            Iris.reportError(e);
            return false;
        }

        if (!removedFromBukkit.isEmpty()) {
            sender().sendMessage(C.GREEN + "Removed from Iris control in bukkit.yml: " + String.join(", ", removedFromBukkit));
        }

        if (!notRemoved.isEmpty()) {
            sender().sendMessage(C.YELLOW + "Skipped from bukkit.yml removal (not found or non-Iris generator): " + String.join(", ", notRemoved));
        }

        return true;
    }

    private Set<String> resolveCleanupTargets(String mainWorld, boolean removeOthers, String removeWorlds) {
        Set<String> targets = new LinkedHashSet<>();
        if (removeOthers) {
            IrisWorlds.readBukkitWorlds().keySet().stream()
                    .filter(world -> !world.equalsIgnoreCase(mainWorld))
                    .forEach(targets::add);
        }

        if (hasExplicitCleanupWorlds(removeWorlds)) {
            for (String raw : removeWorlds.split("[,;\\s]+")) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }

                if (raw.equalsIgnoreCase(mainWorld)) {
                    continue;
                }

                targets.add(raw.trim());
            }
        }

        return targets;
    }

    private static boolean hasExplicitCleanupWorlds(String removeWorlds) {
        if (removeWorlds == null) {
            return false;
        }

        String trimmed = removeWorlds.trim();
        return !trimmed.isEmpty() && !trimmed.equalsIgnoreCase("none");
    }

    private static String findWorldKeyIgnoreCase(ConfigurationSection worlds, String requested) {
        if (worlds == null || requested == null) {
            return null;
        }

        if (worlds.contains(requested)) {
            return requested;
        }

        for (String key : worlds.getKeys(false)) {
            if (key.equalsIgnoreCase(requested)) {
                return key;
            }
        }

        return null;
    }

    @Director(description = "Teleport to another world", aliases = {"tp"}, sync = true)
    public void teleport(
            @Param(description = "World to teleport to")
            World world,
            @Param(description = "Player to teleport", defaultValue = "---", customHandler = NullablePlayerHandler.class)
            Player player
    ) {
        if (player == null && sender().isPlayer())
            player = sender().player();

        final Player target = player;
        if (target == null) {
            sender().sendMessage(C.RED + "The specified player does not exist.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                target.teleport(world.getSpawnLocation());
                new VolmitSender(target).sendMessage(C.GREEN + "You have been teleported to " + world.getName() + ".");
            }
        }.runTask(Iris.instance);
    }

    @Director(description = "Print version information")
    public void version() {
        sender().sendMessage(C.GREEN + "Iris v" + Iris.instance.getDescription().getVersion() + " by Volmit Software");
    }

    /*
    /todo
    @Director(description = "Benchmark a pack", origin = DirectorOrigin.CONSOLE)
    public void packbenchmark(
            @Param(description = "Dimension to benchmark")
            IrisDimension type
    ) throws InterruptedException {

         BenchDimension = type.getLoadKey();

        IrisPackBenchmarking.runBenchmark();
    } */

    @Director(description = "Print world height information", origin = DirectorOrigin.PLAYER)
    public void height() {
        if (sender().isPlayer()) {
            sender().sendMessage(C.GREEN + "" + sender().player().getWorld().getMinHeight() + " to " + sender().player().getWorld().getMaxHeight());
            sender().sendMessage(C.GREEN + "Total Height: " + (sender().player().getWorld().getMaxHeight() - sender().player().getWorld().getMinHeight()));
        } else {
            World mainWorld = getServer().getWorlds().get(0);
            Iris.info(C.GREEN + "" + mainWorld.getMinHeight() + " to " + mainWorld.getMaxHeight());
            Iris.info(C.GREEN + "Total Height: " + (mainWorld.getMaxHeight() - mainWorld.getMinHeight()));
        }
    }

    @Director(description = "Check access of all worlds.", aliases = {"accesslist"})
    public void worlds() {
        KList<World> IrisWorlds = new KList<>();
        KList<World> BukkitWorlds = new KList<>();

        for (World w : Bukkit.getServer().getWorlds()) {
            try {
                Engine engine = IrisToolbelt.access(w).getEngine();
                if (engine != null) {
                    IrisWorlds.add(w);
                }
            } catch (Exception e) {
                BukkitWorlds.add(w);
            }
        }

        if (sender().isPlayer()) {
            sender().sendMessage(C.BLUE + "Iris Worlds: ");
            for (World IrisWorld : IrisWorlds.copy()) {
                sender().sendMessage(C.IRIS + "- " +IrisWorld.getName());
            }
            sender().sendMessage(C.GOLD + "Bukkit Worlds: ");
            for (World BukkitWorld : BukkitWorlds.copy()) {
                sender().sendMessage(C.GRAY + "- " +BukkitWorld.getName());
            }
        } else {
            Iris.info(C.BLUE + "Iris Worlds: ");
            for (World IrisWorld : IrisWorlds.copy()) {
                Iris.info(C.IRIS + "- " +IrisWorld.getName());
            }
            Iris.info(C.GOLD + "Bukkit Worlds: ");
            for (World BukkitWorld : BukkitWorlds.copy()) {
                Iris.info(C.GRAY + "- " +BukkitWorld.getName());
            }
            
        }
    }

    @Director(description = "Remove an Iris world", aliases = {"del", "rm", "delete"}, sync = true)
    public void remove(
            @Param(description = "The world to remove")
            World world,
            @Param(description = "Whether to also remove the folder (if set to false, just does not load the world)", defaultValue = "true")
            boolean delete
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }
        sender().sendMessage(C.GREEN + "Removing world: " + world.getName());

        if (!IrisToolbelt.evacuate(world)) {
            sender().sendMessage(C.RED + "Failed to evacuate world: " + world.getName());
            return;
        }

        if (!FoliaWorldsLink.get().unloadWorld(world, false)) {
            sender().sendMessage(C.RED + "Failed to unload world: " + world.getName());
            return;
        }

        try {
            if (IrisToolbelt.removeWorld(world)) {
                sender().sendMessage(C.GREEN + "Successfully removed " + world.getName() + " from bukkit.yml");
            } else {
                sender().sendMessage(C.YELLOW + "Looks like the world was already removed from bukkit.yml");
            }
        } catch (IOException e) {
            sender().sendMessage(C.RED + "Failed to save bukkit.yml because of " + e.getMessage());
            e.printStackTrace();
        }
        IrisToolbelt.evacuate(world, "Deleting world");
        deletingWorld = true;
        if (!delete) {
            deletingWorld = false;
            return;
        }
        VolmitSender sender = sender();
        J.a(() -> {
            int retries = 12;

            if (deleteDirectory(world.getWorldFolder())) {
                sender.sendMessage(C.GREEN + "Successfully removed world folder");
            } else {
                while(true){
                    if (deleteDirectory(world.getWorldFolder())){
                        sender.sendMessage(C.GREEN + "Successfully removed world folder");
                        break;
                    }
                    retries--;
                    if (retries == 0){
                        sender.sendMessage(C.RED + "Failed to remove world folder");
                        break;
                    }
                    J.sleep(3000);
                }
            }
            deletingWorld = false;
        });
    }

    public static boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDirectory(children[i]);
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    @Director(description = "Toggle debug")
    public void debug(
            @Param(name = "on", description = "Whether or not debug should be on", defaultValue = "other")
            Boolean on
    ) {
        boolean to = on == null ? !IrisSettings.get().getGeneral().isDebug() : on;
        IrisSettings.get().getGeneral().setDebug(to);
        IrisSettings.get().forceSave();
        sender().sendMessage(C.GREEN + "Set debug to: " + to);
    }

    //TODO fix pack trimming
    @Director(description = "Download a project.", aliases = "dl")
    public void download(
            @Param(name = "pack", description = "The pack to download", defaultValue = "overworld", aliases = "project")
            String pack,
            @Param(name = "branch", description = "The branch to download from", defaultValue = "main")
            String branch,
            //@Param(name = "trim", description = "Whether or not to download a trimmed version (do not enable when editing)", defaultValue = "false")
            //boolean trim,
            @Param(name = "overwrite", description = "Whether or not to overwrite the pack with the downloaded one", aliases = "force", defaultValue = "false")
            boolean overwrite
    ) {
        boolean trim = false;
        sender().sendMessage(C.GREEN + "Downloading pack: " + pack + "/" + branch + (trim ? " trimmed" : "") + (overwrite ? " overwriting" : ""));
        if (pack.equals("overworld")) {
            String url = "https://github.com/IrisDimensions/overworld/releases/download/" + INMS.OVERWORLD_TAG + "/overworld.zip";
            Iris.service(StudioSVC.class).downloadRelease(sender(), url, trim, overwrite);
        } else {
            Iris.service(StudioSVC.class).downloadSearch(sender(), "IrisDimensions/" + pack + "/" + branch, trim, overwrite);
        }
    }

    @Director(description = "Get metrics for your world", aliases = "measure", origin = DirectorOrigin.PLAYER)
    public void metrics() {
        if (!IrisToolbelt.isIrisWorld(world())) {
            sender().sendMessage(C.RED + "You must be in an Iris world");
            return;
        }
        sender().sendMessage(C.GREEN + "Sending metrics...");
        engine().printMetrics(sender());
    }

    @Director(description = "Reload configuration file (this is also done automatically)")
    public void reload() {
        IrisSettings.invalidate();
        IrisSettings.get();
        sender().sendMessage(C.GREEN + "Hotloaded settings");
    }

    @Director(name = "regen", aliases = {"rg"}, description = "Regenerate nearby chunks using Iris generation", origin = DirectorOrigin.PLAYER, sync = true)
    public void regen(
            @Param(name = "radius", description = "The radius of nearby chunks", defaultValue = "5")
            int radius,
            @Param(name = "parallelism", aliases = {"threads", "concurrency"}, description = "How many chunks to regenerate in parallel (0 = auto)", defaultValue = "0")
            int parallelism,
            @Param(name = "mode", aliases = {"scope", "profile"}, description = "Regen mode: terrain or full", defaultValue = "full")
            String mode
    ) {
        if (radius < 0) {
            sender().sendMessage(C.RED + "Radius must be 0 or greater.");
            return;
        }

        World world = player().getWorld();
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "You must be in an Iris world to use regen.");
            return;
        }

        RegenMode regenMode = RegenMode.parse(mode);
        if (regenMode == null) {
            sender().sendMessage(C.RED + "Unknown regen mode \"" + mode + "\". Use mode=terrain or mode=full.");
            return;
        }

        VolmitSender sender = sender();
        int centerX = player().getLocation().getBlockX() >> 4;
        int centerZ = player().getLocation().getBlockZ() >> 4;
        int threadCount = resolveRegenThreadCount(parallelism);
        List<Position2> targets = buildRegenTargets(centerX, centerZ, radius);
        int chunks = targets.size();
        String runId = world.getName() + "-" + System.currentTimeMillis();
        RegenDisplay display = createRegenDisplay(sender, regenMode);

        sender.sendMessage(C.GREEN + "Regen started (" + C.GOLD + regenMode.id() + C.GREEN + "): "
                + C.GOLD + chunks + C.GREEN + " chunks, "
                + C.GOLD + threadCount + C.GREEN + " worker(s). "
                + C.GRAY + "Progress is shown on-screen.");
        if (regenMode == RegenMode.TERRAIN) {
            Iris.warn("Regen running in terrain mode; mantle object/jigsaw stages are bypassed. Use mode=full to regenerate objects.");
        }

        Iris.info("Regen run start: id=" + runId
                + " world=" + world.getName()
                + " center=" + centerX + "," + centerZ
                + " radius=" + radius
                + " mode=" + regenMode.id()
                + " workers=" + threadCount
                + " chunks=" + chunks);
        Iris.info("Regen mode config: id=" + runId
                + " mode=" + regenMode.id()
                + " maintenance=" + regenMode.usesMaintenance()
                + " bypassMantle=" + regenMode.bypassMantleStages()
                + " resetMantleChunks=" + regenMode.resetMantleChunks()
                + " passes=" + regenMode.passCount()
                + " overlay=" + regenMode.applyMantleOverlay()
                + " diagnostics=" + regenMode.logChunkDiagnostics());

        String orchestratorName = "Iris-Regen-Orchestrator-" + runId;
        Thread orchestrator = new Thread(() -> runRegenOrchestrator(sender, world, targets, threadCount, regenMode, runId, display), orchestratorName);
        orchestrator.setDaemon(true);
        try {
            orchestrator.start();
            Iris.info("Regen worker dispatched on dedicated thread=" + orchestratorName + " id=" + runId + ".");
        } catch (Throwable e) {
            sender.sendMessage(C.RED + "Failed to start regen worker thread. See console.");
            closeRegenDisplay(display, 0);
            Iris.reportError(e);
        }
    }

    private int resolveRegenThreadCount(int parallelism) {
        int threads = parallelism <= 0 ? Runtime.getRuntime().availableProcessors() : parallelism;
        if (J.isFolia() && parallelism <= 0) {
            threads = 1;
        }
        return Math.max(1, threads);
    }

    private List<Position2> buildRegenTargets(int centerX, int centerZ, int radius) {
        int expected = (radius * 2 + 1) * (radius * 2 + 1);
        List<Position2> targets = new ArrayList<>(expected);
        for (int ring = 0; ring <= radius; ring++) {
            for (int x = -ring; x <= ring; x++) {
                for (int z = -ring; z <= ring; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != ring) {
                        continue;
                    }
                    targets.add(new Position2(centerX + x, centerZ + z));
                }
            }
        }
        return targets;
    }

    private void runRegenOrchestrator(
            VolmitSender sender,
            World world,
            List<Position2> targets,
            int threadCount,
            RegenMode mode,
            String runId,
            RegenDisplay display
    ) {
        long runStart = System.currentTimeMillis();
        AtomicBoolean setupDone = new AtomicBoolean(false);
        AtomicReference<String> setupPhase = new AtomicReference<>("bootstrap");
        AtomicLong setupPhaseSince = new AtomicLong(runStart);
        Thread setupWatchdog = createRegenSetupWatchdog(world, runId, setupDone, setupPhase, setupPhaseSince);
        setupWatchdog.start();
        boolean displayTerminal = false;

        Set<Thread> regenThreads = ConcurrentHashMap.newKeySet();
        AtomicInteger regenThreadCounter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Iris-Regen-" + runId + "-" + regenThreadCounter.incrementAndGet());
            thread.setDaemon(true);
            regenThreads.add(thread);
            return thread;
        };

        try {
            setRegenSetupPhase(setupPhase, setupPhaseSince, "touch-context", world, runId);
            updateRegenSetupDisplay(display, mode, "Touching command context", 1, 6);
            DirectorContext.touch(sender);
            if (mode.usesMaintenance()) {
                setRegenSetupPhase(setupPhase, setupPhaseSince, "enter-maintenance", world, runId);
                updateRegenSetupDisplay(display, mode, "Entering maintenance", 2, 6);
                IrisToolbelt.beginWorldMaintenance(world, "regen:" + mode.id(), mode.bypassMantleStages());
            } else {
                setRegenSetupPhase(setupPhase, setupPhaseSince, "maintenance-skip", world, runId);
                updateRegenSetupDisplay(display, mode, "Skipping maintenance", 2, 6);
            }

            ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount, threadFactory);
            try (SyncExecutor executor = new SyncExecutor(20)) {
                setRegenSetupPhase(setupPhase, setupPhaseSince, "resolve-platform", world, runId);
                updateRegenSetupDisplay(display, mode, "Resolving platform", 3, 6);
                PlatformChunkGenerator platform = IrisToolbelt.access(world);
                setRegenSetupPhase(setupPhase, setupPhaseSince, "validate-engine", world, runId);
                updateRegenSetupDisplay(display, mode, "Validating engine", 4, 6);
                if (platform == null || platform.getEngine() == null) {
                    Iris.warn("Regen aborted: engine access is null for world=" + world.getName() + " id=" + runId + ".");
                    completeRegenDisplay(display, mode, true, C.RED + "Engine access is null. Generate nearby chunks first.");
                    displayTerminal = true;
                    return;
                }

                if (mode.resetMantleChunks()) {
                    setRegenSetupPhase(setupPhase, setupPhaseSince, "prepare-mantle", world, runId);
                    updateRegenSetupDisplay(display, mode, "Preparing mantle reset", 5, 6);
                    int writeRadius = Math.max(0, platform.getEngine().getMantle().getRadius());
                    int plannedRadius = Math.max(0, platform.getEngine().getMantle().getRealRadius());
                    int resetPadding = mode.usesMaintenance() ? plannedRadius : 0;
                    long estimatedResetChunks = estimateRegenMantleResetChunks(targets, resetPadding);
                    if (estimatedResetChunks > REGEN_MAX_RESET_CHUNKS) {
                        int cappedPadding = capRegenMantleResetPadding(targets, resetPadding, REGEN_MAX_RESET_CHUNKS);
                        Iris.warn("Regen mantle reset cap applied: id=" + runId
                                + " desiredPadding=" + resetPadding
                                + " cappedPadding=" + cappedPadding
                                + " estimatedChunks=" + estimatedResetChunks
                                + " maxChunks=" + REGEN_MAX_RESET_CHUNKS);
                        resetPadding = cappedPadding;
                    }
                    Iris.info("Regen mantle reset planning: id=" + runId
                            + " writeRadius=" + writeRadius
                            + " plannedRadius=" + plannedRadius
                            + " resetPadding=" + resetPadding);
                    int resetChunks = resetRegenMantleChunks(platform, targets, resetPadding, runId);
                    Iris.info("Regen mantle reset complete: id=" + runId
                            + " resetChunks=" + resetChunks
                            + " resetPadding=" + resetPadding);
                }

                setRegenSetupPhase(setupPhase, setupPhaseSince, "dispatch", world, runId);
                updateRegenSetupDisplay(display, mode, "Dispatching chunk workers", 6, 6);
                RegenSummary summary = null;
                for (int pass = 1; pass <= mode.passCount(); pass++) {
                    String passId = mode.passCount() > 1 ? runId + "-p" + pass : runId;
                    summary = executeRegenQueue(sender, world, platform, targets, executor, pool, regenThreads, mode, passId, pass, mode.passCount(), runStart, display);
                    if (summary.failedChunks() > 0) {
                        break;
                    }
                }

                if (summary == null) {
                    completeRegenDisplay(display, mode, true, C.RED + "Regen failed before pass execution.");
                    displayTerminal = true;
                    return;
                }

                long totalRuntime = System.currentTimeMillis() - runStart;
                if (summary.failedChunks() <= 0) {
                    completeRegenDisplay(display, mode, false, C.GREEN + "Complete " + C.GOLD + summary.successChunks()
                            + C.GREEN + "/" + C.GOLD + summary.totalChunks() + C.GREEN + " in " + C.GOLD + totalRuntime + "ms");
                    displayTerminal = true;
                    return;
                }

                String failureDetail = C.RED + "Failed chunks " + C.GOLD + summary.failedChunks() + C.RED
                        + ", retries " + C.GOLD + summary.retryCount()
                        + C.RED + ", runtime " + C.GOLD + totalRuntime + "ms";
                if (!summary.failedPreview().isEmpty()) {
                    failureDetail = failureDetail + C.DARK_GRAY + " [" + summary.failedPreview() + "]";
                }
                completeRegenDisplay(display, mode, true, failureDetail);
                displayTerminal = true;
            } finally {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            completeRegenDisplay(display, mode, true, C.RED + "Regen interrupted.");
            displayTerminal = true;
            Iris.warn("Regen run interrupted: id=" + runId + " world=" + world.getName());
        } catch (Throwable e) {
            String failureDetail = C.RED + "Regen failed. Check console.";
            if (e.getMessage() != null && e.getMessage().contains("stalled")) {
                failureDetail = C.RED + "Regen stalled. Try smaller radius or terrain mode.";
            }
            completeRegenDisplay(display, mode, true, failureDetail);
            displayTerminal = true;
            Iris.reportError(e);
            e.printStackTrace();
        } finally {
            setupDone.set(true);
            setupWatchdog.interrupt();
            if (mode.usesMaintenance()) {
                IrisToolbelt.endWorldMaintenance(world, "regen:" + mode.id());
            }
            if (!displayTerminal) {
                closeRegenDisplay(display, REGEN_DISPLAY_FINAL_TICKS);
            }
            DirectorContext.remove();
            Iris.info("Regen run closed: id=" + runId + " world=" + world.getName() + " totalMs=" + (System.currentTimeMillis() - runStart));
        }
    }

    private RegenSummary executeRegenQueue(
            VolmitSender sender,
            World world,
            PlatformChunkGenerator platform,
            List<Position2> targets,
            SyncExecutor executor,
            ThreadPoolExecutor pool,
            Set<Thread> regenThreads,
            RegenMode mode,
            String runId,
            int passIndex,
            int passCount,
            long runStart,
            RegenDisplay display
    ) throws InterruptedException {
        ArrayDeque<RegenChunkTask> pending = new ArrayDeque<>(targets.size());
        long queueTime = System.currentTimeMillis();
        for (Position2 target : targets) {
            pending.addLast(new RegenChunkTask(target.getX(), target.getZ(), 1, queueTime));
        }

        ConcurrentMap<String, RegenActiveTask> activeTasks = new ConcurrentHashMap<>();
        ExecutorCompletionService<RegenChunkResult> completion = new ExecutorCompletionService<>(pool);
        List<Position2> failedChunks = new ArrayList<>();

        int totalChunks = targets.size();
        int successChunks = 0;
        int failedCount = 0;
        int retryCount = 0;
        long submittedTasks = 0L;
        long finishedTasks = 0L;
        int completedChunks = 0;
        int inFlight = 0;
        int unchangedHeartbeats = 0;
        int lastCompleted = -1;
        long lastDump = 0L;
        long lastProgressUiMs = 0L;
        lastProgressUiMs = updateRegenProgressAction(
                sender,
                display,
                mode,
                passIndex,
                passCount,
                completedChunks,
                totalChunks,
                inFlight,
                pending.size(),
                false,
                false,
                false,
                true,
                "Queue initialized",
                lastProgressUiMs
        );

        while (inFlight < pool.getMaximumPoolSize() && !pending.isEmpty()) {
            RegenChunkTask task = pending.removeFirst();
            completion.submit(() -> runRegenChunk(task, world, platform, executor, activeTasks, mode, runId));
            inFlight++;
            submittedTasks++;
        }

        while (completedChunks < totalChunks) {
            Future<RegenChunkResult> future = completion.poll(REGEN_HEARTBEAT_MS, TimeUnit.MILLISECONDS);
            if (future == null) {
                if (completedChunks == lastCompleted) {
                    unchangedHeartbeats++;
                } else {
                    unchangedHeartbeats = 0;
                    lastCompleted = completedChunks;
                }

                Iris.warn("Regen heartbeat: id=" + runId
                        + " completed=" + completedChunks + "/" + totalChunks
                        + " remaining=" + (totalChunks - completedChunks)
                        + " queued=" + pending.size()
                        + " inFlight=" + inFlight
                        + " submitted=" + submittedTasks
                        + " finishedTasks=" + finishedTasks
                        + " retries=" + retryCount
                        + " failed=" + failedCount
                        + " poolActive=" + pool.getActiveCount()
                        + " poolQueue=" + pool.getQueue().size()
                        + " poolDone=" + pool.getCompletedTaskCount()
                        + " activeTasks=" + formatActiveTasks(activeTasks));
                lastProgressUiMs = updateRegenProgressAction(
                        sender,
                        display,
                        mode,
                        passIndex,
                        passCount,
                        completedChunks,
                        totalChunks,
                        inFlight,
                        pending.size(),
                        unchangedHeartbeats > 0,
                        false,
                        false,
                        true,
                        unchangedHeartbeats > 0 ? "Waiting for active chunk to finish" : "Waiting for chunk result",
                        lastProgressUiMs
                );

                if (unchangedHeartbeats >= REGEN_STALL_DUMP_HEARTBEATS && System.currentTimeMillis() - lastDump >= 10000L) {
                    lastDump = System.currentTimeMillis();
                    Iris.warn("Regen appears stalled; dumping worker stack traces for id=" + runId + ".");
                    dumpRegenWorkerStacks(regenThreads, world.getName());
                }
                if (unchangedHeartbeats >= REGEN_STALL_ABORT_HEARTBEATS) {
                    updateRegenProgressAction(
                            sender,
                            display,
                            mode,
                            passIndex,
                            passCount,
                            completedChunks,
                            totalChunks,
                            inFlight,
                            pending.size(),
                            true,
                            true,
                            true,
                            true,
                            "Stalled with no chunk completion",
                            lastProgressUiMs
                    );
                    throw new IllegalStateException("Regen stalled with no progress for "
                            + (REGEN_STALL_ABORT_HEARTBEATS * REGEN_HEARTBEAT_MS)
                            + "ms (id=" + runId
                            + ", mode=" + mode.id()
                            + ", completed=" + completedChunks
                            + "/" + totalChunks
                            + ", inFlight=" + inFlight
                            + ", queued=" + pending.size()
                            + ").");
                }
                continue;
            }

            RegenChunkResult result;
            try {
                result = future.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new IllegalStateException("Regen worker failed unexpectedly for run " + runId, cause);
            }

            inFlight--;
            finishedTasks++;
            long duration = result.finishedAtMs() - result.startedAtMs();

            if (result.success()) {
                completedChunks++;
                successChunks++;
                if (result.task().attempt() > 1) {
                    Iris.warn("Regen chunk recovered after retry: id=" + runId
                            + " chunk=" + result.task().chunkX() + "," + result.task().chunkZ()
                            + " attempt=" + result.task().attempt()
                            + " durationMs=" + duration);
                } else if (duration >= 5000L) {
                    Iris.warn("Regen chunk slow: id=" + runId
                            + " chunk=" + result.task().chunkX() + "," + result.task().chunkZ()
                            + " durationMs=" + duration
                            + " loadedAtStart=" + result.loadedAtStart());
                }
            } else if (result.task().attempt() < REGEN_MAX_ATTEMPTS) {
                retryCount++;
                RegenChunkTask retryTask = result.task().retry(System.currentTimeMillis());
                pending.addLast(retryTask);
                Iris.warn("Regen chunk retry scheduled: id=" + runId
                        + " chunk=" + result.task().chunkX() + "," + result.task().chunkZ()
                        + " failedAttempt=" + result.task().attempt()
                        + " nextAttempt=" + retryTask.attempt()
                        + " error=" + result.errorSummary());
            } else {
                completedChunks++;
                failedCount++;
                Position2 failed = new Position2(result.task().chunkX(), result.task().chunkZ());
                failedChunks.add(failed);
                Iris.warn("Regen chunk failed terminally: id=" + runId
                        + " chunk=" + result.task().chunkX() + "," + result.task().chunkZ()
                        + " attempts=" + result.task().attempt()
                        + " error=" + result.errorSummary());
                if (result.error() != null) {
                    Iris.reportError(result.error());
                }
            }

            while (inFlight < pool.getMaximumPoolSize() && !pending.isEmpty()) {
                RegenChunkTask task = pending.removeFirst();
                completion.submit(() -> runRegenChunk(task, world, platform, executor, activeTasks, mode, runId));
                inFlight++;
                submittedTasks++;
            }

            lastProgressUiMs = updateRegenProgressAction(
                    sender,
                    display,
                    mode,
                    passIndex,
                    passCount,
                    completedChunks,
                    totalChunks,
                    inFlight,
                    pending.size(),
                    unchangedHeartbeats > 0,
                    false,
                    false,
                    false,
                    "Generating chunks",
                    lastProgressUiMs
            );
        }

        MantleOverlaySummary overlaySummary = MantleOverlaySummary.empty();
        if (failedCount <= 0 && mode.applyMantleOverlay()) {
            overlaySummary = applyRegenMantleOverlay(world, platform, targets, runId, mode.logChunkDiagnostics());
        }

        long runtimeMs = System.currentTimeMillis() - runStart;
        String preview = formatFailedChunkPreview(failedChunks);
        Iris.info("Regen run complete: id=" + runId
                + " world=" + world.getName()
                + " total=" + totalChunks
                + " success=" + successChunks
                + " failed=" + failedCount
                + " retries=" + retryCount
                + " submittedTasks=" + submittedTasks
                + " finishedTasks=" + finishedTasks
                + " overlayChunks=" + overlaySummary.chunksProcessed()
                + " overlayObjectChunks=" + overlaySummary.chunksWithObjectKeys()
                + " overlayBlocks=" + overlaySummary.blocksApplied()
                + " runtimeMs=" + runtimeMs
                + " failedPreview=" + preview);
        updateRegenProgressAction(
                sender,
                display,
                mode,
                passIndex,
                passCount,
                completedChunks,
                totalChunks,
                inFlight,
                pending.size(),
                false,
                true,
                failedCount > 0,
                true,
                failedCount > 0 ? "Completed with failures" : "Pass complete",
                lastProgressUiMs
        );
        return new RegenSummary(totalChunks, successChunks, failedCount, retryCount, preview);
    }

    private long updateRegenProgressAction(
            VolmitSender sender,
            RegenDisplay display,
            RegenMode mode,
            int passIndex,
            int passCount,
            int completed,
            int total,
            int inFlight,
            int queued,
            boolean stalled,
            boolean terminal,
            boolean failed,
            boolean force,
            String detail,
            long lastUiMs
    ) {
        if (display == null && !sender.isPlayer()) {
            return lastUiMs;
        }

        long now = System.currentTimeMillis();
        if (!force && now - lastUiMs < REGEN_PROGRESS_UPDATE_MS) {
            return lastUiMs;
        }

        int safePassCount = Math.max(1, passCount);
        int safePassIndex = Math.max(1, Math.min(passIndex, safePassCount));
        int safeTotal = Math.max(1, total);
        int safeCompleted = Math.max(0, Math.min(completed, safeTotal));
        double passProgress = safeCompleted / (double) safeTotal;
        double overallProgress = ((safePassIndex - 1) + passProgress) / safePassCount;
        int percent = (int) Math.round(overallProgress * 100.0D);
        String bar = buildRegenProgressBar(overallProgress);
        C statusColor = failed ? C.RED : terminal ? C.GREEN : stalled ? C.RED : C.AQUA;
        String statusLabel = failed ? "FAILED" : terminal ? "DONE" : stalled ? "STALLED" : "RUN";
        BarColor bossColor = failed ? BarColor.RED : terminal ? BarColor.GREEN : stalled ? BarColor.RED : BarColor.BLUE;
        String title = C.GOLD + "Regen " + mode.id()
                + C.GRAY + " " + statusColor + statusLabel
                + C.GRAY + " " + C.YELLOW + percent + "%"
                + C.DARK_GRAY + " P" + safePassIndex + "/" + safePassCount;
        String action = bar
                + C.GRAY + " " + C.YELLOW + percent + "%"
                + C.DARK_GRAY + " P" + safePassIndex + "/" + safePassCount
                + C.DARK_GRAY + " C" + safeCompleted + "/" + safeTotal
                + C.DARK_GRAY + " Q" + queued
                + C.DARK_GRAY + " F" + inFlight;
        if (detail != null && !detail.isBlank()) {
            action = action + C.GRAY + " | " + C.WHITE + detail;
        }

        if (display != null) {
            updateRegenDisplay(display, overallProgress, bossColor, title, action);
            return now;
        }

        if (sender.isPlayer()) {
            String actionText = action;
            J.runEntity(sender.player(), () -> sender.sendAction(actionText));
        }
        return now;
    }

    private static String buildRegenProgressBar(double progress) {
        int width = REGEN_PROGRESS_BAR_WIDTH;
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, progress)) * width);
        StringBuilder bar = new StringBuilder(width * 3 + 4);
        bar.append(C.DARK_GRAY).append("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? C.GREEN : C.DARK_GRAY).append("|");
        }
        bar.append(C.DARK_GRAY).append("]");
        return bar.toString();
    }

    private RegenDisplay createRegenDisplay(VolmitSender sender, RegenMode mode) {
        if (!sender.isPlayer()) {
            return null;
        }

        Player player = sender.player();
        if (player == null) {
            return null;
        }

        BossBar bossBar = Bukkit.createBossBar(C.GOLD + "Regen " + mode.id(), BarColor.BLUE, BarStyle.SEGMENTED_20);
        bossBar.setProgress(0.0D);
        bossBar.addPlayer(player);
        bossBar.setVisible(true);
        RegenDisplay display = new RegenDisplay(sender, bossBar);
        String title = C.GOLD + "Regen " + mode.id() + C.GRAY + " " + C.AQUA + "RUN" + C.GRAY + " " + C.YELLOW + "0%";
        String action = buildRegenProgressBar(0.0D) + C.GRAY + " " + C.YELLOW + "0%" + C.GRAY + " | " + C.WHITE + "Preparing setup";
        updateRegenDisplay(display, 0.0D, BarColor.BLUE, title, action);
        pulseRegenDisplay(display);
        return display;
    }

    private void updateRegenSetupDisplay(RegenDisplay display, RegenMode mode, String phase, int step, int totalSteps) {
        if (display == null || display.closed.get()) {
            return;
        }

        int safeTotalSteps = Math.max(1, totalSteps);
        int safeStep = Math.max(0, Math.min(step, safeTotalSteps));
        double setupProgress = Math.max(0.0D, Math.min(0.1D, (safeStep / (double) safeTotalSteps) * 0.1D));
        int percent = (int) Math.round(setupProgress * 100.0D);
        String title = C.GOLD + "Regen " + mode.id() + C.GRAY + " " + C.AQUA + "SETUP" + C.GRAY + " " + C.YELLOW + percent + "%";
        String action = buildRegenProgressBar(setupProgress)
                + C.GRAY + " " + C.YELLOW + percent + "%"
                + C.GRAY + " | " + C.WHITE + phase;
        updateRegenDisplay(display, setupProgress, BarColor.BLUE, title, action);
    }

    private void updateRegenDisplay(RegenDisplay display, double progress, BarColor color, String title, String action) {
        if (display == null || display.closed.get()) {
            return;
        }

        display.progress = Math.max(0.0D, Math.min(1.0D, progress));
        display.color = color == null ? BarColor.BLUE : color;
        display.title = title == null ? "" : title;
        display.actionLine = action == null ? "" : action;

        Player player = display.sender.player();
        if (player == null) {
            closeRegenDisplay(display, 0);
            return;
        }

        boolean scheduled = J.runEntity(player, () -> {
            if (display.closed.get()) {
                return;
            }

            display.bossBar.setProgress(display.progress);
            display.bossBar.setColor(display.color);
            display.bossBar.setTitle(display.title);
            if (!display.actionLine.isBlank()) {
                display.sender.sendAction(display.actionLine);
            }
        });
        if (!scheduled) {
            closeRegenDisplay(display, 0);
        }
    }

    private void pulseRegenDisplay(RegenDisplay display) {
        if (display == null || display.closed.get()) {
            return;
        }

        Player player = display.sender.player();
        if (player == null) {
            closeRegenDisplay(display, 0);
            return;
        }

        boolean scheduled = J.runEntity(player, () -> {
            if (display.closed.get()) {
                return;
            }

            Player activePlayer = display.sender.player();
            if (activePlayer == null || !activePlayer.isOnline()) {
                closeRegenDisplay(display, 0);
                return;
            }

            if (!display.actionLine.isBlank()) {
                display.sender.sendAction(display.actionLine);
            }
            pulseRegenDisplay(display);
        }, REGEN_ACTION_PULSE_TICKS);

        if (!scheduled) {
            closeRegenDisplay(display, 0);
        }
    }

    private void completeRegenDisplay(RegenDisplay display, RegenMode mode, boolean failed, String detail) {
        if (display == null || display.closed.get()) {
            return;
        }

        double progress = failed ? Math.max(0.0D, Math.min(1.0D, display.progress)) : 1.0D;
        int percent = (int) Math.round(progress * 100.0D);
        BarColor color = failed ? BarColor.RED : BarColor.GREEN;
        String status = failed ? C.RED + "FAILED" : C.GREEN + "DONE";
        String title = C.GOLD + "Regen " + mode.id() + C.GRAY + " " + status + C.GRAY + " " + C.YELLOW + percent + "%";
        String action = buildRegenProgressBar(progress) + C.GRAY + " " + C.YELLOW + percent + "%";
        if (detail != null && !detail.isBlank()) {
            action = action + C.GRAY + " | " + C.WHITE + detail;
        }

        updateRegenDisplay(display, progress, color, title, action);
        closeRegenDisplay(display, REGEN_DISPLAY_FINAL_TICKS);
    }

    private void closeRegenDisplay(RegenDisplay display, int delayTicks) {
        if (display == null || display.closed.get()) {
            return;
        }

        Player player = display.sender.player();
        Runnable closeTask = () -> {
            if (!display.closed.compareAndSet(false, true)) {
                return;
            }

            display.bossBar.removeAll();
            display.bossBar.setVisible(false);
            display.sender.sendAction(" ");
        };

        if (player == null) {
            display.closed.set(true);
            return;
        }

        boolean scheduled = delayTicks > 0
                ? J.runEntity(player, closeTask, delayTicks)
                : J.runEntity(player, closeTask);
        if (!scheduled) {
            display.closed.set(true);
        }
    }

    private RegenChunkResult runRegenChunk(
            RegenChunkTask task,
            World world,
            PlatformChunkGenerator platform,
            SyncExecutor executor,
            ConcurrentMap<String, RegenActiveTask> activeTasks,
            RegenMode mode,
            String runId
    ) {
        String worker = Thread.currentThread().getName();
        long startedAt = System.currentTimeMillis();
        boolean loadedAtStart = false;
        try {
            loadedAtStart = world.isChunkLoaded(task.chunkX(), task.chunkZ());
        } catch (Throwable ignored) {
        }

        activeTasks.put(worker, new RegenActiveTask(task.chunkX(), task.chunkZ(), task.attempt(), startedAt, loadedAtStart));
        try {
            if (mode.logChunkDiagnostics()) {
                Iris.info("Regen chunk start: id=" + runId
                        + " chunk=" + task.chunkX() + "," + task.chunkZ()
                        + " attempt=" + task.attempt()
                        + " loadedAtStart=" + loadedAtStart
                        + " worker=" + worker);
            }
            RegenRuntime.setRunId(runId);
            try {
                platform.injectChunkReplacement(world, task.chunkX(), task.chunkZ(), executor);
            } finally {
                RegenRuntime.clear();
            }
            if (mode.logChunkDiagnostics()) {
                Iris.info("Regen chunk end: id=" + runId
                        + " chunk=" + task.chunkX() + "," + task.chunkZ()
                        + " attempt=" + task.attempt()
                        + " worker=" + worker
                        + " durationMs=" + (System.currentTimeMillis() - startedAt));
            }
            return RegenChunkResult.success(task, worker, startedAt, System.currentTimeMillis(), loadedAtStart);
        } catch (Throwable e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return RegenChunkResult.failure(task, worker, startedAt, System.currentTimeMillis(), loadedAtStart, e);
        } finally {
            activeTasks.remove(worker);
        }
    }

    private Thread createRegenSetupWatchdog(
            World world,
            String runId,
            AtomicBoolean setupDone,
            AtomicReference<String> setupPhase,
            AtomicLong setupPhaseSince
    ) {
        String setupWatchdogName = "Iris-Regen-SetupWatchdog-" + runId;
        Thread setupWatchdog = new Thread(() -> {
            while (!setupDone.get()) {
                try {
                    Thread.sleep(REGEN_HEARTBEAT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (!setupDone.get()) {
                    long elapsed = System.currentTimeMillis() - setupPhaseSince.get();
                    Iris.warn("Regen setup heartbeat: id=" + runId
                            + " phase=" + setupPhase.get()
                            + " elapsedMs=" + elapsed
                            + " world=" + world.getName());
                }
            }
        }, setupWatchdogName);
        setupWatchdog.setDaemon(true);
        return setupWatchdog;
    }

    private void setRegenSetupPhase(
            AtomicReference<String> setupPhase,
            AtomicLong setupPhaseSince,
            String nextPhase,
            World world,
            String runId
    ) {
        setupPhase.set(nextPhase);
        setupPhaseSince.set(System.currentTimeMillis());
        Iris.info("Regen setup phase: id=" + runId + " phase=" + nextPhase + " world=" + world.getName());
    }

    private RegenMantleChunkState inspectRegenMantleChunk(PlatformChunkGenerator platform, int chunkX, int chunkZ) {
        MantleChunk<Matter> chunk = platform.getEngine().getMantle().getMantle().getChunk(chunkX, chunkZ).use();
        try {
            AtomicInteger blockDataEntries = new AtomicInteger();
            AtomicInteger stringEntries = new AtomicInteger();
            AtomicInteger objectKeyEntries = new AtomicInteger();
            AtomicInteger tileEntries = new AtomicInteger();

            chunk.iterate(BlockData.class, (x, y, z, data) -> {
                if (data != null) {
                    blockDataEntries.incrementAndGet();
                }
            });
            chunk.iterate(String.class, (x, y, z, key) -> {
                if (key == null || key.isEmpty()) {
                    return;
                }
                stringEntries.incrementAndGet();
                if (key.indexOf('@') > 0) {
                    objectKeyEntries.incrementAndGet();
                }
            });
            chunk.iterate(TileWrapper.class, (x, y, z, tile) -> {
                if (tile != null) {
                    tileEntries.incrementAndGet();
                }
            });

            return new RegenMantleChunkState(
                    chunk.isFlagged(MantleFlag.PLANNED),
                    chunk.isFlagged(MantleFlag.OBJECT),
                    chunk.isFlagged(MantleFlag.JIGSAW),
                    chunk.isFlagged(MantleFlag.REAL),
                    blockDataEntries.get(),
                    stringEntries.get(),
                    objectKeyEntries.get(),
                    tileEntries.get()
            );
        } finally {
            chunk.release();
        }
    }

    private MantleOverlaySummary applyRegenMantleOverlay(
            World world,
            PlatformChunkGenerator platform,
            List<Position2> targets,
            String runId,
            boolean diagnostics
    ) throws InterruptedException {
        int processed = 0;
        int chunksWithObjectKeys = 0;
        int totalAppliedBlocks = 0;

        for (Position2 target : targets) {
            int chunkX = target.getX();
            int chunkZ = target.getZ();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger chunkApplied = new AtomicInteger();
            AtomicInteger chunkObjectKeys = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            boolean scheduled = J.runRegion(world, chunkX, chunkZ, () -> {
                try {
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    MantleChunk<Matter> mantleChunk = platform.getEngine().getMantle().getMantle().getChunk(chunkX, chunkZ).use();
                    try {
                        mantleChunk.iterate(String.class, (x, y, z, value) -> {
                            if (value != null && !value.isEmpty() && value.indexOf('@') > 0) {
                                chunkObjectKeys.incrementAndGet();
                            }
                        });

                        int minWorldY = world.getMinHeight();
                        int maxWorldY = world.getMaxHeight();
                        mantleChunk.iterate(BlockData.class, (x, y, z, blockData) -> {
                            if (blockData == null) {
                                return;
                            }
                            int worldY = y + minWorldY;
                            if (worldY < minWorldY || worldY >= maxWorldY) {
                                return;
                            }
                            chunk.getBlock(x & 15, worldY, z & 15).setBlockData(blockData, false);
                            chunkApplied.incrementAndGet();
                        });
                    } finally {
                        mantleChunk.release();
                    }
                } catch (Throwable e) {
                    failure.set(e);
                } finally {
                    latch.countDown();
                }
            });

            if (!scheduled) {
                throw new IllegalStateException("Failed to schedule regen mantle overlay for chunk " + chunkX + "," + chunkZ + " id=" + runId);
            }

            while (!latch.await(REGEN_HEARTBEAT_MS, TimeUnit.MILLISECONDS)) {
                Iris.warn("Regen overlay heartbeat: id=" + runId
                        + " chunk=" + chunkX + "," + chunkZ
                        + " appliedBlocks=" + chunkApplied.get());
            }

            Throwable error = failure.get();
            if (error != null) {
                throw new IllegalStateException("Failed to apply regen mantle overlay at chunk " + chunkX + "," + chunkZ + " id=" + runId, error);
            }

            processed++;
            totalAppliedBlocks += chunkApplied.get();
            if (chunkObjectKeys.get() > 0) {
                chunksWithObjectKeys++;
            }

            if (diagnostics) {
                Iris.info("Regen overlay chunk: id=" + runId
                        + " chunk=" + chunkX + "," + chunkZ
                        + " objectKeys=" + chunkObjectKeys.get()
                        + " appliedBlocks=" + chunkApplied.get());
            }
        }
        return new MantleOverlaySummary(processed, chunksWithObjectKeys, totalAppliedBlocks);
    }

    private static String formatFailedChunkPreview(List<Position2> failedChunks) {
        if (failedChunks.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        int index = 0;
        for (Position2 chunk : failedChunks) {
            if (index > 0) {
                builder.append(", ");
            }
            if (index >= 10) {
                builder.append("...");
                break;
            }
            builder.append(chunk.getX()).append(",").append(chunk.getZ());
            index++;
        }
        builder.append("]");
        return builder.toString();
    }

    private static String formatActiveTasks(ConcurrentMap<String, RegenActiveTask> activeTasks) {
        if (activeTasks.isEmpty()) {
            return "{}";
        }

        StringBuilder builder = new StringBuilder("{");
        int count = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, RegenActiveTask> entry : activeTasks.entrySet()) {
            if (count > 0) {
                builder.append(", ");
            }
            if (count >= 8) {
                builder.append("...");
                break;
            }
            RegenActiveTask activeTask = entry.getValue();
            builder.append(entry.getKey())
                    .append("=")
                    .append(activeTask.chunkX())
                    .append(",")
                    .append(activeTask.chunkZ())
                    .append("@")
                    .append(activeTask.attempt())
                    .append("/")
                    .append(now - activeTask.startedAtMs())
                    .append("ms")
                    .append(activeTask.loadedAtStart() ? ":loaded" : ":cold");
            count++;
        }
        builder.append("}");
        return builder.toString();
    }

    private static void dumpRegenWorkerStacks(Set<Thread> explicitThreads, String worldName) {
        Set<Thread> threads = new LinkedHashSet<>();
        threads.addAll(explicitThreads);
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread == null || !thread.isAlive()) {
                continue;
            }

            String name = thread.getName();
            if (name.startsWith("Iris-Regen-")
                    || name.startsWith("Iris EngineSVC-")
                    || name.startsWith("Iris World Manager")
                    || name.contains(worldName)) {
                threads.add(thread);
            }
        }

        for (Thread thread : threads) {
            if (thread == null || !thread.isAlive()) {
                continue;
            }

            Iris.warn("Regen worker thread=" + thread.getName() + " state=" + thread.getState());
            StackTraceElement[] trace = thread.getStackTrace();
            int limit = Math.min(trace.length, REGEN_STACK_LIMIT);
            for (int i = 0; i < limit; i++) {
                Iris.warn("  at " + trace[i]);
            }
        }
    }

    private int resetRegenMantleChunks(
            PlatformChunkGenerator platform,
            List<Position2> targets,
            int padding,
            String runId
    ) {
        if (targets.isEmpty()) {
            return 0;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Position2 target : targets) {
            minX = Math.min(minX, target.getX());
            maxX = Math.max(maxX, target.getX());
            minZ = Math.min(minZ, target.getZ());
            maxZ = Math.max(maxZ, target.getZ());
        }

        int fromX = minX - padding;
        int toX = maxX + padding;
        int fromZ = minZ - padding;
        int toZ = maxZ + padding;
        long total = (long) (toX - fromX + 1) * (long) (toZ - fromZ + 1);
        long started = System.currentTimeMillis();
        int resetCount = 0;
        art.arcane.volmlib.util.mantle.runtime.Mantle mantle = platform.getEngine().getMantle().getMantle();
        AtomicReference<Thread> deleteThread = new AtomicReference<>();
        ThreadFactory deleteFactory = runnable -> {
            Thread thread = new Thread(runnable, "Iris-Regen-Reset-" + runId);
            thread.setDaemon(true);
            deleteThread.set(thread);
            return thread;
        };
        ExecutorService deleteExecutor = Executors.newSingleThreadExecutor(deleteFactory);

        Iris.info("Regen mantle reset begin: id=" + runId
                + " targets=" + targets.size()
                + " padding=" + padding
                + " bounds=" + fromX + "," + fromZ + "->" + toX + "," + toZ
                + " totalChunks=" + total);

        try {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    final int chunkX = x;
                    final int chunkZ = z;
                    Future<?> deleteFuture = deleteExecutor.submit(() -> mantle.deleteChunk(chunkX, chunkZ));
                    long waitStart = System.currentTimeMillis();
                    while (true) {
                        try {
                            deleteFuture.get(REGEN_HEARTBEAT_MS, TimeUnit.MILLISECONDS);
                            break;
                        } catch (TimeoutException timeout) {
                            long waited = System.currentTimeMillis() - waitStart;
                            Iris.warn("Regen mantle reset waiting: id=" + runId
                                    + " chunk=" + chunkX + "," + chunkZ
                                    + " waitedMs=" + waited
                                    + " reset=" + resetCount + "/" + total);
                            Thread worker = deleteThread.get();
                            if (worker != null && worker.isAlive()) {
                                Iris.warn("Regen mantle reset worker thread=" + worker.getName() + " state=" + worker.getState());
                                StackTraceElement[] trace = worker.getStackTrace();
                                int limit = Math.min(trace.length, REGEN_STACK_LIMIT);
                                for (int i = 0; i < limit; i++) {
                                    Iris.warn("  at " + trace[i]);
                                }
                            }
                            if (waited >= REGEN_RESET_DELETE_ABORT_MS) {
                                deleteFuture.cancel(true);
                                throw new IllegalStateException("Timed out deleting mantle chunk " + chunkX + "," + chunkZ
                                        + " during regen reset id=" + runId
                                        + " waitedMs=" + waited);
                            }
                        } catch (ExecutionException e) {
                            Throwable cause = e.getCause() == null ? e : e.getCause();
                            throw new IllegalStateException("Failed deleting mantle chunk " + chunkX + "," + chunkZ + " during regen reset id=" + runId, cause);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted deleting mantle chunk " + chunkX + "," + chunkZ + " during regen reset id=" + runId, e);
                        }
                    }

                    resetCount++;
                    if (resetCount % REGEN_RESET_PROGRESS_STEP == 0 || resetCount == total) {
                        long elapsed = System.currentTimeMillis() - started;
                        Iris.info("Regen mantle reset progress: id=" + runId
                                + " reset=" + resetCount + "/" + total
                                + " elapsedMs=" + elapsed
                                + " chunk=" + chunkX + "," + chunkZ);
                    }
                }
            }
        } finally {
            deleteExecutor.shutdownNow();
        }

        Iris.info("Regen mantle reset done: id=" + runId
                + " targets=" + targets.size()
                + " padding=" + padding
                + " resetChunks=" + resetCount
                + " elapsedMs=" + (System.currentTimeMillis() - started));
        return resetCount;
    }

    private long estimateRegenMantleResetChunks(List<Position2> targets, int padding) {
        if (targets.isEmpty()) {
            return 0L;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Position2 target : targets) {
            minX = Math.min(minX, target.getX());
            maxX = Math.max(maxX, target.getX());
            minZ = Math.min(minZ, target.getZ());
            maxZ = Math.max(maxZ, target.getZ());
        }

        long width = (long) (maxX - minX + 1) + (padding * 2L);
        long depth = (long) (maxZ - minZ + 1) + (padding * 2L);
        return Math.max(0L, width) * Math.max(0L, depth);
    }

    private int capRegenMantleResetPadding(List<Position2> targets, int desiredPadding, long maxChunks) {
        int low = 0;
        int high = Math.max(0, desiredPadding);
        int best = 0;
        while (low <= high) {
            int mid = low + ((high - low) >>> 1);
            long estimate = estimateRegenMantleResetChunks(targets, mid);
            if (estimate <= maxChunks) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static final class RegenDisplay {
        private final VolmitSender sender;
        private final BossBar bossBar;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile String title = "";
        private volatile String actionLine = "";
        private volatile double progress = 0.0D;
        private volatile BarColor color = BarColor.BLUE;

        private RegenDisplay(VolmitSender sender, BossBar bossBar) {
            this.sender = sender;
            this.bossBar = bossBar;
        }
    }

    private record RegenChunkTask(int chunkX, int chunkZ, int attempt, long queuedAtMs) {
        private RegenChunkTask retry(long now) {
            return new RegenChunkTask(chunkX, chunkZ, attempt + 1, now);
        }
    }

    private record MantleOverlaySummary(int chunksProcessed, int chunksWithObjectKeys, int blocksApplied) {
        private static MantleOverlaySummary empty() {
            return new MantleOverlaySummary(0, 0, 0);
        }
    }

    private record RegenMantleChunkState(
            boolean planned,
            boolean objectFlag,
            boolean jigsawFlag,
            boolean realFlag,
            int blockDataEntries,
            int stringEntries,
            int objectKeyEntries,
            int tileEntries
    ) {
        private String describe() {
            return "flags[planned=" + planned
                    + ",object=" + objectFlag
                    + ",jigsaw=" + jigsawFlag
                    + ",real=" + realFlag
                    + "] slices[blockData=" + blockDataEntries
                    + ",strings=" + stringEntries
                    + ",objectKeys=" + objectKeyEntries
                    + ",tiles=" + tileEntries
                    + "]";
        }
    }

    private enum RegenMode {
        TERRAIN("terrain", true, true, false, 1, false, false),
        FULL("full", true, false, false, 2, true, true);

        private final String id;
        private final boolean usesMaintenance;
        private final boolean bypassMantleStages;
        private final boolean resetMantleChunks;
        private final int passCount;
        private final boolean applyMantleOverlay;
        private final boolean logChunkDiagnostics;

        RegenMode(
                String id,
                boolean usesMaintenance,
                boolean bypassMantleStages,
                boolean resetMantleChunks,
                int passCount,
                boolean applyMantleOverlay,
                boolean logChunkDiagnostics
        ) {
            this.id = id;
            this.usesMaintenance = usesMaintenance;
            this.bypassMantleStages = bypassMantleStages;
            this.resetMantleChunks = resetMantleChunks;
            this.passCount = passCount;
            this.applyMantleOverlay = applyMantleOverlay;
            this.logChunkDiagnostics = logChunkDiagnostics;
        }

        private String id() {
            return id;
        }

        private boolean usesMaintenance() {
            return usesMaintenance;
        }

        private boolean bypassMantleStages() {
            return bypassMantleStages;
        }

        private boolean resetMantleChunks() {
            return resetMantleChunks;
        }

        private int passCount() {
            return passCount;
        }

        private boolean applyMantleOverlay() {
            return applyMantleOverlay;
        }

        private boolean logChunkDiagnostics() {
            return logChunkDiagnostics && IrisSettings.get().getGeneral().isDebug();
        }

        private static RegenMode parse(String raw) {
            if (raw == null) {
                return FULL;
            }

            String normalized = raw.trim();
            if (normalized.isEmpty()) {
                return FULL;
            }

            for (RegenMode mode : values()) {
                if (mode.id.equalsIgnoreCase(normalized)) {
                    return mode;
                }
            }
            return null;
        }
    }

    private record RegenActiveTask(int chunkX, int chunkZ, int attempt, long startedAtMs, boolean loadedAtStart) {
    }

    private record RegenChunkResult(
            RegenChunkTask task,
            String worker,
            long startedAtMs,
            long finishedAtMs,
            boolean loadedAtStart,
            boolean success,
            Throwable error
    ) {
        private static RegenChunkResult success(RegenChunkTask task, String worker, long startedAtMs, long finishedAtMs, boolean loadedAtStart) {
            return new RegenChunkResult(task, worker, startedAtMs, finishedAtMs, loadedAtStart, true, null);
        }

        private static RegenChunkResult failure(RegenChunkTask task, String worker, long startedAtMs, long finishedAtMs, boolean loadedAtStart, Throwable error) {
            return new RegenChunkResult(task, worker, startedAtMs, finishedAtMs, loadedAtStart, false, error);
        }

        private String errorSummary() {
            if (error == null) {
                return "unknown";
            }
            String message = error.getMessage();
            if (message == null || message.isEmpty()) {
                return error.getClass().getSimpleName();
            }
            return error.getClass().getSimpleName() + ": " + message;
        }
    }

    private record RegenSummary(int totalChunks, int successChunks, int failedChunks, int retryCount, String failedPreview) {
    }

    @Director(description = "Unload an Iris World", origin = DirectorOrigin.PLAYER, sync = true)
    public void unloadWorld(
            @Param(description = "The world to unload")
            World world
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }
        sender().sendMessage(C.GREEN + "Unloading world: " + world.getName());
        try {
            IrisToolbelt.evacuate(world);
            boolean unloaded = FoliaWorldsLink.get().unloadWorld(world, false);
            if (unloaded) {
                sender().sendMessage(C.GREEN + "World unloaded successfully.");
            } else {
                sender().sendMessage(C.RED + "Failed to unload the world.");
            }
        } catch (Exception e) {
            sender().sendMessage(C.RED + "Failed to unload the world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Director(description = "Load an Iris World", origin = DirectorOrigin.PLAYER, sync = true, aliases = {"import"})
    public void loadWorld(
            @Param(description = "The name of the world to load")
            String world
    ) {
        World worldloaded = Bukkit.getWorld(world);
        worldNameToCheck = world;
        boolean worldExists = doesWorldExist(worldNameToCheck);
        WorldEngine = world;

        if (!worldExists) {
            sender().sendMessage(C.YELLOW + world + " Doesnt exist on the server.");
            return;
        }

        String pathtodim = world + File.separator +"iris"+File.separator +"pack"+File.separator +"dimensions"+File.separator;
        File directory = new File(Bukkit.getWorldContainer(), pathtodim);

        String dimension = null;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String fileName = file.getName();
                        if (fileName.endsWith(".json")) {
                            dimension = fileName.substring(0, fileName.length() - 5);
                            sender().sendMessage(C.BLUE + "Generator: " + dimension);
                        }
                    }
                }
            }
        } else {
            sender().sendMessage(C.GOLD + world + " is not an iris world.");
            return;
        }

        if (dimension == null) {
            sender().sendMessage(C.RED + "Could not determine Iris dimension for " + world + ".");
            return;
        }

        sender().sendMessage(C.GREEN + "Loading world: " + world);

        if (!registerWorldInBukkitYml(world, dimension, null)) {
            return;
        }

        if (J.isFolia()) {
            sender().sendMessage(C.YELLOW + "Folia cannot load new worlds at runtime. Restart the server to load \"" + world + "\".");
            return;
        }

        Iris.instance.checkForBukkitWorlds(world::equals);
        sender().sendMessage(C.GREEN + world + " loaded successfully.");
    }
    @Director(description = "Evacuate an iris world", origin = DirectorOrigin.PLAYER, sync = true)
    public void evacuate(
            @Param(description = "Evacuate the world")
            World world
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }
        sender().sendMessage(C.GREEN + "Evacuating world" + world.getName());
        IrisToolbelt.evacuate(world);
    }

    boolean doesWorldExist(String worldName) {
        File worldContainer = Bukkit.getWorldContainer();
        File worldDirectory = new File(worldContainer, worldName);
        return worldDirectory.exists() && worldDirectory.isDirectory();
    }

    public static class PackDimensionTypeHandler implements DirectorParameterHandler<String> {
        @Override
        public KList<String> getPossibilities() {
            Set<String> options = new LinkedHashSet<>();
            options.add("default");

            File packsFolder = Iris.instance.getDataFolder("packs");
            File[] packs = packsFolder.listFiles();
            if (packs != null) {
                for (File pack : packs) {
                    if (pack == null || !pack.isDirectory()) {
                        continue;
                    }

                    options.add(pack.getName());

                    try {
                        IrisData data = IrisData.get(pack);
                        for (String key : data.getDimensionLoader().getPossibleKeys()) {
                            options.add(key);
                        }
                    } catch (Throwable ex) {
                        Iris.warn("Failed to read dimension keys from pack %s: %s%s",
                                pack.getName(),
                                ex.getClass().getSimpleName(),
                                ex.getMessage() == null ? "" : " - " + ex.getMessage());
                        Iris.reportError(ex);
                    }
                }
            }

            return new KList<>(options);
        }

        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String parse(String in, boolean force) throws DirectorParsingException {
            if (in == null || in.trim().isEmpty()) {
                throw new DirectorParsingException("World type cannot be empty");
            }

            return in.trim();
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }
    }
}
