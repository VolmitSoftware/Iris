/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.MinecraftVersion;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.util.common.plugin.VolmitPlugin;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudBossBarLane;
import art.arcane.volmlib.util.math.Vector3d;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.function.Supplier;

/**
 * Bukkit implementation of the root Iris platform service.
 */
public final class BukkitPlatform implements IrisPlatform {
    private static volatile Plugin PLUGIN;
    private static volatile HudActionBar HUD_BAR;
    private static volatile HudBossBarLane HUD_LANES;
    private static volatile Supplier<VolmitSender> CONSOLE;
    private static volatile HostBridge BRIDGE;

    public record HostBridge(
            java.util.function.BiConsumer<LogLevel, String> logSink,
            java.util.function.Consumer<String> msgSink,
            java.util.function.Consumer<Throwable> errorSink,
            java.util.function.Consumer<Object> eventSink,
            Supplier<File> dataFolder,
            java.util.function.Function<String[], File> dataFile,
            Supplier<File> pluginJar,
            java.util.function.IntSupplier irisVersion,
            java.util.function.IntSupplier minecraftVersion) {
    }

    public static void hostBridge(HostBridge bridge) {
        BRIDGE = bridge;
    }

    private static HostBridge bridge() {
        HostBridge bridge = BRIDGE;
        if (bridge == null) {
            throw new IllegalStateException("No Iris host bridge is hosted");
        }
        return bridge;
    }

    private final BukkitRegistries registries = new BukkitRegistries();
    private final BukkitScheduler scheduler = new BukkitScheduler();
    private final BukkitStructureHooks structureHooks = new BukkitStructureHooks();
    private final BukkitBiomeWriter biomeWriter = new BukkitBiomeWriter();

    public static void hostPlugin(Plugin plugin) {
        PLUGIN = plugin;
    }

    public static void releaseHost() {
        PLUGIN = null;
        HUD_BAR = null;
        HUD_LANES = null;
        CONSOLE = null;
        BRIDGE = null;
    }

    public static Plugin plugin() {
        Plugin plugin = PLUGIN;
        if (plugin == null) {
            throw new IllegalStateException("No Iris plugin is hosted");
        }
        return plugin;
    }

    public static boolean hasPlugin() {
        return PLUGIN != null;
    }

    public static VolmitPlugin volmitPlugin() {
        Plugin plugin = plugin();
        if (plugin instanceof VolmitPlugin host) {
            return host;
        }
        throw new IllegalStateException("Hosted Iris plugin is not a VolmitPlugin");
    }

    public static void hostHud(HudActionBar hudBar, HudBossBarLane hudLanes) {
        HUD_BAR = hudBar;
        HUD_LANES = hudLanes;
    }

    public static boolean hasHud() {
        return HUD_BAR != null && HUD_LANES != null;
    }

    public static HudActionBar hudBar() {
        HudActionBar hudBar = HUD_BAR;
        if (hudBar == null) {
            throw new IllegalStateException("No Iris HUD action bar is hosted");
        }
        return hudBar;
    }

    public static HudBossBarLane hudLanes() {
        HudBossBarLane hudLanes = HUD_LANES;
        if (hudLanes == null) {
            throw new IllegalStateException("No Iris HUD boss bar lanes are hosted");
        }
        return hudLanes;
    }

    public static void showProgressLane(Player player, String laneId, String title, double progress, long staleMillis) {
        if (!IrisSettings.get().getGeneral().isProgressBossBar()) {
            return;
        }
        hudLanes().show(player, laneId, title, progress, BarColor.BLUE, BarStyle.SOLID, staleMillis);
    }

    public static void hostConsoleSender(Supplier<VolmitSender> supplier) {
        CONSOLE = supplier;
    }

    public static VolmitSender console() {
        Supplier<VolmitSender> supplier = CONSOLE;
        if (supplier == null) {
            throw new IllegalStateException("No Iris console sender is hosted");
        }
        return supplier.get();
    }

    public static World unwrapWorld(PlatformWorld world) {
        return (World) world.nativeHandle();
    }

    public static PlatformBiome wrapBiome(Object biome) {
        return BukkitBiome.of((Biome) biome);
    }

    @Override
    public Class<?> classifyMantleValue(Object value) {
        if (value instanceof World) {
            return World.class;
        }

        if (value instanceof BlockData) {
            return BlockData.class;
        }

        if (value instanceof Entity) {
            return Entity.class;
        }

        return value.getClass();
    }

