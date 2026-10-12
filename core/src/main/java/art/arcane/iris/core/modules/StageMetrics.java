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

package art.arcane.iris.core.modules;

import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.volmlib.util.atomics.AtomicRollingSequence;
import com.volmit.host.ResolvedStage;
import com.volmit.host.StageTimings;

import java.util.Map;
import java.util.function.Function;

/**
 * Iris's generation metrics for the built-in chunk stages that Iris timed under a name of its own before they were
 * modules, so studio and the integration metrics keep showing them: Iris times each such stage around its run
 * instead of the module timing itself. A ported built-in that reported a metric adds its stage here.
 */
final class StageMetrics implements StageTimings {
    private static final double NANOS_PER_MILLISECOND = 1_000_000D;
    private static final Map<String, Function<EngineMetrics, AtomicRollingSequence>> NAMED = Map.of(
            "iris:post/smooth", EngineMetrics::getPost,
            "iris:perfection/surface", EngineMetrics::getPerfection,
            "iris:carve/apply", EngineMetrics::getCave);

    private final EngineMetrics metrics;

    /** {@code metrics} may be null, as for a mocked engine: nothing is recorded then. */
    StageMetrics(EngineMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void record(ResolvedStage stage, long nanos) {
        Function<EngineMetrics, AtomicRollingSequence> named = NAMED.get(stage.stage().id().toString());
        if (metrics == null || named == null) {
            return;
        }
        named.apply(metrics).put(nanos / NANOS_PER_MILLISECOND);
    }
}
