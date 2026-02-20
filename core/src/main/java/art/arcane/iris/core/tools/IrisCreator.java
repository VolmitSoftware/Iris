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

import com.google.common.util.concurrent.AtomicDouble;
import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.link.FoliaWorldsLink;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.pregenerator.PregenTask;
import art.arcane.iris.core.service.BoardSVC;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.engine.IrisNoisemapPrebakePipeline;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.volmlib.util.exceptions.IrisException;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.O;
import io.papermc.lib.PaperLib;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.IntSupplier;

import static art.arcane.iris.util.common.misc.ServerProperties.BUKKIT_YML;

/**
 * Makes it a lot easier to setup an engine, world, studio or whatever
 */
@Data
@Accessors(fluent = true, chain = true)
public class IrisCreator {
    /**
     * Specify an area to pregenerate during creation
     */
    private PregenTask pregen;
    /**
     * Specify a sender to get updates & progress info + tp when world is created.
     */
    private VolmitSender sender;
    /**
     * The seed to use for this generator
     */
    private long seed = 1337;
    /**
     * The dimension to use. This can be any online dimension, or a dimension in the
     * packs folder
     */
    private String dimension = IrisSettings.get().getGenerator().getDefaultWorldType();
    /**
     * The name of this world.
     */
    private String name = "irisworld";
    /**
     * Studio mode makes the engine hotloadable and uses the dimension in
     * your Iris/packs folder instead of copying the dimension files into
     * the world itself. Studio worlds are deleted when they are unloaded.
     */
    private boolean studio = false;
    /**
     * Benchmark mode
     */
    private boolean benchmark = false;

    public static boolean removeFromBukkitYml(String name) throws IOException {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(BUKKIT_YML);
        ConfigurationSection section = yml.getConfigurationSection("worlds");
        if (section == null) {
            return false;
        }
        section.set(name, null);
        if (section.getValues(false).keySet().stream().noneMatch(k -> section.get(k) != null)) {
            yml.set("worlds", null);
        }
        yml.save(BUKKIT_YML);
        return true;
    }
    public static boolean worldLoaded(){
        return true;
    }

    /**
     * Create the IrisAccess (contains the world)
     *
     * @return the IrisAccess
     * @throws IrisException shit happens
     */

