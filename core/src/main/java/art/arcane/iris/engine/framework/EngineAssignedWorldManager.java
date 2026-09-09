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

package art.arcane.iris.engine.framework;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.RuntimeUiMessages;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.core.events.IrisEngineHotloadEvent;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public abstract class EngineAssignedWorldManager extends EngineAssignedComponent implements EngineWorldManager, BukkitEngineWorldManager, Listener {
    private final Object managerLifecycleLock;
    private final AtomicBoolean started;
    private boolean listenerRegistered;
    private boolean closeRequested;
    private int taskId;

    public EngineAssignedWorldManager() {
        super(null, null);
        managerLifecycleLock = new Object();
        started = new AtomicBoolean(false);
        taskId = -1;
    }

    public EngineAssignedWorldManager(Engine engine) {
        super(engine, "World");
        managerLifecycleLock = new Object();
        started = new AtomicBoolean(false);
        taskId = -1;
    }

    @Override
    public void start() {
        synchronized (managerLifecycleLock) {
            if (started.get()) {
                return;
            }
            if (closeRequested) {
                throw new IllegalStateException("Cannot restart a closed Iris world manager.");
            }

            started.set(true);
            try {
                listenerRegistered = true;
                registerManagerListener();
                taskId = scheduleManagerTick(() -> runManagerTask("bukkit_world_manager_tick", this::onTick));
            } catch (Throwable e) {
                started.set(false);
                closeRequested = true;
                Throwable cleanupFailure = closeManagerResources();
                if (cleanupFailure != null) {
                    e.addSuppressed(cleanupFailure);
                }
                throw propagate(e);
            }
        }
    }

    @EventHandler
    public void on(IrisEngineHotloadEvent e) {
        runManagerTask("bukkit_world_manager_hotload_event", () -> {
            for (Player i : BukkitWorldBinding.players(e.getEngine().getWorld())) {
                i.playSound(i.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.8f);
                VolmitSender s = new VolmitSender(i);
                s.sendAction(C.IRIS + IrisLanguage.text(RuntimeUiMessages.ENGINE_HOTLOADED));
            }
        });
    }

    @EventHandler
    public void on(WorldSaveEvent e) {
        runManagerTask("bukkit_world_manager_save", () -> {
            if (e.getWorld().equals(BukkitWorldBinding.world(getTarget().getWorld()))) {
                getEngine().requestSave();
            }
        });
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        runManagerTask("bukkit_world_manager_block_break", () -> {
            if (e.getPlayer().getWorld().equals(BukkitWorldBinding.world(getTarget().getWorld()))) {
                onBlockBreak(e);
            }
        });
    }

    @EventHandler
    public void on(BlockPlaceEvent e) {
        runManagerTask("bukkit_world_manager_block_place", () -> {
            if (e.getPlayer().getWorld().equals(BukkitWorldBinding.world(getTarget().getWorld()))) {
                onBlockPlace(e);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerTeleportEvent e) {
        Engine engine = getEngine();
        if (!BukkitPlatform.isPaperServer()
                || !engine.isStudio()
                || e.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {
            return;
        }

        Location destination = e.getTo();
        World targetWorld = BukkitWorldBinding.world(getTarget().getWorld());
        if (destination == null || targetWorld == null || !targetWorld.equals(destination.getWorld())) {
            return;
        }
        if (engine.isShuttingDown()) {
            e.setCancelled(true);
            return;
        }

        int chunkX = destination.getBlockX() >> 4;
        int chunkZ = destination.getBlockZ() >> 4;
        if (targetWorld.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        teleportAsync(e);
    }

    @EventHandler
    public void on(ChunkLoadEvent e) {
        runManagerTask("bukkit_world_manager_chunk_load", () -> {
            if (e.getChunk().getWorld().equals(BukkitWorldBinding.world(getTarget().getWorld()))) {
                onChunkLoad(e.getChunk(), e.isNewChunk());
            }
        });
    }

    @EventHandler
    public void on(ChunkUnloadEvent e) {
        runManagerTask("bukkit_world_manager_chunk_unload", () -> {
            if (e.getChunk().getWorld().equals(BukkitWorldBinding.world(getTarget().getWorld()))) {
                onChunkUnload(e.getChunk());
            }
        });
    }

    @Override
    public void close() {
        Throwable failure;
        synchronized (managerLifecycleLock) {
            closeRequested = true;
            started.set(false);
            failure = closeManagerResources();
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to completely stop the Iris world manager.", failure);
        }
    }

    protected void registerManagerListener() {
        BukkitPlatform.volmitPlugin().registerListener(this);
    }

    protected int scheduleManagerTick(Runnable tick) {
        return J.sr(tick, 1);
    }

    protected void unregisterManagerListener() {
        BukkitPlatform.volmitPlugin().unregisterListener(this);
    }

    protected void cancelManagerTick(int scheduledTaskId) {
        J.csr(scheduledTaskId);
    }

    private Throwable closeManagerResources() {
        Throwable failure = null;
        if (listenerRegistered) {
            try {
                unregisterManagerListener();
                listenerRegistered = false;
            } catch (Throwable e) {
                failure = e;
            }
        }
        if (taskId != -1) {
            try {
                cancelManagerTick(taskId);
                taskId = -1;
            } catch (Throwable e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }

    private RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(failure);
    }

    protected boolean runManagerTask(String operation, Runnable task) {
        if (!started.get()) {
            return false;
        }
        return EngineLifecycleTasks.run(getEngine(), operation, task);
    }

    protected <T> T callManagerTask(String operation, Supplier<T> task, T unavailable) {
        if (!started.get()) {
            return unavailable;
        }
        return EngineLifecycleTasks.call(getEngine(), operation, task, unavailable);
    }

    protected boolean isManagerStarted() {
        return started.get();
    }
}
