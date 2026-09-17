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

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.MeteredCache;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.utility.CachedStream2D;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PregenPerformanceProfile {
    private static final long HOTLOAD_ACQUISITION_TIMEOUT_SECONDS = 30L;
    private static final AtomicBoolean JVM_HINT_LOGGED = new AtomicBoolean(false);

    private PregenPerformanceProfile() {
    }

    public static boolean apply() {
        IrisSettings.IrisSettingsPerformance performance = IrisSettings.get().getPerformance();
        int previousNoiseCacheSize = performance.getNoiseCacheSize();
        int targetNoiseCacheSize = Math.max(previousNoiseCacheSize, 4_096);
        boolean fastCacheEnabledBefore = Boolean.getBoolean("iris.cache.fast");
        boolean changed = false;

        if (targetNoiseCacheSize != previousNoiseCacheSize) {
            performance.setNoiseCacheSize(targetNoiseCacheSize);
            changed = true;
        }

        if (!fastCacheEnabledBefore) {
            System.setProperty("iris.cache.fast", "true");
            changed = true;
        }

        if (JVM_HINT_LOGGED.compareAndSet(false, true) && !fastCacheEnabledBefore) {
            IrisLogging.info("For startup-wide cache-fast coverage, set JVM argument: -Diris.cache.fast=true");
        }

        return changed;
    }

    public static void apply(Engine engine) {
        apply();
        if (requiresNoiseCacheRefresh(engine)) {
            engine.hotloadComplex();
            logApplied();
        }
    }

    public static void applyToGenerator(PlatformChunkGenerator generator) {
        apply();
        Engine engine = generator == null ? null : generator.getEngine();
        if (requiresNoiseCacheRefresh(engine)) {
            generator.hotloadComplexAsync(HOTLOAD_ACQUISITION_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
            logApplied();
        }
    }

    private static boolean requiresNoiseCacheRefresh(Engine engine) {
        if (engine == null) {
            return false;
        }
        IrisComplex complex = engine.getComplex();
        if (complex == null) {
            return true;
        }
        ProceduralStream<Double> heightStream = complex.getHeightStream();
        if (!(heightStream instanceof MeteredCache cache)) {
            return true;
        }
        ProceduralStream<IrisBiome> caveBiomeStream = complex.getCaveBiomeStream();
        if (!(caveBiomeStream instanceof CachedStream2D<?> cachedStream) || !cachedStream.usesFastCache()) {
            return true;
        }
        long configuredMaxSize = (long) IrisSettings.get().getPerformance().getNoiseCacheSize() * 256L;
        return cache.getMaxSize() != configuredMaxSize;
    }

    private static void logApplied() {
        IrisLogging.info("Pregen profile applied: noiseCacheSize=" + IrisSettings.get().getPerformance().getNoiseCacheSize() + " iris.cache.fast=" + Boolean.getBoolean("iris.cache.fast"));
    }
}
