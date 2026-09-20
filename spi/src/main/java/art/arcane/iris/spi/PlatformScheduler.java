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

package art.arcane.iris.spi;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;

/**
 * Platform task dispatch; region scheduling targets the owning region thread on regionized platforms and the global thread elsewhere.
 * <p>
 * Every method is safe to call from any thread. Delivery timing is deliberately unspecified: submitting from the
 * thread that already owns the target may run the task inline before returning, or queue it for the next tick,
 * depending on the adapter. Callers must assume neither - do not treat return as completion, and do not assume
 * the task has not already run. Only a {@code later*} call with a positive delay guarantees a deferral. Tasks that throw are
 * reported rather than killing the scheduler, and there is no handle to cancel with; a task that must stop early
 * checks its own state.
 * <p>
 * Internal to Iris; not a published integration surface.
 */
public interface PlatformScheduler {
    /**
     * Runs {@code task} on the server thread - the global region thread on regionized platforms.
     */
    void global(Runnable task);

    /**
     * Runs {@code task} on the thread owning chunk {@code (chunkX, chunkZ)} in {@code world}, which is that
     * chunk's region thread on regionized platforms and the server thread elsewhere. Use for anything that
     * touches blocks or entities in a known chunk. Adapters that cannot resolve a region owner fall back to
     * {@link #global(Runnable)}.
     */
    void region(NativeWorld world, int chunkX, int chunkZ, Runnable task);

    /**
     * Runs {@code task} on a pooled thread off the server thread. Must not touch world state.
     */
    void async(Runnable task);

    /**
     * {@link #global(Runnable)} delayed by {@code ticks}. A non-positive delay degrades to a plain
     * {@link #global(Runnable)}, inheriting its unspecified timing.
     */
    void laterGlobal(Runnable task, int ticks);

    /**
     * {@link #region(NativeWorld, int, int, Runnable)} delayed by {@code ticks}, with the same non-positive-delay
     * degradation as {@link #laterGlobal(Runnable, int)}. Adapters without regions fall back to the delayed global
     * queue.
     */
    void laterRegion(NativeWorld world, int chunkX, int chunkZ, Runnable task, int ticks);
}
