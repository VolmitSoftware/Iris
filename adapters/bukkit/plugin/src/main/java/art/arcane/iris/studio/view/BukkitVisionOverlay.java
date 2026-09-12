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

package art.arcane.iris.studio.view;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static art.arcane.iris.platform.bukkit.registry.Attributes.MAX_HEALTH;

public final class BukkitVisionOverlay implements GuiOverlay {
    private final Engine engine;
    private final UUID openerId;
    private final AtomicBoolean nativeTeleportActive = new AtomicBoolean();
    private final AtomicBoolean playerRefreshQueued = new AtomicBoolean();
    private final AtomicLong teleportSequence = new AtomicLong();
    private final AtomicReference<VisionTeleportRequest> latestTeleport = new AtomicReference<>();
    private volatile List<GuiMarker> playerMarkers = List.of();

    public BukkitVisionOverlay(Engine engine, UUID openerId) {
        this.engine = engine;
        this.openerId = openerId;
    }

    /**
     * Called from the AWT event thread, so it may only hand back the last snapshot
     * built by a server thread.
     */
    @Override
    public List<GuiMarker> players() {
        queuePlayerRefresh();
        return playerMarkers;
    }

    private void queuePlayerRefresh() {
        if (!playerRefreshQueued.compareAndSet(false, true)) {
            return;
        }

        boolean scheduled = J.runGlobal(() -> {
            try {
                List<GuiMarker> markers = new ArrayList<>();
                for (Player player : BukkitWorldBinding.players(engine.getWorld())) {
                    Location at = player.getLocation();
                    markers.add(GuiMarker.player(player.getName(), at.getX(), at.getZ()));
                }
                playerMarkers = List.copyOf(markers);
            } finally {
                playerRefreshQueued.set(false);
            }
        });

        if (!scheduled) {
            playerRefreshQueued.set(false);
        }
    }

    @Override
    public void requestEntities(Consumer<List<GuiMarker>> sink) {
        J.runGlobal(() -> {
            IrisWorld target = engine.getWorld();
            World world = BukkitWorldBinding.world(target);
            if (world == null) {
                sink.accept(List.of());
                return;
            }

            List<LivingEntity> living = new ArrayList<>();
            for (LivingEntity entity : BukkitWorldBinding.entities(target, LivingEntity.class)) {
                if (!(entity instanceof Player)) {
                    living.add(entity);
                }
            }

            if (living.isEmpty()) {
                sink.accept(List.of());
                return;
            }

            List<GuiMarker> collected = Collections.synchronizedList(new ArrayList<>(living.size()));
            AtomicInteger pending = new AtomicInteger(living.size());
            Runnable complete = () -> {
                if (pending.decrementAndGet() == 0) {
                    sink.accept(List.copyOf(collected));
                }
            };

            for (LivingEntity entity : living) {
                Location at = entity.getLocation();
                Runnable read = () -> {
                    try {
                        collected.add(marker(entity, at));
                    } catch (Throwable ignored) {
                    } finally {
                        complete.run();
                    }
                };

                if (!J.runRegion(world, at.getBlockX() >> 4, at.getBlockZ() >> 4, read)) {
                    complete.run();
                }
            }
        });
    }

    private GuiMarker marker(LivingEntity entity, Location at) {
        String label = Form.capitalizeWords(entity.getType().name().toLowerCase(Locale.ROOT).replaceAll("\\Q_\\E", " "));
        double maxHealth = 0;
        try {
            maxHealth = entity.getAttribute(MAX_HEALTH).getValue();
        } catch (Throwable ignored) {
        }
        return GuiMarker.entity(label, at.getX(), at.getY(), at.getZ(), entity.getHealth(), maxHealth);
    }

    @Override
    public void teleport(double worldX, double worldZ) {
        VisionTeleportRequest request = new VisionTeleportRequest(
                teleportSequence.incrementAndGet(),
                VisionGUI.floorWorldCoordinate(worldX),
                VisionGUI.floorWorldCoordinate(worldZ));
        latestTeleport.set(request);
        startTeleport(request);
    }

    private void startTeleport(VisionTeleportRequest request) {
        if (!request.processing.compareAndSet(false, true)) {
            return;
        }
        boolean scheduled = J.runGlobal(() -> {
            IrisWorld target = engine.getWorld();
            if (!isCurrent(request, target)) {
                finish(request);
                return;
            }
            World world = BukkitWorldBinding.world(target);
            if (world == null) {
                finish(request);
                return;
            }
            List<Player> players = BukkitWorldBinding.players(target);
            Player player = selectPlayer(players);
            if (player == null) {
                finish(request);
                return;
            }
            int blockX = request.blockX;
            int blockZ = request.blockZ;
            try {
                int blockY = engine.getMinHeight() + engine.getHeight(blockX, blockZ, false) + 2;
                Location destination = new Location(
                        world,
                        blockX + 0.5D,
                        blockY,
                        blockZ + 0.5D);
                if (!J.runEntity(player, () -> delegateTeleport(
                        request,
                        target,
                        player,
                        world,
                        destination))) {
                    fail(request, target, world, new IllegalStateException(
                            "Failed to schedule the Vision teleport on the player entity."));
                }
            } catch (Throwable failure) {
                fail(request, target, world, failure);
            }
        });
        if (!scheduled) {
            finish(request);
        }
    }

