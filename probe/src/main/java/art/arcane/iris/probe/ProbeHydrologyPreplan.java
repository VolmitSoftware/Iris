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

package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyTileCache;
import art.arcane.iris.generation.hydrology.HydrologyTileKey;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

record ProbeHydrologyPreplan(int tiles, int tileSize, int publicationRadius, int reachChunks, long elapsedNanos) {
    private static final int PLANNING_THREADS = 4;

    static Optional<ProbeHydrologyPreplan> run(Engine engine, GenerationProbe.ProbeConfiguration configuration)
            throws Exception {
        IrisHydrologyRuntime hydrology = engine.getComplex().getHydrologyRuntime();
        if (hydrology == null) {
            return Optional.empty();
        }
        HydrologyPlannerSettings settings = hydrology.settings();
        int tileSize = settings.routing().tileSize();
        int publicationRadius = settings.publicationRadius();
        int reachChunks = Math.max(engine.getMantle().getRadius(), engine.getMantle().getRealRadius());
        List<HydrologyTileKey> keys = tiles(configuration, tileSize, publicationRadius, reachChunks);
        long started = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(keys.size(), PLANNING_THREADS));
        try {
            List<Future<?>> plans = new ArrayList<>(keys.size());
            for (HydrologyTileKey key : keys) {
                plans.add(executor.submit(() -> hydrology.tile(key)));
            }
            for (Future<?> plan : plans) {
                await(plan);
            }
        } finally {
            executor.shutdown();
        }
        return Optional.of(new ProbeHydrologyPreplan(keys.size(), tileSize, publicationRadius, reachChunks,
                System.nanoTime() - started));
    }

    static List<HydrologyTileKey> tiles(GenerationProbe.ProbeConfiguration configuration, int tileSize,
                                        int publicationRadius, int reachChunks) {
        HydrologyTileCache.PregenerationArea area = GenerationProbe.pregenerationArea(configuration);
        long reach = (long) reachChunks * 16L + publicationRadius;
        int minimumX = Math.toIntExact(Math.floorDiv(area.minimumBlockX() - reach, tileSize)) - 1;
        int minimumZ = Math.toIntExact(Math.floorDiv(area.minimumBlockZ() - reach, tileSize)) - 1;
        int maximumX = Math.toIntExact(Math.floorDiv(area.maximumBlockX() + reach, tileSize)) + 1;
        int maximumZ = Math.toIntExact(Math.floorDiv(area.maximumBlockZ() + reach, tileSize)) + 1;
        int centerX = Math.floorDiv(area.centerBlockX(), tileSize);
        int centerZ = Math.floorDiv(area.centerBlockZ(), tileSize);
        List<HydrologyTileKey> keys = new ArrayList<>((maximumX - minimumX + 1) * (maximumZ - minimumZ + 1));
        for (int tileZ = minimumZ; tileZ <= maximumZ; tileZ++) {
            for (int tileX = minimumX; tileX <= maximumX; tileX++) {
                keys.add(new HydrologyTileKey(tileX, tileZ));
            }
        }
        keys.sort(Comparator.comparingInt((HydrologyTileKey key) ->
                Math.max(Math.abs(key.tileX() - centerX), Math.abs(key.tileZ() - centerZ))));
        return keys;
    }

    String machineLine() {
        return String.format(Locale.ROOT,
                "IRIS_GENPROBE_HYDROLOGY_PREPLAN version=1 tiles=%d tile_size=%d publication_radius=%d reach_chunks=%d planned_ms=%.3f",
                tiles, tileSize, publicationRadius, reachChunks, elapsedNanos / 1_000_000D);
    }

    private static void await(Future<?> plan) throws Exception {
        try {
            plan.get();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(cause);
        }
    }
}
