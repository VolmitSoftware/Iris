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

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.GenerationCounter;
import art.arcane.volmlib.util.cache.CacheContention;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

record ProbeWindowMetrics(Map<String, EngineMetrics.StageTotal> stages, Map<String, Long> counters) {
    static ProbeWindowMetrics capture(Engine engine, boolean counters) {
        return new ProbeWindowMetrics(engine.getMetrics().stageTotals(), counters ? counterValues() : Map.of());
    }

    static String stagesLine(ProbeWindowMetrics start, ProbeWindowMetrics end, int chunks, int threads) {
        StringBuilder line = new StringBuilder(512);
        line.append("IRIS_GENPROBE_STAGES version=1 chunks=").append(chunks).append(" threads=").append(threads);
        for (Map.Entry<String, EngineMetrics.StageTotal> entry : end.stages().entrySet()) {
            EngineMetrics.StageTotal earlier = start.stages().get(entry.getKey());
            EngineMetrics.StageTotal window = earlier == null ? entry.getValue() : entry.getValue().since(earlier);
            line.append(' ').append(entry.getKey()).append('=')
                    .append(String.format(Locale.ROOT, "%.3f", window.millis()));
        }
        return line.toString();
    }

    static String countersLine(ProbeWindowMetrics start, ProbeWindowMetrics end, int chunks) {
        StringBuilder line = new StringBuilder(256);
        line.append("IRIS_GENPROBE_COUNTERS version=1 chunks=").append(chunks);
        for (Map.Entry<String, Long> entry : end.counters().entrySet()) {
            long window = entry.getValue() - start.counters().getOrDefault(entry.getKey(), 0L);
            line.append(' ').append(entry.getKey()).append('=').append(window)
                    .append(' ').append(entry.getKey()).append("_per_chunk=")
                    .append(String.format(Locale.ROOT, "%.1f", chunks == 0 ? 0D : window / (double) chunks));
        }
        return line.toString();
    }

    private static Map<String, Long> counterValues() {
        Map<String, Long> values = new LinkedHashMap<>();
        for (GenerationCounter counter : GenerationCounter.values()) {
            values.put(counter.key(), counter.sum());
        }
        values.put("cache_duplicate_computations", CacheContention.duplicateComputations());
        return values;
    }
}
