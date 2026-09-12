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

import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.world.task.J;
import org.bukkit.World;

/**
 * Bukkit adapter dispatching platform tasks through the Folia-aware J scheduler.
 */
public final class BukkitScheduler implements PlatformScheduler {
    @Override
    public void global(Runnable task) {
        J.s(task);
    }

    @Override
    public void region(PlatformWorld world, int chunkX, int chunkZ, Runnable task) {
        World bukkitWorld = (World) world.nativeHandle();
        if (!J.runRegion(bukkitWorld, chunkX, chunkZ, task)) {
            J.s(task);
        }
    }

    @Override
    public void async(Runnable task) {
        J.a(task);
    }

    @Override
    public void laterGlobal(Runnable task, int ticks) {
        J.s(task, ticks);
    }

    @Override
    public void laterRegion(PlatformWorld world, int chunkX, int chunkZ, Runnable task, int ticks) {
        World bukkitWorld = (World) world.nativeHandle();
        if (!J.runRegion(bukkitWorld, chunkX, chunkZ, task, ticks)) {
            J.s(task, ticks);
        }
    }
}