    public World create() throws IrisException {
        if (Bukkit.isPrimaryThread()) {
            throw new IrisException("You cannot invoke create() on the main thread.");
        }

        if (studio()) {
            World existing = Bukkit.getWorld(name());
            if (existing == null) {
                IO.delete(new File(Bukkit.getWorldContainer(), name()));
                IO.delete(new File(Bukkit.getWorldContainer(), name() + "_nether"));
                IO.delete(new File(Bukkit.getWorldContainer(), name() + "_the_end"));
            }
        }

        IrisDimension d = IrisToolbelt.getDimension(dimension());

        if (d == null) {
            throw new IrisException("Dimension cannot be found null for id " + dimension());
        }

        if (sender == null)
            sender = Iris.getSender();

        if (!studio() || benchmark) {
            Iris.service(StudioSVC.class).installIntoWorld(sender, d.getLoadKey(), new File(Bukkit.getWorldContainer(), name()));
        }
        prebakeNoisemapsBeforeWorldCreate(d);

        AtomicDouble pp = new AtomicDouble(0);
        O<Boolean> done = new O<>();
        done.set(false);
        WorldCreator wc = new IrisWorldCreator()
                .dimension(dimension)
                .name(name)
                .seed(seed)
                .studio(studio)
                .create();
        if (ServerConfigurator.installDataPacks(true)) {
            throw new IrisException("Datapacks were missing!");
        }

        PlatformChunkGenerator access = (PlatformChunkGenerator) wc.generator();
        if (access == null) throw new IrisException("Access is null. Something bad happened.");

        J.a(() -> {
            IntSupplier g = () -> {
                if (access.getEngine() == null) {
                    return 0;
                }
                return access.getEngine().getGenerated();
            };
            if(!benchmark) {
                int req = access.getSpawnChunks().join();
                for (int c = 0; c < req && !done.get(); c = g.getAsInt()) {
                    double v = (double) c / req;
                    if (sender.isPlayer()) {
                        sender.sendProgress(v, "Generating");
                        J.sleep(16);
                    } else {
                        sender.sendMessage(C.WHITE + "Generating " + Form.pc(v) + ((C.GRAY + " (" + (req - c) + " Left)")));
                        J.sleep(1000);
                    }
                }
            }
        });


        World world;
        try {
            world = J.sfut(() -> INMS.get().createWorldAsync(wc))
                    .thenCompose(Function.identity())
                    .get();
        } catch (Throwable e) {
            done.set(true);
            if (J.isFolia() && containsCreateWorldUnsupportedOperation(e)) {
                if (FoliaWorldsLink.get().isActive()) {
                    throw new IrisException("Runtime world creation is blocked and async Folia runtime world-loader creation also failed.", e);
                }
                throw new IrisException("Runtime world creation is blocked and no async Folia runtime world-loader path is active.", e);
            }
            throw new IrisException("Failed to create world!", e);
        }

        done.set(true);

        if (sender.isPlayer() && !benchmark) {
            Player senderPlayer = sender.player();
            if (senderPlayer == null) {
                Iris.warn("Studio opened, but sender player reference is unavailable for teleport.");
            } else {
                Location studioEntryLocation = resolveStudioEntryLocation(world);
                if (studioEntryLocation == null) {
                    sender.sendMessage(C.YELLOW + "Studio opened, but entry location could not be resolved safely.");
                } else {
                    CompletableFuture<Boolean> teleportFuture = PaperLib.teleportAsync(senderPlayer, studioEntryLocation);
                    if (teleportFuture != null) {
                        teleportFuture.thenAccept(success -> {
                            if (Boolean.TRUE.equals(success)) {
                                J.runEntity(senderPlayer, () -> Iris.service(BoardSVC.class).updatePlayer(senderPlayer));
                            }
                        });
                        teleportFuture.exceptionally(throwable -> {
                            Iris.warn("Failed to schedule studio teleport task for " + senderPlayer.getName() + ".");
                            Iris.reportError(throwable);
                            return false;
                        });
                    }
                }
            }
        }

        if (studio || benchmark) {
            Runnable applyStudioWorldSettings = () -> {
                Iris.linkMultiverseCore.removeFromConfig(world);

                if (IrisSettings.get().getStudio().isDisableTimeAndWeather()) {
                    setBooleanGameRule(world, false, "ADVANCE_WEATHER", "DO_WEATHER_CYCLE", "WEATHER_CYCLE", "doWeatherCycle", "weatherCycle");
                    setBooleanGameRule(world, false, "ADVANCE_TIME", "DO_DAYLIGHT_CYCLE", "DAYLIGHT_CYCLE", "doDaylightCycle", "daylightCycle");
                    world.setTime(6000);
                }
            };

            J.s(applyStudioWorldSettings);
        } else {
            addToBukkitYml();
            J.s(() -> Iris.linkMultiverseCore.updateWorld(world, dimension));
        }

        if (pregen != null) {
            CompletableFuture<Boolean> ff = new CompletableFuture<>();

            IrisToolbelt.pregenerate(pregen, access)
                    .onProgress(pp::set)
                    .whenDone(() -> ff.complete(true));

            try {
                AtomicBoolean dx = new AtomicBoolean(false);

                J.a(() -> {
                    while (!dx.get()) {
                        if (sender.isPlayer()) {
                            sender.sendProgress(pp.get(), "Pregenerating");
                            J.sleep(16);
                        } else {
                            sender.sendMessage(C.WHITE + "Pregenerating " + Form.pc(pp.get()));
                            J.sleep(1000);
                        }
                    }
                });

                ff.get();
                dx.set(true);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return world;
    }

    private void prebakeNoisemapsBeforeWorldCreate(IrisDimension dimension) {
        IrisSettings.IrisSettingsPregen pregenSettings = IrisSettings.get().getPregen();
        if (!pregenSettings.isStartupNoisemapPrebake()) {
            return;
        }

        try {
            File targetDataFolder = new File(Bukkit.getWorldContainer(), name());
            if (studio() && !benchmark) {
                IrisData studioData = dimension.getLoader();
                if (studioData != null) {
                    targetDataFolder = studioData.getDataFolder();
                }
            }

            IrisData targetData = IrisData.get(targetDataFolder);
            SeedManager seedManager = new SeedManager(seed());
            IrisNoisemapPrebakePipeline.prebake(targetData, seedManager, name(), dimension.getLoadKey());
        } catch (Throwable throwable) {
            Iris.warn("Failed pre-create noisemap pre-bake for " + name() + "/" + dimension.getLoadKey() + ": " + throwable.getMessage());
            Iris.reportError(throwable);
        }
    }

    private Location resolveStudioEntryLocation(World world) {
        CompletableFuture<Location> locationFuture = J.sfut(() -> {
            Location spawnLocation = world.getSpawnLocation();
            if (spawnLocation != null) {
                return spawnLocation.clone();
            }

            int x = 0;
            int z = 0;
            int y = Math.max(world.getMinHeight() + 1, 96);
            return new Location(world, x + 0.5D, y, z + 0.5D);
        });
        if (locationFuture == null) {
            Iris.warn("Failed to schedule studio entry-location resolve task on the global scheduler for world \"" + world.getName() + "\".");
            return null;
        }

        try {
            Location rawLocation = locationFuture.get(15, TimeUnit.SECONDS);
            return resolveTopSafeStudioLocation(world, rawLocation);
        } catch (Throwable e) {
            Iris.warn("Failed to resolve studio entry location for world \"" + world.getName() + "\".");
            Iris.reportError(e);
            return null;
        }
    }

    private Location resolveTopSafeStudioLocation(World world, Location rawLocation) {
        if (world == null || rawLocation == null) {
            return rawLocation;
        }

        int chunkX = rawLocation.getBlockX() >> 4;
        int chunkZ = rawLocation.getBlockZ() >> 4;
        try {
            CompletableFuture<Chunk> chunkFuture = PaperLib.getChunkAtAsync(world, chunkX, chunkZ, false);
            if (chunkFuture != null) {
                chunkFuture.get(10, TimeUnit.SECONDS);
            }
        } catch (Throwable ignored) {
            return rawLocation;
        }

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return rawLocation;
        }

        CompletableFuture<Location> regionFuture = new CompletableFuture<>();
        boolean scheduled = J.runRegion(world, chunkX, chunkZ, () -> {
            try {
                regionFuture.complete(findTopSafeStudioLocation(world, rawLocation));
            } catch (Throwable e) {
                regionFuture.completeExceptionally(e);
            }
        });
        if (!scheduled) {
            return rawLocation;
        }

        try {
            Location resolved = regionFuture.get(15, TimeUnit.SECONDS);
            return resolved == null ? rawLocation : resolved;
        } catch (Throwable e) {
            Iris.warn("Failed to resolve safe studio entry surface for world \"" + world.getName() + "\".");
            Iris.reportError(e);
            return rawLocation;
        }
    }

    private Location findTopSafeStudioLocation(World world, Location source) {
        int x = source.getBlockX();
        int z = source.getBlockZ();
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int sourceY = source.getBlockY();
        int startY = Math.max(minY, Math.min(maxY, sourceY));
        float yaw = source.getYaw();
        float pitch = source.getPitch();

        int upperBound = Math.min(maxY, startY + 32);
        for (int y = startY; y <= upperBound; y++) {
            if (isSafeStandingLocation(world, x, y, z)) {
                return new Location(world, x + 0.5D, y, z + 0.5D, yaw, pitch);
            }
        }

        int lowerBound = Math.max(minY, startY - 64);
        for (int y = startY - 1; y >= lowerBound; y--) {
            if (isSafeStandingLocation(world, x, y, z)) {
                return new Location(world, x + 0.5D, y, z + 0.5D, yaw, pitch);
            }
        }

        int fallbackY = Math.max(minY, Math.min(maxY, source.getBlockY()));
        return new Location(world, x + 0.5D, fallbackY, z + 0.5D, yaw, pitch);
    }

    private boolean isSafeStandingLocation(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }

        Block below = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);

