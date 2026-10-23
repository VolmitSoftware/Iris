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
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.ModuleRegistry;
import com.volmit.host.ModuleSetups;
import com.volmit.host.ResolvedStage;
import com.volmit.host.StageGraphBuilder;
import com.volmit.stage.Pipeline;
import com.volmit.stage.Stage;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Iris times the built-in stages it reported under names of their own, post, perfection and biome among them, around their runs. */
public class StageMetricsTest {
    private static final long TWO_MILLISECONDS = 2_000_000L;

    @Test
    public void namedBuiltInStagesFeedTheirMetricAndOthersNothing() {
        EngineMetrics metrics = new EngineMetrics(4);
        StageMetrics timings = new StageMetrics(metrics);

        for (ResolvedStage stage : stages("iris:post/smooth", "iris:perfection/surface", "iris:biome/write", "iris:other/work")) {
            timings.record(stage, TWO_MILLISECONDS);
        }

        assertEquals(2D, metrics.getPost().getAverage(), 0D);
        assertEquals(2D, metrics.getPerfection().getAverage(), 0D);
        assertEquals(2D, metrics.getBiome().getAverage(), 0D);
    }

    @Test
    public void anEngineWithoutMetricsRecordsNothing() {
        new StageMetrics(null).record(stages("iris:post/smooth").getFirst(), TWO_MILLISECONDS);
    }

    private static List<ResolvedStage> stages(String... ids) {
        StageGraphBuilder builder = new StageGraphBuilder();
        for (String id : ids) {
            String module = id.substring(0, id.indexOf('/'));
            ModuleSetups.run(DiscoveredModule.builtIn(new IrisModule() {
                @Override
                public ModuleInfo info() {
                    return ModuleInfo.of(module, module, "test");
                }

                @Override
                public void setup(ModuleSetup setup) {
                    setup.stages().define(Stage.chunk(id)).run(context -> {
                    });
                }
            }), builder, new ModuleRegistry());
        }
        return builder.resolve(Pipeline.CHUNK).stages();
    }
}
