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

package art.arcane.iris.core.service;

import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.runtime.ObjectStudioActivation;
import art.arcane.iris.core.runtime.ObjectStudioLayout;
import art.arcane.iris.core.runtime.ObjectStudioLayout.GridCell;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.platform.studio.generators.ObjectStudioGenerator;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.IrisService;
import art.arcane.iris.util.common.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import art.arcane.iris.core.localization.BukkitRuntimeMessages;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
public class ObjectStudioSaveService implements IrisService {
    private static ObjectStudioSaveService INSTANCE;

    private final Map<UUID, ActiveStudio> studios = new ConcurrentHashMap<>();

    public static ObjectStudioSaveService get() {
        ObjectStudioSaveService svc = INSTANCE;
        if (svc != null) return svc;
        svc = IrisServices.get(ObjectStudioSaveService.class);
        return svc;
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        studios.clear();
        INSTANCE = null;
    }

    public void register(Engine engine, ObjectStudioGenerator generator) {
        World world = BukkitWorldBinding.world(engine.getTarget().getWorld());
        if (world == null) return;
        ObjectStudioLayout layout = generator.getLayout();
        if (layout == null) return;

        Map<String, IrisData> sources = generator.getPackData();
        if (sources == null || sources.isEmpty()) {
            IrisLogging.warn("Object Studio save disabled: no pack data sources available for world %s", world.getName());
            return;
        }

        Map<String, File> objectsDirs = new ConcurrentHashMap<>();
        for (Map.Entry<String, IrisData> e : sources.entrySet()) {
            File dir = resolveObjectsDir(e.getValue(), engine);
            if (dir != null) {
                objectsDirs.put(e.getKey(), dir);
            }
        }
        if (objectsDirs.isEmpty()) {
            IrisLogging.warn("Object Studio save disabled: no resolvable objects folders for world %s", world.getName());
            return;
        }

        ActiveStudio existing = studios.get(world.getUID());
        if (existing != null && existing.layout == layout) {
            return;
        }

        String packKey = engine.getDimension() == null ? null : engine.getDimension().getLoadKey();
        studios.put(world.getUID(), new ActiveStudio(world.getUID(), generator, objectsDirs, packKey));
        IrisLogging.debug("Object Studio live-save registered: world=%s cells=%d packs=%d",
                world.getName(), layout.cells().size(), objectsDirs.size());
    }

