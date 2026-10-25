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

import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.runtime.Engine;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

record ProbeHydrologyPreplan(int windows, long elapsedNanos) {
    static final int RINGS = 1;
    private static final int CHUNKS_PER_WINDOW = WindowKey.SIZE >> 4;

    static Optional<ProbeHydrologyPreplan> run(Engine engine, GenerationProbe.ProbeConfiguration configuration)
            throws Exception {
        HydrologyRuntime hydrology = engine.getComplex().getHydrologyRuntime();
        if (hydrology == null || !hydrology.enabled()) {
            return Optional.empty();
        }
        int[] bounds = GenerationProbe.scheduledChunkBounds(configuration);
        long started = System.nanoTime();
        try {
            hydrology.prepareWindowsAround(bounds[0], bounds[1], bounds[2], bounds[3], RINGS).get();
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
        return Optional.of(new ProbeHydrologyPreplan(windows(bounds), System.nanoTime() - started));
    }

    static int windows(int[] chunkBounds) {
        int width = Math.floorDiv(chunkBounds[2], CHUNKS_PER_WINDOW) - Math.floorDiv(chunkBounds[0], CHUNKS_PER_WINDOW) + 1 + 2 * RINGS;
        int depth = Math.floorDiv(chunkBounds[3], CHUNKS_PER_WINDOW) - Math.floorDiv(chunkBounds[1], CHUNKS_PER_WINDOW) + 1 + 2 * RINGS;
        return width * depth;
    }

    String machineLine() {
        return String.format(Locale.ROOT, "IRIS_GENPROBE_HYDROLOGY_PREPLAN version=2 windows=%d planned_ms=%.3f", windows,
                elapsedNanos / 1_000_000D);
    }
}
