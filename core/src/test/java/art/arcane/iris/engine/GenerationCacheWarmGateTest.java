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

package art.arcane.iris.engine;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.SeedManager;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.stage.StageGraph;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GenerationCacheWarmGateTest {
    private static final long WAIT_SECONDS = 10;

    @Test
    public void theFirstWorldIsWarmOnlyAfterTheWarmUpReturned() {
        GenerationCacheWarmGate gate = new GenerationCacheWarmGate();
        List<String> steps = new ArrayList<>();
        assertTrue(gate.isPending());

        gate.warmFirstWorld(() -> {
            steps.add("modules");
            assertTrue(gate.isPending());
        });

        assertEquals(List.of("modules"), steps);
        assertFalse(gate.isPending());
        gate.await();
    }

    /** A module whose warm-up takes as long as the test says, and records the world it warmed. */
    private static final class SlowWarmUpModule implements IrisModule, ModuleLifecycle {
        private final CountDownLatch warming = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile PackWorld warmed;

        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("test:slow", "Slow", "1");
        }

        @Override
        public void install(StageGraph graph) {
        }

        @Override
        public void register(FeatureRegistry registry) {
            registry.registerLifecycle(this);
        }

        @Override
        public void warmUp(PackWorld world) {
            warming.countDown();
            awaitQuietly(release);
            warmed = world;
        }
    }

    private static EngineGeneration generationOf(SlowWarmUpModule module) {
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture.serve(data);
        ModuleResourceFixture.serveModules(data, module);
        SeedManager seeds = mock(SeedManager.class);
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getSeedManager()).thenReturn(seeds);
        return new EngineGeneration(engine);
    }

    /** Worlds the modules are handed are views of the generation's world, so they are told apart by what they share. */
    private static final class WorldMarker {
    }

    @Test
    public void generationWaitsForAModulesSlowWarmUp() throws Exception {
        GenerationCacheWarmGate gate = new GenerationCacheWarmGate();
        SlowWarmUpModule module = new SlowWarmUpModule();
        EngineGeneration generation = generationOf(module);
        generation.startModules();
        ExecutorService background = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> warmUp = CompletableFuture.runAsync(
                    () -> gate.warmFirstWorld(generation::warmModules), background);
            assertTrue(module.warming.await(WAIT_SECONDS, TimeUnit.SECONDS));
            CompletableFuture<Void> generating = CompletableFuture.runAsync(gate::await);

            assertThrows(TimeoutException.class, () -> generating.get(200, TimeUnit.MILLISECONDS));
            assertTrue(gate.isPending());

            module.release.countDown();
            warmUp.get(WAIT_SECONDS, TimeUnit.SECONDS);
            generating.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertFalse(gate.isPending());
            assertSame("the module warmed the world it started",
                    generation.world().state(WorldMarker.class, ignored -> new WorldMarker()),
                    module.warmed.state(WorldMarker.class, ignored -> new WorldMarker()));
        } finally {
            module.release.countDown();
            background.shutdownNow();
        }
    }

    @Test
    public void aFailedWarmUpFailsWhatWaitsForIt() {
        GenerationCacheWarmGate gate = new GenerationCacheWarmGate();

        assertThrows(IllegalStateException.class, () -> gate.warmFirstWorld(() -> {
            throw new IllegalStateException("host cannot warm");
        }));

        IllegalStateException refused = assertThrows(IllegalStateException.class, gate::await);
        assertEquals("host cannot warm", refused.getCause().getMessage());
    }

    @Test
    public void worldsStartedLaterHoldGenerationUntilTheyAreWarmToo() {
        GenerationCacheWarmGate gate = new GenerationCacheWarmGate();
        gate.skipFirstWorld();
        assertFalse(gate.isPending());

        CompletableFuture<Void> hotloaded = gate.beginRestartedWorld();
        CompletableFuture<Void> restarted = gate.beginRestartedWorld();
        assertTrue(gate.isPending());

        hotloaded.complete(null);
        assertTrue("the second world is still warming", gate.isPending());
        restarted.complete(null);
        assertFalse(gate.isPending());
        gate.await();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
