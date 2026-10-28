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

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.config.Feature;
import com.volmit.config.FeatureSettings;
import com.volmit.generation.MantleGenerationContext;
import com.volmit.host.LoadedPack;
import com.volmit.host.MantleRunner;
import com.volmit.host.ModuleHost;
import com.volmit.host.StageTimings;
import com.volmit.mantle.Mantle;
import com.volmit.stage.MantleStage;
import com.volmit.stage.Stage;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assume.assumeTrue;

/**
 * How much the any-column activation scan costs: a stage conditioned on settings makes the runner ask Iris's
 * settings at all 256 columns of the chunk, against the same stage without a condition. The engine answers from
 * fixed objects, so the result is the scan's own cost without Iris's (cached) region and biome stream reads. Runs
 * only with {@code IRIS_TIMING=1} and prints its result:
 * {@code IRIS_TIMING=1 ./gradlew :core:test --tests '*ChunkSettingsScanTimingTest' --rerun -i} (the switch is no task
 * input, hence {@code --rerun}).
 */
public class ChunkSettingsScanTimingTest {
    private static final String TIMING_SWITCH = "IRIS_TIMING";
    private static final int WARMUP_CHUNKS = 20_000;
    private static final int MEASURED_CHUNKS = 100_000;
    private static final int CHUNK_WIDTH = 16;
    private static final int BORDER_X = 8;
    private static final MantleStage CONDITIONED = Stage.mantle("test:scan/conditioned");
    private static final MantleStage PLAIN = Stage.mantle("test:scan/plain");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Feature(id = "test:scan", name = "Scan", key = "scan", targets = {Level.DIMENSION, Level.REGION, Level.BIOME})
    static final class ScanSettings implements IrisFeature {
    }

    @Test
    public void measuresTheScanPerChunkAndSettingsType() throws IOException {
        assumeTrue("set " + TIMING_SWITCH + "=1 to run", "1".equals(System.getenv(TIMING_SWITCH)));
        IrisModule scan = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:scan", "Scan", "1"); }
            public void setup(ModuleSetup setup) {
                setup.stages().define(CONDITIONED).when(ScanSettings.class).run(context -> context.configured(ScanSettings.class));
                setup.stages().define(PLAIN).run(context -> { });
            }
        };
        Engine engine = borderEngine();

        try (LoadedPack pack = LoadedPack.load(folder.newFolder("pack").toPath(),
                ModuleHost.of(List.of(scan)).copyingJarsTo(folder.newFolder("copies").toPath()))) {
            MantleRunner runner = new MantleRunner(pack.mantle());
            ChunkModuleSettings settings = new ChunkModuleSettings(engine, new PackResourceIds(folder.getRoot()));
            time(runner, PLAIN, settings, WARMUP_CHUNKS);
            time(runner, CONDITIONED, settings, WARMUP_CHUNKS);
            double plain = time(runner, PLAIN, settings, MEASURED_CHUNKS);
            double conditioned = time(runner, CONDITIONED, settings, MEASURED_CHUNKS);

            System.out.printf("Any-column scan: %.2f us per chunk and settings type (stage alone %.2f us, conditioned %.2f us)%n",
                    conditioned - plain, plain, conditioned);
        }
    }

    /** Microseconds per chunk to run {@code stage} for {@code chunks} chunks in a row. */
    private static double time(MantleRunner runner, MantleStage stage, FeatureSettings settings, int chunks) {
        long started = System.nanoTime();
        for (int chunk = 0; chunk < chunks; chunk++) {
            runner.generate(stage, inputs(chunk * CHUNK_WIDTH, settings), chunk, StageTimings.NONE);
        }
        return (System.nanoTime() - started) / 1_000D / chunks;
    }

    /** A dimension and region without the settings; a biome with them west of each chunk's middle, one without east of it. */
    private static Engine borderEngine() {
        IrisDimension dimension = new IrisDimension();
        IrisRegion region = new IrisRegion();
        IrisBiome configured = new IrisBiome();
        configured.setLoadKey("configured");
        configured.setModuleSettings(ModuleSettings.of(Map.of(ScanSettings.class, new ScanSettings())));
        IrisBiome plain = new IrisBiome();
        return (Engine) Proxy.newProxyInstance(Engine.class.getClassLoader(), new Class<?>[]{Engine.class}, (proxy, method, arguments) ->
                switch (method.getName()) {
                    case "getDimension" -> dimension;
                    case "getRegion" -> region;
                    case "getSurfaceBiome" -> Math.floorMod((int) arguments[0], CHUNK_WIDTH) < BORDER_X ? configured : plain;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static MantleGenerationContext inputs(int originX, FeatureSettings settings) {
        return new MantleGenerationContext() {
            public int originX() { return originX; }
            public int originZ() { return 0; }
            public TerrainSampler terrain() { throw new UnsupportedOperationException(); }
            public FeatureSettings settings() { return settings; }
            public Mantle mantle() { throw new UnsupportedOperationException(); }
            public PackWorld world() { throw new UnsupportedOperationException(); }
            public com.volmit.placement.PlacementTerrain placementTerrain() { throw new UnsupportedOperationException(); }
        };
    }
}
