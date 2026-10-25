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

package art.arcane.iris.studio.map;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.studio.view.GuiOverlay;
import art.arcane.iris.studio.view.OpenerPosition;

import javax.swing.Timer;
import java.awt.EventQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Follows the region and biomes where the player who opened the map stands.
 */
final class PlayerLocationTracker {
    private static final int CHECK_INTERVAL_MILLIS = 500;

    private final Supplier<Engine> engine;
    private final GuiOverlay overlay;
    private final Listener listener;
    private final Timer timer;
    private final ExecutorService lookups = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Iris Map Player Location");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean checkQueued = new AtomicBoolean();
    private volatile PlayerLocation lastLocation;

    private PlayerLocationTracker(Supplier<Engine> engine, GuiOverlay overlay, Listener listener) {
        this.engine = engine;
        this.overlay = overlay;
        this.listener = listener;
        this.timer = new Timer(CHECK_INTERVAL_MILLIS, event -> queueCheck());
        MapHost.interruptOnPluginShutdown(lookups);
    }

    static PlayerLocationTracker follow(Supplier<Engine> engine, GuiOverlay overlay, Listener listener) {
        PlayerLocationTracker tracker = new PlayerLocationTracker(engine, overlay, listener);
        if (overlay == null) {
            EventQueue.invokeLater(() -> listener.onLocationChanged(PlayerLocation.OFFLINE));
            return tracker;
        }
        tracker.timer.setInitialDelay(0);
        tracker.timer.start();
        return tracker;
    }

    void stop() {
        timer.stop();
        lookups.shutdownNow();
    }

    private void queueCheck() {
        if (!checkQueued.compareAndSet(false, true)) {
            return;
        }
        overlay.requestOpenerPosition(position -> {
            try {
                lookups.execute(() -> publish(locate(position)));
            } catch (RuntimeException rejected) {
                checkQueued.set(false);
            }
        });
    }

    private void publish(PlayerLocation location) {
        checkQueued.set(false);
        if (location.equals(lastLocation)) {
            return;
        }
        lastLocation = location;
        EventQueue.invokeLater(() -> listener.onLocationChanged(location));
    }

    private PlayerLocation locate(OpenerPosition position) {
        if (position.status() == OpenerPosition.Status.OFFLINE) {
            return PlayerLocation.OFFLINE;
        }
        Engine current = engine.get();
        if (position.status() == OpenerPosition.Status.IN_OTHER_WORLD || current == null || current.isClosed()) {
            return PlayerLocation.IN_OTHER_WORLD;
        }
        int blockX = (int) Math.floor(position.x());
        int blockY = (int) Math.floor(position.y());
        int blockZ = (int) Math.floor(position.z());
        IrisRegion region = current.getRegion(blockX, blockZ);
        String surfaceBiome = keyOf(current.getSurfaceBiome(blockX, blockZ));
        String biomeAtPosition = keyOf(current.getBiomeOrMantle(blockX, blockY - current.getMinHeight(), blockZ));
        if (region == null || region.getLoadKey() == null || surfaceBiome == null) {
            return PlayerLocation.IN_OTHER_WORLD;
        }
        return new PlayerLocation(Status.IN_REGION, region.getLoadKey(), surfaceBiome, biomeAtPosition);
    }

    private static String keyOf(IrisBiome biome) {
        return biome == null ? null : biome.getLoadKey();
    }

    enum Status {
        OFFLINE,
        IN_OTHER_WORLD,
        IN_REGION
    }

    // Biomes are 3D: underground, the biome at the player's position can differ from the surface biome above it.
    record PlayerLocation(Status status, String regionKey, String surfaceBiomeKey, String biomeAtPositionKey) {
        static final PlayerLocation OFFLINE = new PlayerLocation(Status.OFFLINE, null, null, null);
        static final PlayerLocation IN_OTHER_WORLD = new PlayerLocation(Status.IN_OTHER_WORLD, null, null, null);

        boolean isBelowSurfaceBiome() {
            return biomeAtPositionKey != null && !biomeAtPositionKey.equals(surfaceBiomeKey);
        }
    }

    interface Listener {
        void onLocationChanged(PlayerLocation location);
    }
}
