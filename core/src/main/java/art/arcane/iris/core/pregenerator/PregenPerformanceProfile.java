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

package art.arcane.iris.core.pregenerator;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.utility.CachedDoubleStream2D;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class PregenPerformanceProfile {
    private static final AtomicInteger PARALLELISM_HOLDERS = new AtomicInteger();

    private PregenPerformanceProfile() {
    }

    public static boolean apply() {
        IrisSettings.IrisSettingsPerformance performance = IrisSettings.get().getPerformance();
        int previousNoiseCacheSize = performance.getNoiseCacheSize();
        int targetNoiseCacheSize = Math.max(previousNoiseCacheSize, 4_096);
        boolean changed = false;

        if (targetNoiseCacheSize != previousNoiseCacheSize) {
            performance.setNoiseCacheSize(targetNoiseCacheSize);
            changed = true;
        }

        if (MultiBurst.burst.raiseParallelism(pregenBurstParallelism(Runtime.getRuntime().availableProcessors()))) {
            PARALLELISM_HOLDERS.incrementAndGet();
            changed = true;
        }

        return changed;
    }

    /**
     * Releases one hold on the pregeneration burst parallelism. The pool returns to the parallelism it had
     * before the first pregeneration raised it once the last concurrent job releases its hold.
     */
    public static boolean restore() {
        while (true) {
            int holders = PARALLELISM_HOLDERS.get();
            if (holders <= 0) {
                return false;
            }
            if (PARALLELISM_HOLDERS.compareAndSet(holders, holders - 1)) {
                return holders == 1 && MultiBurst.burst.restoreParallelism();
            }
        }
    }

    static int parallelismHolders() {
        return PARALLELISM_HOLDERS.get();
    }

    public static void apply(Engine engine) {
        apply();
        if (engine != null) {
            prepareNoiseCacheInPlace(engine);
            IrisLogging.info("Pregen profile applied: noiseCacheSize="
                    + IrisSettings.get().getPerformance().getNoiseCacheSize());
        }
    }

    public static void applyToGenerator(PlatformChunkGenerator generator) {
        apply(generator == null ? null : generator.getEngine());
    }

    static int pregenBurstParallelism(int availableProcessors) {
        return Math.max(4, availableProcessors * 2);
    }

    private static void prepareNoiseCacheInPlace(Engine engine) {
        IrisComplex complex = Objects.requireNonNull(engine.getComplex(), "Pregeneration requires an active biome complex");
        int configuredChunks = IrisSettings.get().getPerformance().getNoiseCacheSize();
        resizeNoiseCache(complex.getNaturalHeightStream(), configuredChunks);
        resizeNoiseCache(complex.getRawHeightStream(), configuredChunks);
    }

    private static void resizeNoiseCache(ProceduralStream<Double> stream, int configuredChunks) {
        if (!(stream instanceof CachedDoubleStream2D cachedStream)) {
            throw new IllegalStateException("Pregeneration requires a mutable double terrain cache");
        }
        cachedStream.setMaximumChunks(configuredChunks);
    }
}