    private Player selectPlayer(List<Player> players) {
        if (openerId == null) {
            return players.isEmpty() ? null : players.get(0);
        }
        for (Player player : players) {
            if (openerId.equals(player.getUniqueId())) {
                return player;
            }
        }
        return null;
    }

    private void delegateTeleport(
            VisionTeleportRequest request,
            IrisWorld target,
            Player player,
            World world,
            Location destination
    ) {
        if (!isCurrent(request, target) || !player.isOnline() || player.getWorld() != world) {
            finish(request);
            return;
        }
        if (!nativeTeleportActive.compareAndSet(false, true)) {
            finish(request);
            return;
        }
        if (!isCurrent(request, target)) {
            nativeTeleportActive.set(false);
            finish(request);
            restartLatest(request);
            return;
        }

        CompletableFuture<Boolean> teleport;
        try {
            teleport = BukkitPlatform.teleportAsync(player, destination);
        } catch (Throwable failure) {
            nativeTeleportActive.set(false);
            fail(request, target, world, failure);
            restartLatest(request);
            return;
        }
        if (teleport == null) {
            nativeTeleportActive.set(false);
            fail(request, target, world, new IllegalStateException(
                    "Vision teleport returned no completion future."));
            restartLatest(request);
            return;
        }
        teleport.whenComplete((success, failure) -> {
            nativeTeleportActive.set(false);
            finish(request);
            if (isCurrent(request, target)) {
                if (failure != null) {
                    reportTeleportFailure(world, request.blockX, request.blockZ, failure);
                } else if (!Boolean.TRUE.equals(success)) {
                    reportTeleportFailure(world, request.blockX, request.blockZ, new IllegalStateException(
                            "Vision teleport did not complete successfully."));
                }
            }
            restartLatest(request);
        });
    }

    private boolean isCurrent(VisionTeleportRequest request, IrisWorld target) {
        VisionTeleportRequest current = latestTeleport.get();
        return current != null
                && current.sequence == request.sequence
                && target != null
                && engine.getWorld() == target
                && target.hasPlatformWorld()
                && !engine.isClosing()
                && !engine.isClosed();
    }

    private void fail(
            VisionTeleportRequest request,
            IrisWorld target,
            World world,
            Throwable failure
    ) {
        finish(request);
        if (isCurrent(request, target)) {
            reportTeleportFailure(world, request.blockX, request.blockZ, failure);
        }
    }

    private void finish(VisionTeleportRequest request) {
        request.processing.set(false);
    }

    private void restartLatest(VisionTeleportRequest completed) {
        VisionTeleportRequest current = latestTeleport.get();
        if (current != null && current != completed) {
            startTeleport(current);
        }
    }

    private void reportTeleportFailure(World world, int blockX, int blockZ, Throwable failure) {
        IrisLogging.reportError("Vision could not teleport to " + world.getName() + "@"
                + blockX + "," + blockZ + ".", failure);
    }

    @Override
    public String openInEditor(double worldX, double worldZ, RenderType type) {
        IrisComplex complex = engine.getComplex();
        File file = switch (type) {
            case BIOME, LAYER_LOAD, DECORATOR_LOAD, OBJECT_LOAD, HEIGHT, RIVER ->
                    complex.getTrueBiomeStream().get(worldX, worldZ).openInVSCode();
            case BIOME_LAND -> complex.getLandBiomeStream().get(worldX, worldZ).openInVSCode();
            case BIOME_SEA -> complex.getSeaBiomeStream().get(worldX, worldZ).openInVSCode();
            case REGION -> complex.getRegionStream().get(worldX, worldZ).openInVSCode();
            case CAVE_LAND -> complex.getCaveBiomeStream().get(worldX, worldZ).openInVSCode();
            default -> null;
        };
        return file == null ? null : file.getName();
    }

    private static final class VisionTeleportRequest {
        private final long sequence;
        private final int blockX;
        private final int blockZ;
        private final AtomicBoolean processing;

        private VisionTeleportRequest(long sequence, int blockX, int blockZ) {
            this.sequence = sequence;
            this.blockX = blockX;
            this.blockZ = blockZ;
            processing = new AtomicBoolean(false);
        }
    }
}
