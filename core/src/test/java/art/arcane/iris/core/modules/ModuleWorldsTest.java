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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.SeedManager;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.ModuleSetup;
import com.volmit.PackContext;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.world.PackWorld;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The world modules see for an engine runtime: it starts once the runtime is published, its state is the store built-in
 * stages share, it restarts with a fresh store when the pack's resources reload, and it stops with the runtime.
 */
public class ModuleWorldsTest {
    private static final long SEED = 1337L;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<String> calls = new ArrayList<>();
    private LoadedPack pack;
    private IrisData data;
    private Engine engine;
    private String failingHook;

    /** What a world keeps for the recording module; closing it is recorded too. */
    public static final class Remembered implements AutoCloseable {
        private final List<String> calls;

        Remembered(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void close() {
            calls.add("closed state");
        }
    }

    /** Records its hooks and builds its state when a world starts. */
    public final class RecordingModule implements IrisModule, ModuleLifecycle {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("test:recording", "Recording", "1");
        }

        @Override
        public void setup(ModuleSetup setup) {
            setup.registry().registerLifecycle(this);
        }

        @Override
        public void packLoaded(PackContext pack) {
            record("loaded");
        }

        @Override
        public void worldStarted(PackWorld world) {
            record("started");
            world.state(Remembered.class, ignored -> new Remembered(calls));
        }

        @Override
        public void warmUp(PackWorld world) {
            record("warmed");
        }

        @Override
        public void worldStopped(PackWorld world) {
            record("stopped");
        }

        private void record(String hook) {
            calls.add(hook);
            if (hook.equals(failingHook)) {
                throw new IllegalStateException("cannot be " + hook);
            }
        }
    }

    @Before
    public void servePack() throws IOException {
        pack = LoadedPack.load(folder.newFolder("pack").toPath(),
                ModuleHost.of(List.of(new RecordingModule())).copyingJarsTo(folder.newFolder("copies").toPath()));
        data = mock(IrisData.class);
        PackModuleSet modules = mock(PackModuleSet.class);
        when(modules.pack()).thenReturn(pack);
        when(data.getModules()).thenReturn(modules);
        File root = folder.getRoot();
        when(data.getDataFolder()).thenReturn(root);
        reloadResources();
        engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getSeed()).thenReturn(SEED);
        when(engine.getSeedManager()).thenReturn(seeds);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void theWorldStartsWithThePackSnapshotAndTheEngineSeedAndSharesItsStoreWithBuiltInStages() {
        EngineGeneration generation = generation();

        generation.startModules();

        IrisPackWorld world = generation.world();
        assertEquals(List.of("loaded", "started"), calls);
        assertEquals(SEED, world.seed());
        assertSame(data.getPackResources(), world.resources());
        assertSame(engine, IrisPackWorld.of(world).engine());
        Remembered built = world.state(Remembered.class, ignored -> new Remembered(calls));
        assertSame(built, generation.state(Remembered.class, ignored -> new Remembered(calls)));
    }

    @Test
    public void reloadedResourcesRestartTheWorldWithAnEmptyStoreAndCloseTheOldStateLater() {
        EngineGeneration generation = generation();
        generation.startModules();
        IrisPackWorld first = generation.world();
        Remembered firstState = first.state(Remembered.class, ignored -> new Remembered(calls));
        calls.clear();

        reloadResources();
        generation.restartModules();

        IrisPackWorld second = generation.world();
        assertNotSame(first, second);
        assertSame(data.getPackResources(), second.resources());
        assertEquals(List.of("stopped", "loaded", "started"), calls);
        assertNotSame(firstState, second.state(Remembered.class, ignored -> new Remembered(calls)));
        assertSame("a running invocation may still use the old world", firstState, first.state(Remembered.class, ignored -> null));

        calls.clear();
        reloadResources();
        generation.restartModules();
        assertEquals(List.of("stopped", "closed state", "loaded", "started"), calls);
        assertThrows(IllegalStateException.class, () -> first.state(Remembered.class, ignored -> null));
    }

    @Test
    public void closingTheRuntimeStopsTheWorldAndClosesItsState() {
        EngineGeneration generation = generation();
        generation.startModules();
        IrisPackWorld world = generation.world();
        calls.clear();

        generation.close();
        generation.close();

        assertEquals(List.of("stopped", "closed state"), calls);
        assertThrows(IllegalStateException.class, () -> world.state(Remembered.class, ignored -> null));
    }

    @Test
    public void theWarmUpRunsOnlyForAStartedWorldAndWarmsTheWorldItStarted() {
        EngineGeneration generation = generation();
        generation.warmModules();
        assertEquals("nothing started yet", List.of(), calls);

        generation.startModules();
        generation.warmModules();
        assertEquals(List.of("loaded", "started", "warmed"), calls);

        reloadResources();
        generation.restartModules();
        calls.clear();
        generation.warmModules();
        assertEquals("the restarted world is warmed too", List.of("warmed"), calls);

        generation.close();
        calls.clear();
        generation.warmModules();
        assertEquals("a closed runtime warms nothing", List.of(), calls);
    }

    @Test
    public void aFailedWarmUpNamesTheModuleAndLeavesTheWorldRunning() {
        failingHook = "warmed";
        EngineGeneration generation = generation();
        generation.startModules();

        IllegalStateException failure = assertThrows(IllegalStateException.class, generation::warmModules);

        assertTrue(failure.getMessage(), failure.getMessage().contains("test:recording"));
        generation.world().state(Remembered.class, ignored -> new Remembered(calls));
    }

    @Test
    public void everyWorldOfOneSnapshotStartsButThePackLoadsOnce() {
        EngineGeneration first = generation();
        EngineGeneration second = generation();

        first.startModules();
        second.startModules();

        assertEquals(List.of("loaded", "started", "started"), calls);
    }

    @Test
    public void aRuntimeThatStartsAfterAReloadStartsWithTheNewSnapshot() {
        EngineGeneration generation = generation();
        IrisPackWorld madeEarly = generation.world();

        reloadResources();
        generation.startModules();

        assertNotSame(madeEarly, generation.world());
        assertSame(data.getPackResources(), generation.world().resources());
    }

    @Test
    public void aModuleThatFailsToStartStopsTheRuntimeFromStartingAndIsNotStoppedLater() {
        failingHook = "started";
        EngineGeneration generation = generation();

        IllegalStateException failure = assertThrows(IllegalStateException.class, generation::startModules);
        assertTrue(failure.getMessage(), failure.getMessage().contains("test:recording"));

        calls.clear();
        generation.close();
        assertEquals(List.of(), calls);
    }

    @Test
    public void aPackWhoseModulesFailToLoadRunsItsWorldWithoutHooks() {
        PackModuleSet broken = mock(PackModuleSet.class);
        when(broken.pack()).thenThrow(new PackModulesException(folder.getRoot(), new IllegalArgumentException("broken jar")));
        when(data.getModules()).thenReturn(broken);
        EngineGeneration generation = generation();

        generation.startModules();
        generation.world().state(Remembered.class, ignored -> new Remembered(calls));
        generation.close();

        assertEquals(List.of("closed state"), calls);
    }

    private EngineGeneration generation() {
        return new EngineGeneration(engine);
    }

    /** A new snapshot of the pack's resources, as {@link IrisData#reloadModuleResources()} makes. */
    private void reloadResources() {
        IrisPackResources resources = new IrisPackResources(data, List::of);
        when(data.getPackResources()).thenReturn(resources);
    }
}