    @Override
    public boolean supportsMatterWorldIo() {
        return true;
    }

    public static void unregisterListener(Object candidate) {
        if (candidate instanceof Listener listener) {
            volmitPlugin().unregisterListener(listener);
        }
    }

    public static IrisPosition positionOf(Location location) {
        return new IrisPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static IrisPosition positionOf(Vector vector) {
        return new IrisPosition(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    public static Location toLocation(IrisPosition position, World world) {
        return new Location(world, position.getX(), position.getY(), position.getZ());
    }

    public static Entity spawnEntity(Location at, EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        return INMS.get().spawnEntity(at, type, reason);
    }

    public static Vector3d entityBoundingBox(EntityType type) {
        return INMS.get().getBoundingbox(type);
    }

    public static boolean hasTile(Material material) {
        return INMS.get().hasTile(material);
    }

    public static KMap<String, Object> serializeTile(Location location) {
        return INMS.get().serializeTile(location);
    }

    public static void deserializeTile(KMap<String, Object> properties, Location location) {
        INMS.get().deserializeTile(properties, location);
    }

    public static ItemStack applyCustomNbt(ItemStack itemStack, KMap<String, Object> customNbt) {
        return INMS.get().applyCustomNbt(itemStack, customNbt);
    }

    public static java.util.concurrent.CompletableFuture<Boolean> teleportAsync(Entity entity, Location destination) {
        return io.papermc.lib.PaperLib.teleportAsync(entity, destination);
    }

    public static java.util.concurrent.CompletableFuture<Boolean> teleportAsync(Entity entity, Location destination,
                                                                                PlayerTeleportEvent.TeleportCause cause) {
        return io.papermc.lib.PaperLib.teleportAsync(entity, destination, cause);
    }

    public static boolean isPaperServer() {
        return io.papermc.lib.PaperLib.isPaper();
    }

    public static java.util.concurrent.CompletableFuture<org.bukkit.Chunk> chunkAtAsync(World world, int x, int z, boolean generate) {
        return io.papermc.lib.PaperLib.getChunkAtAsync(world, x, z, generate);
    }

    @Override
    public String platformName() {
        return "bukkit";
    }

    @Override
    public String minecraftVersion() {
        return minecraftVersion(Bukkit.getServer());
    }

    static String minecraftVersion(Server server) {
        MinecraftVersion detected = MinecraftVersion.detect(server);
        return detected == null ? null : detected.value();
    }

    @Override
    public PlatformRegistries registries() {
        return registries;
    }

    @Override
    public PlatformScheduler scheduler() {
        return scheduler;
    }

    @Override
    public PlatformStructureHooks structureHooks() {
        return structureHooks;
    }

    @Override
    public PlatformBiomeWriter biomeWriter() {
        return biomeWriter;
    }

    @Override
    public File dataFolder() {
        return bridge().dataFolder().get();
    }

    @Override
    public File dataFile(String... path) {
        return bridge().dataFile().apply(path);
    }

    @Override
    public File pluginJar() {
        return bridge().pluginJar().get();
    }

    @Override
    public int irisVersionNumber() {
        return bridge().irisVersion().getAsInt();
    }

    @Override
    public int minecraftVersionNumber() {
        return bridge().minecraftVersion().getAsInt();
    }

    @Override
    public void callEvent(Object event) {
        bridge().eventSink().accept(event);
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @Override
    public boolean spawnEntity(PlatformWorld world, String entityKey, double x, double y, double z) {
        if (world == null || entityKey == null || !(world.nativeHandle() instanceof World bukkitWorld)) {
            return false;
        }
        NamespacedKey namespacedKey = NamespacedKey.fromString(entityKey);
        if (namespacedKey == null) {
            return false;
        }
        EntityType type = Registry.ENTITY_TYPE.get(namespacedKey);
        if (type == null) {
            return false;
        }
        Entity entity = spawnEntity(new Location(bukkitWorld, x, y, z), type, CreatureSpawnEvent.SpawnReason.COMMAND);
        return entity != null;
    }

    @Override
    public void log(LogLevel level, String message) {
        bridge().logSink().accept(level, message);
    }

    @Override
    public void msg(String message) {
        bridge().msgSink().accept(message);
    }

    @Override
    public void reportError(Throwable error) {
        bridge().errorSink().accept(error);
    }
}
