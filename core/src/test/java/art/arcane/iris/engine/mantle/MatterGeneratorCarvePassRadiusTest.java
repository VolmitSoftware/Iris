package art.arcane.iris.engine.mantle;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EnginePlatformHooks;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MatterGeneratorCarvePassRadiusTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();


    @Test
    @SuppressWarnings("unchecked")
    public void platformFilterRemovesSkippedComponentFromGenerationReach() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(true);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));

        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getPlatformHooks()).thenReturn(new EnginePlatformHooks() {
            @Override
            public boolean shouldGenerateMantleComponent(Engine target, MantleComponent component) {
                return component.getFlag() != ReservedFlag.OBJECT;
            }
        });

        RecordingComponent carving = new RecordingComponent(ReservedFlag.CARVED, 0, 0);
        RecordingComponent objects = new RecordingComponent(ReservedFlag.OBJECT, 1, 160);
        TestMatterGenerator generator = new TestMatterGenerator(engine, mantle, List.of(
                new MantlePass(List.of(carving), 11, 160),
                new MantlePass(List.of(objects), 10, 0)
        ));

        generator.generateMatter(0, 0, false, mock(ChunkContext.class));

        assertEquals(Set.of("0,0"), carving.visited);
        assertTrue(objects.visited.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void carvePassCoversEveryChunkTheObjectPassWritesObjectsInto() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(true);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));

        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);

        RecordingComponent carving = new RecordingComponent(ReservedFlag.CARVED, 0, 1);
        RecordingComponent objects = new RecordingComponent(ReservedFlag.OBJECT, 1, 40);

        TestMatterGenerator generator = new TestMatterGenerator(engine, mantle, List.of(
                new MantlePass(List.of(carving), Math.ceilDiv(1 + 40, 16), 40),
                new MantlePass(List.of(objects), Math.ceilDiv(40, 16), 0)
        ));
        generator.generateMatter(0, 0, false, mock(ChunkContext.class));

        assertTrue(objects.visited.size() > 9);
        assertEquals(objects.visited, carving.visited);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void chunkSpecificInputRadiusAvoidsUnusedPrerequisiteHalo() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(true);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));

        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);

        RecordingComponent carving = new RecordingComponent(ReservedFlag.CARVED, 0, 1);
        RecordingComponent conditional = new RecordingComponent(
                ReservedFlag.RIVER_HYDROLOGY,
                1,
                0,
                160,
                0
        );
        TestMatterGenerator generator = new TestMatterGenerator(engine, mantle, List.of(
                new MantlePass(List.of(carving), 11, 160),
                new MantlePass(List.of(conditional), 0, 0)
        ));

        generator.generateMatter(0, 0, false, mock(ChunkContext.class));

        assertEquals(9, carving.visited.size());
        assertEquals(Set.of("0,0"), conditional.visited);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void lazyInputGenerationKeepsReadAccessWithoutEagerlyGeneratingTheHalo() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(true);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));

        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);

        RecordingComponent carving = new RecordingComponent(ReservedFlag.CARVED, 0, 0);
        RecordingComponent conditional = new RecordingComponent(
                ReservedFlag.RIVER_HYDROLOGY,
                1,
                0,
                0,
                160,
                true,
                10
        );
        TestMatterGenerator generator = new TestMatterGenerator(engine, mantle, List.of(
                new MantlePass(List.of(carving), 10, 160),
                new MantlePass(List.of(conditional), 0, 0)
        ));

        generator.generateMatter(0, 0, false, mock(ChunkContext.class));

        assertEquals(Set.of("0,0"), carving.visited);
        assertEquals(Set.of("0,0"), conditional.visited);
        assertTrue(conditional.accessSucceeded);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void destinationOwnedOutputGeneratesOnlyDestinationsAndPreparesItsInputHalo() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(true);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));

        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);

        RecordingComponent carving = new RecordingComponent(ReservedFlag.CARVED, 0, 1);
        RecordingComponent objects = new RecordingComponent(
                ReservedFlag.OBJECT,
                1,
                40,
                0,
                0,
                false,
                0,
                0,
                89
        );
        TestMatterGenerator generator = new TestMatterGenerator(engine, mantle, List.of(
                new MantlePass(List.of(carving), 6, 89),
                new MantlePass(List.of(objects), 0, 0)
        ));

        generator.generateMatter(0, 0, false, mock(ChunkContext.class));

        assertEquals(Set.of("0,0"), objects.visited);
        assertEquals(169, carving.visited.size());
    }

    private static final class RecordingComponent implements MantleComponent {
        private final MantleFlag flag;
        private final int priority;
        private final int radius;
        private final int inputRadius;
        private final int chunkInputRadius;
        private final boolean lazyInputGeneration;
        private final int accessProbeOffset;
        private final int outputRadius;
        private final int destinationInputRadius;
        private final Set<String> visited = new LinkedHashSet<>();
        private boolean accessSucceeded;

        private RecordingComponent(MantleFlag flag, int priority, int radius) {
            this(flag, priority, radius, 0, 0, false, 0, radius, 0);
        }

        private RecordingComponent(
                MantleFlag flag,
                int priority,
                int radius,
                int inputRadius,
                int chunkInputRadius
        ) {
            this(flag, priority, radius, inputRadius, chunkInputRadius, false, 0, radius, 0);
        }

        private RecordingComponent(
                MantleFlag flag,
                int priority,
                int radius,
                int inputRadius,
                int chunkInputRadius,
                boolean lazyInputGeneration,
                int accessProbeOffset
        ) {
            this(flag, priority, radius, inputRadius, chunkInputRadius, lazyInputGeneration,
                    accessProbeOffset, radius, 0);
        }

        private RecordingComponent(
                MantleFlag flag,
                int priority,
                int radius,
                int inputRadius,
                int chunkInputRadius,
                boolean lazyInputGeneration,
                int accessProbeOffset,
                int outputRadius,
                int destinationInputRadius
        ) {
            this.flag = flag;
            this.priority = priority;
            this.radius = radius;
            this.inputRadius = inputRadius;
            this.chunkInputRadius = chunkInputRadius;
            this.lazyInputGeneration = lazyInputGeneration;
            this.accessProbeOffset = accessProbeOffset;
            this.outputRadius = outputRadius;
            this.destinationInputRadius = destinationInputRadius;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public int getRadius() {
            return radius;
        }

        @Override
        public int getOutputRadius() {
            return outputRadius;
        }

        @Override
        public int getInputRadius() {
            return inputRadius;
        }

        @Override
        public int getInputRadius(
                int targetChunkX,
                int targetChunkZ,
                int invocationChunkRadius,
                ChunkContext context
        ) {
            return destinationInputRadius > 0 ? destinationInputRadius : chunkInputRadius;
        }

        @Override
        public boolean isInputGenerationLazy() {
            return lazyInputGeneration;
        }

        @Override
        public EngineMantle getEngineMantle() {
            return null;
        }

        @Override
        public MantleFlag getFlag() {
            return flag;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void setEnabled(boolean enabled) {
        }

        @Override
        public void hotload() {
        }

        @Override
        public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
            visited.add(x + "," + z);
            if (accessProbeOffset != 0) {
                accessSucceeded = writer.acquireChunk(x + accessProbeOffset, z) != null;
            }
        }
    }

    private static final class TestMatterGenerator implements MatterGenerator {
        private final Engine engine;
        private final Mantle<Matter> mantle;
        private final List<MantlePass> components;

        private TestMatterGenerator(Engine engine, Mantle<Matter> mantle, List<MantlePass> components) {
            EngineMantle engineMantle = mock(EngineMantle.class);
            when(engine.getMantle()).thenReturn(engineMantle);
            when(engineMantle.getEngine()).thenReturn(engine);
            this.engine = engine;
            this.mantle = mantle;
            this.components = components;
        }

        @Override
        public Engine getEngine() {
            return engine;
        }

        @Override
        public Mantle<Matter> getMantle() {
            return mantle;
        }

        @Override
        public int getRadius() {
            return components.getFirst().passChunkRadius();
        }

        @Override
        public int getRealRadius() {
            return components.getLast().passChunkRadius();
        }

        @Override
        public List<MantlePass> getComponents() {
            return components;
        }
    }
}
