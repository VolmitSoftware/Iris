package com.volmit.example.world;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.ModuleSetup;
import com.volmit.host.ChunkRunner;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.LifecycleRunner;
import com.volmit.host.LoadedModules;
import com.volmit.host.StageTimings;
import com.volmit.service.ObjectsAt;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.Stage;
import com.volmit.support.ColumnSettings;
import com.volmit.support.TestContexts;
import com.volmit.support.TestWorld;
import com.volmit.world.PackWorld;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;

/** A module that remembers, per world, which chunks it planted ruins in: its chunk stage writes the world's state,
 * its {@link ObjectsAt} service reads it, and its lifecycle builds the state when the world starts.
 */
class WorldStateExampleTest {
    static final class RuinsModule implements IrisModule, ModuleLifecycle {
        static final ChunkStage<Void> PLANT = Stage.chunk("example:ruins/plant");

        /** What ruins keeps per world. A field of the module would be shared by every world. */
        static final class PlantedChunks {
            final long seed;
            final Set<Long> chunks = ConcurrentHashMap.newKeySet();

            PlantedChunks(PackWorld world) {
                this.seed = world.seed();
            }
        }

        public ModuleInfo info() { return ModuleInfo.of("example:ruins", "Ruins", "1"); }

        public void setup(ModuleSetup setup) {
            setup.stages().define(PLANT).run(RuinsModule::plant);
            var registry = setup.registry();
            registry.registerLifecycle(this);
            registry.registerService(ObjectsAt.class, new ObjectsAt() {
                @Override
                public Set<String> objectsAt(PackWorld world, int chunkX, int chunkZ) {
                    return RuinsModule.objectsAt(world, chunkX, chunkZ);
                }

                @Override
                public PlacedBy placedBy(PackWorld world, String objectKey) {
                    return PlacedBy.NOTHING;
                }
            });
        }

        @Override
        public void worldStarted(PackWorld world) {
            planted(world);
        }

        static PlantedChunks planted(PackWorld world) {
            return world.state(PlantedChunks.class, PlantedChunks::new);
        }

        private static void plant(ChunkStageContext context) {
            planted(context.world()).chunks.add(key(context.chunkX(), context.chunkZ()));
        }

        private static Set<String> objectsAt(PackWorld world, int chunkX, int chunkZ) {
            return planted(world).chunks.contains(key(chunkX, chunkZ)) ? Set.of("ruins/arch") : Set.of();
        }

        private static long key(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
        }
    }

    @Test
    void stagesServicesAndHooksShareTheStateOfTheirWorldAndANewWorldStartsEmpty() {
        var pack = LoadedModules.load(List.of(DiscoveredModule.builtIn(new RuinsModule())), Map.of(), Set.of(), Set.of());
        var lifecycles = new LifecycleRunner(pack.catalog());
        ObjectsAt objects = pack.catalog().services(ObjectsAt.class).getFirst();

        try (var world = new TestWorld()) {
            lifecycles.worldStarted(world);
            var startedWith = RuinsModule.planted(world);
            assertEquals(TestContexts.SEED, startedWith.seed);

            new ChunkRunner(pack.chunk()).generate(TestContexts.generation(Map.of(), ColumnSettings.NONE, null, world), TestContexts.SEED,
                    StageTimings.NONE);

            assertSame(startedWith, RuinsModule.planted(world), "the stage used the state the hook built");
            assertEquals(Set.of("ruins/arch"), objects.objectsAt(world, 0, 0));
            assertEquals(Set.of(), objects.objectsAt(world, 1, 0));
            lifecycles.worldStopped(world);
        }

        try (var reloaded = new TestWorld()) {
            assertEquals(Set.of(), objects.objectsAt(reloaded, 0, 0), "a new world starts with empty state");
        }
    }
}