    public void unregister(World world) {
        if (world == null) return;
        ActiveStudio removed = studios.remove(world.getUID());
        if (removed != null) {
            if (removed.packKey != null) {
                ObjectStudioActivation.deactivate(removed.packKey);
            }
            IrisLogging.debug("Object Studio live-save unregistered: world=%s", world.getName());
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        unregister(event.getWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        World world = clicked.getWorld();
        ActiveStudio studio = studios.get(world.getUID());
        if (studio == null) return;

        Player player = event.getPlayer();
        GridCell cell = findCellNear(studio, clicked.getX(), clicked.getZ());
        if (cell == null) {
            send(player, IrisLanguage.text(BukkitRuntimeMessages.OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_NO_CELL_UNDER_CLICK_X_Z, MessageArgument.untrusted("x", String.valueOf(clicked.getX())), MessageArgument.untrusted("z", String.valueOf(clicked.getZ()))));
            return;
        }

        send(player, IrisLanguage.text(BukkitRuntimeMessages.OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_SAVING_X_X, MessageArgument.untrusted("pack", String.valueOf(cell.pack())), MessageArgument.untrusted("key", String.valueOf(cell.key())), MessageArgument.untrusted("w", String.valueOf(cell.w())), MessageArgument.untrusted("h", String.valueOf(cell.h())), MessageArgument.untrusted("d", String.valueOf(cell.d()))));
        IrisLogging.debug("Object Studio save triggered by %s for %s/%s", player.getName(), cell.pack(), cell.key());
        J.runRegion(world, cell.chunkMinX(), cell.chunkMinZ(), () -> {
            try {
                captureAndSave(studio, world, cell, player);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        });
    }

    private static GridCell findCellNear(ActiveStudio studio, int x, int z) {
        GridCell inside = studio.layout.findAt(x, z);
        if (inside != null) return inside;
        int reach = Math.max(1, studio.layout.padding() + 1);
        GridCell best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridCell cell : studio.layout.cells()) {
            int dx = 0;
            if (x < cell.originX()) dx = cell.originX() - x;
            else if (x >= cell.originX() + cell.w()) dx = x - (cell.originX() + cell.w() - 1);
            int dz = 0;
            if (z < cell.originZ()) dz = cell.originZ() - z;
            else if (z >= cell.originZ() + cell.d()) dz = z - (cell.originZ() + cell.d() - 1);
            int dist = Math.max(dx, dz);
            if (dist <= reach && dist < bestDist) {
                bestDist = dist;
                best = cell;
            }
        }
        return best;
    }

    public boolean teleportTo(Player player, String objectKey) {
        if (player == null || objectKey == null) return false;
        for (ActiveStudio studio : studios.values()) {
            GridCell cell = studio.layout.get(objectKey);
            if (cell == null) continue;
            World world = Bukkit.getWorld(studio.worldId);
            if (world == null) continue;

            double targetX = cell.originX() + cell.w() / 2.0D + 0.5D;
            double targetZ = cell.originZ() + cell.d() / 2.0D + 0.5D;
            double targetY = cell.originY() + cell.h() + 2.0D;
            Location location = new Location(world, targetX, targetY, targetZ);
            J.runEntity(player, () -> PaperLib.teleportAsync(player, location));
            IrisLogging.debug("Object Studio goto: %s -> %s at %.0f,%.0f,%.0f",
                    player.getName(), objectKey, location.getX(), location.getY(), location.getZ());
            return true;
        }
        return false;
    }

    static File resolveObjectsDir(IrisData data, Engine engine) {
        File root = data.getDataFolder();
        if (root == null) return null;
        if (root.toPath().toAbsolutePath().normalize().equals(
                engine.getData().getDataFolder().toPath().toAbsolutePath().normalize())) {
            root = engine.getPackSource().toFile();
        }
        File objects = new File(root, "objects");
        if (!objects.exists()) {
            objects.mkdirs();
        }
        return objects;
    }

    private void captureAndSave(ActiveStudio studio, World world, GridCell cell, Player notify) {
        if (!allChunksLoaded(world, cell)) {
            return;
        }

        IrisObject snapshot = studio.generator.createCapture(cell);
        int originX = cell.originX();
        int originY = cell.originY();
        int originZ = cell.originZ();

        boolean anyBlock = false;
        for (int dx = 0; dx < cell.w(); dx++) {
            for (int dy = 0; dy < cell.h(); dy++) {
                for (int dz = 0; dz < cell.d(); dz++) {
                    Block block = world.getBlockAt(originX + dx, originY + dy, originZ + dz);
                    if (block.getType() == Material.AIR) continue;
                    snapshot.setUnsigned(dx, dy, dz, block, false);
                    anyBlock = true;
                }
            }
        }

        String hashKey = cell.pack() + "/" + cell.key();
        long hash = hashOf(snapshot);
        Long prior = studio.hashes.get(hashKey);
        if (prior != null && prior == hash) {
            if (notify != null) {
                send(notify, IrisLanguage.text(BukkitRuntimeMessages.OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_NO_CHANGES, MessageArgument.untrusted("pack", String.valueOf(cell.pack())), MessageArgument.untrusted("key", String.valueOf(cell.key()))));
            }
            return;
        }

        if (!anyBlock && prior == null) {
            studio.hashes.put(hashKey, hash);
            if (notify != null) {
                send(notify, IrisLanguage.text(BukkitRuntimeMessages.OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_EMPTY_CELL_NOTHING_WRITE, MessageArgument.untrusted("pack", String.valueOf(cell.pack())), MessageArgument.untrusted("key", String.valueOf(cell.key()))));
            }
            return;
        }

        studio.hashes.put(hashKey, hash);

        File targetFile = objectFileFor(studio, cell);
        if (targetFile == null) {
            if (notify != null) {
                send(notify, IrisLanguage.text(BukkitRuntimeMessages.OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_NO_TARGET_FILE, MessageArgument.untrusted("pack", String.valueOf(cell.pack())), MessageArgument.untrusted("key", String.valueOf(cell.key()))));
            }
            return;
        }

        J.a(() -> {
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                snapshot.write(targetFile);
                IrisLogging.debug("Object Studio saved: %s/%s (%dx%dx%d)",
                        cell.pack(), cell.key(), cell.w(), cell.h(), cell.d());
                if (notify != null) {
                    J.runEntity(notify, () -> send(notify, IrisLanguage.text(BukkitRuntimeMessages.OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_SAVED, MessageArgument.untrusted("pack", String.valueOf(cell.pack())), MessageArgument.untrusted("key", String.valueOf(cell.key())))));
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
                if (notify != null) {
                    J.runEntity(notify, () -> send(notify, IrisLanguage.text(BukkitRuntimeMessages.OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_SAVE_FAILED, MessageArgument.untrusted("pack", String.valueOf(cell.pack())), MessageArgument.untrusted("key", String.valueOf(cell.key())), MessageArgument.untrusted("error", String.valueOf(e.getMessage())))));
                }
            }
        });
    }

    private static void send(Player player, String message) {
        ComponentMessenger.sendSection(player, message);
    }

    private boolean allChunksLoaded(World world, GridCell cell) {
        for (int cx = cell.chunkMinX(); cx <= cell.chunkMaxX(); cx++) {
            for (int cz = cell.chunkMinZ(); cz <= cell.chunkMaxZ(); cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static long hashOf(IrisObject snapshot) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            snapshot.write(baos);
            byte[] bytes = baos.toByteArray();
            long h = 1125899906842597L;
            for (byte b : bytes) {
                h = 31 * h + b;
            }
            return h;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return System.nanoTime();
        }
    }

    private static File objectFileFor(ActiveStudio studio, GridCell cell) {
        File objectsDir = studio.objectsDirs.get(cell.pack());
        if (objectsDir == null) return null;
        String relative = cell.key().replace('\\', '/');
        return new File(objectsDir, relative + ".iob");
    }

    private static final class ActiveStudio {
        final UUID worldId;
        final ObjectStudioGenerator generator;
        final ObjectStudioLayout layout;
        final Map<String, File> objectsDirs;
        final String packKey;
        final Map<String, Long> hashes = new ConcurrentHashMap<>();

        ActiveStudio(UUID worldId, ObjectStudioGenerator generator, Map<String, File> objectsDirs, String packKey) {
            this.worldId = worldId;
            this.generator = generator;
            this.layout = generator.getLayout();
            this.objectsDirs = objectsDirs;
            this.packKey = packKey;
        }
    }
}
