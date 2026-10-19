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

package com.volmit.iris.core.gui;

import com.volmit.iris.Iris;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisWorld;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.volmit.iris.util.data.registry.Attributes.MAX_HEALTH;

final class VisionOverlay {
    private final Engine engine;
    private final UUID openerId;
    private final AtomicBoolean playerRefreshQueued = new AtomicBoolean();
    private volatile List<GuiMarker> playerMarkers = List.of();

    VisionOverlay(Engine engine, UUID openerId) {
        this.engine = engine;
        this.openerId = openerId;
    }

    List<GuiMarker> lastKnownPlayers() {
        queuePlayerRefresh();
        return playerMarkers;
    }

    void requestEntities(Consumer<List<GuiMarker>> sink) {
        runOnServerThread(() -> sink.accept(collectEntities()));
    }

    void teleport(int blockX, int blockZ) {
        runOnServerThread(() -> teleportOpener(blockX, blockZ));
    }

    Optional<String> openInEditor(double worldX, double worldZ, RenderType type) {
        IrisComplex complex = engine.getComplex();
        File file = switch (type) {
            case BIOME, HEIGHT ->
                    complex.getTrueBiomeStream().get(worldX, worldZ).openInVSCode();
            case BIOME_LAND -> complex.getLandBiomeStream().get(worldX, worldZ).openInVSCode();
            case BIOME_SEA -> complex.getSeaBiomeStream().get(worldX, worldZ).openInVSCode();
            case REGION -> complex.getRegionStream().get(worldX, worldZ).openInVSCode();
            case CAVE_LAND -> complex.getCaveBiomeStream().get(worldX, worldZ).openInVSCode();
            case CONTINENT -> null;
        };
        return Optional.ofNullable(file).map(File::getName);
    }

    private void queuePlayerRefresh() {
        if (!playerRefreshQueued.compareAndSet(false, true)) {
            return;
        }

        boolean scheduled = runOnServerThread(() -> {
            try {
                playerMarkers = collectPlayers();
            } finally {
                playerRefreshQueued.set(false);
            }
        });
        if (!scheduled) {
            playerRefreshQueued.set(false);
        }
    }

    private List<GuiMarker> collectPlayers() {
        List<GuiMarker> markers = new ArrayList<>();
        for (Player player : engine.getWorld().getPlayers()) {
            Location at = player.getLocation();
            markers.add(GuiMarker.player(player.getName(), at.getX(), at.getZ()));
        }
        return List.copyOf(markers);
    }

    private List<GuiMarker> collectEntities() {
        IrisWorld world = engine.getWorld();
        if (!world.hasRealWorld()) {
            return List.of();
        }

        List<GuiMarker> markers = new ArrayList<>();
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
            if (!(entity instanceof Player)) {
                markers.add(marker(entity));
            }
        }
        return List.copyOf(markers);
    }

    private static GuiMarker marker(LivingEntity entity) {
        Location at = entity.getLocation();
        String label = Form.capitalizeWords(entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        double maxHealth = 0D;
        try {
            maxHealth = entity.getAttribute(MAX_HEALTH).getValue();
        } catch (Throwable ignored) {
        }
        return GuiMarker.entity(label, at.getX(), at.getY(), at.getZ(), entity.getHealth(), maxHealth);
    }

    private void teleportOpener(int blockX, int blockZ) {
        IrisWorld target = engine.getWorld();
        Player player = selectPlayer(target.getPlayers());
        if (player == null || !target.hasRealWorld() || engine.isClosed()) {
            return;
        }

        World world = target.realWorld();
        int blockY = engine.getMinHeight() + engine.getHeight(blockX, blockZ, false) + 2;
        Location destination = new Location(world, blockX + 0.5D, blockY, blockZ + 0.5D);
        player.teleportAsync(destination).whenComplete((success, failure) -> {
            if (failure != null) {
                Iris.reportError(failure);
                Iris.warn("Vision could not teleport %s to %d, %d.", player.getName(), blockX, blockZ);
            }
        });
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

    private static boolean runOnServerThread(Runnable task) {
        try {
            J.s(task);
            return true;
        } catch (Throwable e) {
            Iris.reportError(e);
            return false;
        }
    }
}
