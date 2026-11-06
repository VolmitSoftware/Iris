package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLog;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.config.PackSettings;
import com.volmit.generation.GenerationContext;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.Mantle;
import com.volmit.placement.Placements;
import com.volmit.resource.PackResources;
import com.volmit.stage.Pipeline;
import com.volmit.stage.Stage;
import com.volmit.support.TestContexts;
import com.volmit.support.TestSetups;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import com.volmit.world.TerrainSampler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** What a host offers its own modules beyond the API reaches the stages through the world and the invocation. */
class ExtensionsTest {
    /** Something a host offers for a whole world. */
    record WorldOffer(String name) {}

    /** Something a host offers for one invocation. */
    record ChunkOffer(int chunkX) {}

    @Test
    void aStageReachesWhatTheHostOffersForItsWorldAndItsInvocation() {
        var seen = new ArrayList<Object>();
        var stage = Stage.chunk("test:offers/read");
        IrisModule module = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:offers", "Offers", "1"); }
            public void setup(ModuleSetup setup) {
                setup.stages().define(stage).run(context -> {
                    seen.add(context.world().extension(WorldOffer.class));
                    seen.add(context.extension(ChunkOffer.class));
                    seen.add(context.world().extension(ChunkOffer.class));
                    seen.add(context.extension(WorldOffer.class));
                    seen.add(context.world().log() == ModuleLogs.discarding() ? "host's log" : "module's log");
                });
            }
        };
        var world = new OfferingWorld(new WorldOffer("iris"));
        var inputs = new OfferingChunk(TestContexts.generation(null, null, null, world), new ChunkOffer(0));

        new ChunkRunner(TestSetups.stages(module).resolve(Pipeline.CHUNK)).generate(inputs, TestContexts.SEED, StageTimings.NONE);

        assertEquals(List.of(Optional.of(new WorldOffer("iris")), Optional.of(new ChunkOffer(0)), Optional.empty(), Optional.empty(),
                "module's log"), seen, "the module's view of the world keeps the host's offers, with the module's own log");
    }

    @Test
    void aModulesViewOfAWorldCreatesStateWithThatView() {
        var world = new OfferingWorld(new WorldOffer("iris"));
        var log = ModuleLogs.discarding();
        PackWorld view = ModuleWorld.of(ModuleWorld.of(world, ModuleLogs.discarding()), log);

        PackWorld createdWith = view.state(PackWorld.class, Function.identity());

        assertSame(view, createdWith);
        assertSame(createdWith, world.state(PackWorld.class, ignored -> null), "the state lives in the host's world");
        assertEquals(Optional.of(new WorldOffer("iris")), view.extension(WorldOffer.class));
    }

    private static final class OfferingWorld implements PackWorld {
        private final WorldStates states = new WorldStates();
        private final WorldOffer offer;

        OfferingWorld(WorldOffer offer) {
            this.offer = offer;
        }

        public long seed() { return TestContexts.SEED; }
        public PackResources resources() { throw new AssertionError("Unused resources"); }
        public PackSettings settings() { throw new AssertionError("Unused settings"); }
        public int minWorldY() { return 0; }
        public Optional<RunningWorld> running() { return Optional.empty(); }
        public Placements placements() { throw new AssertionError("Unused placements"); }
        public <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create) { return states.state(type, () -> create.apply(this)); }
        public ModuleLog log() { return ModuleLogs.discarding(); }

        @Override
        public <T> Optional<T> extension(Class<T> type) {
            return type.isInstance(offer) ? Optional.of(type.cast(offer)) : Optional.empty();
        }
    }

    private record OfferingChunk(GenerationContext inputs, ChunkOffer offer) implements GenerationContext {
        public int originX() { return inputs.originX(); }
        public int originZ() { return inputs.originZ(); }
        public TerrainSampler terrain() { return inputs.terrain(); }
        public FeatureSettings settings() { return inputs.settings(); }
        public Mantle mantle() { return inputs.mantle(); }
        public Hunk<BlockState> blocks() { return inputs.blocks(); }
        public PackWorld world() { return inputs.world(); }

        @Override
        public <T> Optional<T> extension(Class<T> type) {
            return type.isInstance(offer) ? Optional.of(type.cast(offer)) : Optional.empty();
        }
    }
}
