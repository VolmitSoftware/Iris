package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleObjectComponentPlacementDeterminismTest {
    private static final Position LEFT = new Position(15, 10, 0);
    private static final Position RIGHT = new Position(16, 10, 0);
    private static final Position SUPPORT = new Position(15, 11, 0);

    @Test
    public void ordinaryObjectsKeepPrerequisitesAndSameSourceReadsAcrossDestinationOrders() throws Exception {
        Fixture forward = new Fixture();
        forward.generate(List.of(0, 1));
        Fixture reverse = new Fixture();
        reverse.generate(List.of(1, 0));
        Fixture parallel = new Fixture();
        parallel.generateParallel();
        Map<Position, String> expected = Map.of(
                LEFT, "minecraft:mangrove_wood",
                RIGHT, "minecraft:mangrove_wood",
                SUPPORT, "minecraft:mangrove_wood"
        );

        assertEquals(expected, forward.snapshot());
        assertEquals(expected, reverse.snapshot());
        assertEquals(expected, parallel.snapshot());
        assertEquals(12, parallel.sourceCalls.size());
        for (Integer calls : parallel.sourceCalls.values()) {
            assertEquals(1, calls.intValue());
        }
    }

    @Test
    public void ordinaryObjectSourcePlansRemainReusableAfterNeighborPublicationAndHotload() {
        Fixture fixture = new Fixture();
        fixture.generate(List.of(1, 0));
        Map<Position, String> expected = fixture.snapshot();
        Map<Chunk, Integer> calls = Map.copyOf(fixture.sourceCalls);

        fixture.generate(List.of(0, 1));

        assertEquals(expected, fixture.snapshot());
        assertEquals(calls, fixture.sourceCalls);
        fixture.component.hotload();
        fixture.generate(List.of(0, 1));
        assertEquals(expected, fixture.snapshot());
        for (Map.Entry<Chunk, Integer> entry : fixture.sourceCalls.entrySet()) {
            assertEquals(2, entry.getValue().intValue());
        }
    }

    private static final class Fixture {
        private final NativeBlockState stone = state("minecraft:stone");
        private final NativeBlockState oak = state("minecraft:oak_wood");
        private final NativeBlockState mangrove = state("minecraft:mangrove_wood");
        private final NativeBlockState wrongRead = state("minecraft:dirt");
        private final Map<Position, NativeBlockState> published = new ConcurrentHashMap<>();
        private final Map<Chunk, Integer> sourceCalls = new ConcurrentHashMap<>();
        private final ThreadLocal<Chunk> destination = new ThreadLocal<>();
        private final MantleWriter writer;
        private final MantleObjectComponent component;
        private final ChunkContext context = mock(ChunkContext.class);

        @SuppressWarnings("unchecked")
        private Fixture() {
            IrisDimension dimension = mock(IrisDimension.class);
            when(dimension.getAllRegions(any())).thenReturn(new KList<>());
            when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
            Engine engine = mock(Engine.class);
            when(engine.getDimension()).thenReturn(dimension);
            EngineMantle engineMantle = mock(EngineMantle.class);
            when(engineMantle.getEngine()).thenReturn(engine);
            Mantle<Matter> mantle = mock(Mantle.class);
            when(mantle.getWorldHeight()).thenReturn(64);
            writer = mock(MantleWriter.class);
            when(writer.getMantle()).thenReturn(mantle);
            when(writer.getEngine()).thenReturn(engine);
            when(writer.getPrerequisiteBlock(anyInt(), anyInt(), anyInt())).thenReturn(stone);
            when(writer.get(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> published.getOrDefault(
                    new Position(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)),
                    stone));
            doAnswer(invocation -> {
                destination.set(new Chunk(invocation.getArgument(0), invocation.getArgument(1)));
                try {
                    Runnable task = invocation.getArgument(2);
                    task.run();
                } finally {
                    destination.remove();
                }
                return null;
            }).when(writer).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
            doAnswer(invocation -> {
                Position position = new Position(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
                NativeBlockState value = invocation.getArgument(3);
                assertEquals(new Chunk(position.x() >> 4, position.z() >> 4), destination.get());
                published.put(position, value);
                return null;
            }).when(writer).setData(anyInt(), anyInt(), anyInt(), any(NativeBlockState.class));
            component = new OrdinaryObjects(engineMantle, this);
        }

        private void generate(List<Integer> destinations) {
            for (Integer chunkX : destinations) {
                component.generateLayer(writer, chunkX, 0, context);
            }
        }

        private void generateParallel() throws Exception {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<?> left = executor.submit(() -> generateAfterBarrier(0, ready, start));
                Future<?> right = executor.submit(() -> generateAfterBarrier(1, ready, start));
                assertTrue(ready.await(5L, TimeUnit.SECONDS));
                start.countDown();
                left.get(5L, TimeUnit.SECONDS);
                right.get(5L, TimeUnit.SECONDS);
            }
        }

        private void generateAfterBarrier(int chunkX, CountDownLatch ready, CountDownLatch start) {
            ready.countDown();
            try {
                assertTrue(start.await(5L, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
            component.generateLayer(writer, chunkX, 0, context);
        }

        private Map<Position, String> snapshot() {
            Map<Position, String> result = new HashMap<>();
            for (Map.Entry<Position, NativeBlockState> entry : published.entrySet()) {
                result.put(entry.getKey(), entry.getValue().key());
            }
            return result;
        }

        private static NativeBlockState state(String key) {
            NativeBlockState state = mock(NativeBlockState.class);
            when(state.key()).thenReturn(key);
            return state;
        }
    }

    private static final class OrdinaryObjects extends MantleObjectComponent {
        private final Fixture fixture;

        private OrdinaryObjects(EngineMantle engineMantle, Fixture fixture) {
            super(engineMantle);
            this.fixture = fixture;
        }

        @Override
        protected int computeRadius() {
            return 16;
        }

        @Override
        void generateOrigin(ObjectPassPlacer placer, int chunkX, int chunkZ, ChunkContext context) {
            fixture.sourceCalls.merge(new Chunk(chunkX, chunkZ), 1, Integer::sum);
            if (chunkZ != 0 || chunkX < 0 || chunkX > 1) {
                return;
            }
            NativeBlockState prerequisite = placer.get(LEFT.x(), LEFT.y(), LEFT.z());
            NativeBlockState wood = chunkX == 0 ? fixture.oak : fixture.mangrove;
            placer.set(SUPPORT.x(), SUPPORT.y(), SUPPORT.z(), wood);
            boolean sameSourceSupport = placer.get(SUPPORT.x(), SUPPORT.y(), SUPPORT.z()) == wood;
            NativeBlockState placed = prerequisite == fixture.stone && sameSourceSupport ? wood : fixture.wrongRead;
            placer.set(LEFT.x(), LEFT.y(), LEFT.z(), placed);
            placer.set(RIGHT.x(), RIGHT.y(), RIGHT.z(), placed);
        }
    }

    private record Position(int x, int y, int z) {
    }

    private record Chunk(int x, int z) {
    }
}