        Material belowType = below.getType();
        if (!belowType.isSolid()) {
            return false;
        }
        if (Tag.LEAVES.isTagged(belowType)) {
            return false;
        }
        if (belowType == Material.LAVA
                || belowType == Material.MAGMA_BLOCK
                || belowType == Material.FIRE
                || belowType == Material.SOUL_FIRE
                || belowType == Material.CAMPFIRE
                || belowType == Material.SOUL_CAMPFIRE) {
            return false;
        }
        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return false;
        }
        if (feet.isLiquid() || head.isLiquid()) {
            return false;
        }

        return true;
    }

    private static boolean containsCreateWorldUnsupportedOperation(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof UnsupportedOperationException) {
                for (StackTraceElement element : cursor.getStackTrace()) {
                    if ("org.bukkit.craftbukkit.CraftServer".equals(element.getClassName())
                            && "createWorld".equals(element.getMethodName())) {
                        return true;
                    }
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void setBooleanGameRule(World world, boolean value, String... names) {
        GameRule<Boolean> gameRule = resolveBooleanGameRule(world, names);
        if (gameRule != null) {
            world.setGameRule(gameRule, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static GameRule<Boolean> resolveBooleanGameRule(World world, String... names) {
        if (world == null || names == null || names.length == 0) {
            return null;
        }

        Set<String> candidates = buildRuleNameCandidates(names);
        for (String name : candidates) {
            if (name == null || name.isBlank()) {
                continue;
            }

            try {
                Field field = GameRule.class.getField(name);
                Object value = field.get(null);
                if (value instanceof GameRule<?> gameRule && Boolean.class.equals(gameRule.getType())) {
                    return (GameRule<Boolean>) gameRule;
                }
            } catch (Throwable ignored) {
            }

            try {
                GameRule<?> byName = GameRule.getByName(name);
                if (byName != null && Boolean.class.equals(byName.getType())) {
                    return (GameRule<Boolean>) byName;
                }
            } catch (Throwable ignored) {
            }
        }

        String[] availableRules = world.getGameRules();
        if (availableRules == null || availableRules.length == 0) {
            return null;
        }

        Set<String> normalizedCandidates = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                normalizedCandidates.add(normalizeRuleName(candidate));
            }
        }

        for (String availableRule : availableRules) {
            String normalizedAvailable = normalizeRuleName(availableRule);
            if (!normalizedCandidates.contains(normalizedAvailable)) {
                continue;
            }

            try {
                GameRule<?> byName = GameRule.getByName(availableRule);
                if (byName != null && Boolean.class.equals(byName.getType())) {
                    return (GameRule<Boolean>) byName;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Set<String> buildRuleNameCandidates(String... names) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }

            candidates.add(name);
            candidates.add(name.toLowerCase(Locale.ROOT));

            String lowerCamel = toLowerCamel(name);
            if (!lowerCamel.isEmpty()) {
                candidates.add(lowerCamel);
            }
        }

        return candidates;
    }

    private static String toLowerCamel(String name) {
        if (name == null) {
            return "";
        }

        String raw = name.trim();
        if (raw.isEmpty()) {
            return "";
        }

        String[] parts = raw.split("_+");
        if (parts.length == 0) {
            return raw;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(parts[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].toLowerCase(Locale.ROOT);
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static String normalizeRuleName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private void addToBukkitYml() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(BUKKIT_YML);
        String gen = "Iris:" + dimension;
        ConfigurationSection section = yml.contains("worlds") ? yml.getConfigurationSection("worlds") : yml.createSection("worlds");
        if (!section.contains(name)) {
            section.createSection(name).set("generator", gen);
            try {
                yml.save(BUKKIT_YML);
                Iris.info("Registered \"" + name + "\" in bukkit.yml");
            } catch (IOException e) {
                Iris.error("Failed to update bukkit.yml!");
                e.printStackTrace();
            }
        }
    }
}
