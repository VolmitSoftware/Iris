/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.engine.framework;

import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.core.modules.ModuleChunkGeneration;
import art.arcane.iris.core.modules.ModuleWorlds;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.documentation.BlockCoordinates;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * How every Iris world generates a chunk: the mantle around it first, then the pack's chunk pipeline, Iris's
 * built-in stages included. It also owns the world modules see for this engine runtime ({@link ModuleWorlds}), whose
 * state store keeps what stages and services hold per world, such as an actuator's caches, so a hotload, which builds
 * a new runtime, starts them fresh.
 */
public final class EngineGeneration {
    private static final int CHUNK_SHIFT = 4;
    private static final long MAINTENANCE_LOG_INTERVAL_MILLIS = 5000L;
    private static final AtomicLong lastMaintenanceBypassLog = new AtomicLong(0L);

    private final Engine engine;
    private final ModuleWorlds modules;

    public EngineGeneration(Engine engine) {
        this.engine = engine;
        this.modules = new ModuleWorlds(engine);
    }

    /**
     * The one {@code type} instance of the world this runtime generates, created by {@code create} the first time a
     * stage asks; the same store as {@link IrisPackWorld#state}. See {@link com.volmit.world.PackWorld#state} for
     * what {@code create} may do.
     */
    public <T> T state(Class<T> type, Function<Engine, T> create) {
        return modules.current().state(type, ignored -> create.apply(engine));
    }

    /** The world modules see now: stage contexts, services and lifecycle hooks get it. */
    public IrisPackWorld world() {
        return modules.current();
    }

    /** Starts the modules' world once the runtime is published; a module whose hook fails stops the runtime from starting. */
    public void startModules() {
        modules.start();
    }

    /** Restarts the modules' world with the pack's reloaded resources; see {@link ModuleWorlds#restart()}. */
    public void restartModules() {
        modules.restart();
    }

    @BlockCoordinates
    public void generate(int x, int z, Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes, boolean multicore, long generationSessionId) {
        boolean cacheContext = !engine.getPlatformHooks().shouldDisableChunkContextCache(engine);
        ChunkContext.PrefillPlan prefillPlan = cacheContext ? ChunkContext.PrefillPlan.NO_CAVE : ChunkContext.PrefillPlan.NONE;
        ChunkContext context = new ChunkContext(x, z, engine.getComplex(), generationSessionId, cacheContext, prefillPlan, engine.getMetrics());
        try (IrisContext.Scope chunkScope = IrisContext.open(engine, generationSessionId, context)) {
            generateMantle(x, z, multicore, context);
            ModuleChunkGeneration.generate(engine, x, z, blocks, biomes, multicore, context);
        }
    }

    /**
     * Whether stages backed by the mantle skip this chunk: on Folia, maintenance regeneration runs without the
     * mantle to stay region-safe.
     */
    public boolean bypassesMantleStages() {
        boolean bypass = engine.getPlatformHooks().shouldBypassMantleStages(engine);
        if (bypass) {
            logMaintenanceBypass();
        }
        return bypass;
    }

    /** Stops the modules' world and closes the state stages kept, such as actuators that hold engine resources. */
    public void close() {
        modules.close();
    }

    public static boolean shouldDisableContextCacheForMaintenance(boolean maintenanceActive, boolean pregeneratorTargetsWorld) {
        return maintenanceActive && !pregeneratorTargetsWorld;
    }

    private void generateMantle(int x, int z, boolean multicore, ChunkContext context) {
        if (bypassesMantleStages()) {
            return;
        }
        engine.getMantle().generateMatter(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT, multicore || engine.isStudio(), context);
    }

    private static void logMaintenanceBypass() {
        long now = System.currentTimeMillis();
        long last = lastMaintenanceBypassLog.get();
        if (now - last >= MAINTENANCE_LOG_INTERVAL_MILLIS && lastMaintenanceBypassLog.compareAndSet(last, now)) {
            IrisLogging.info("Maintenance regen bypass: skipping mantle-backed stages for Folia safety.");
        }
    }
}
