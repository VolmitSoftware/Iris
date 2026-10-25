package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleWriterTerrainAccessTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    @Test
    public void terrainReachFollowsWhatEachTerrainComponentWritesAndReads() {
        MantleComponent carving = new Recording(ReservedFlag.CARVED, 0, 1, 0, 0, MatterGenerationPhase.TERRAIN, 0, new ArrayList<>());
        MantleComponent pools = new Recording(ReservedFlag.RIVER_HYDROLOGY, 1, 16, 16, 16, MatterGenerationPhase.TERRAIN, 0, new ArrayList<>());
        MantleComponent falls = new Recording(ReservedFlag.RIVER_HYDROLOGY, 1, 0, 0, 0, MatterGenerationPhase.TERRAIN, 0, new ArrayList<>());
        MantleComponent spill = new Recording(ReservedFlag.RIVER_HYDROLOGY, 1, 16, 16, 0, MatterGenerationPhase.TERRAIN, 0, new ArrayList<>());

        assertArrayEquals(new int[]{2, 1}, MantleWriter.terrainReaches(new MantleComponent[]{carving, pools}));
        assertArrayEquals("terrain that reads and writes only its own chunk stays on the accessed chunk", new int[]{0, 0},
                MantleWriter.terrainReaches(new MantleComponent[]{carving, falls}));
        assertArrayEquals(new int[]{0}, MantleWriter.terrainReaches(new MantleComponent[]{carving}));
        assertArrayEquals("a component that writes into neighbours without reading them still widens the reach", new int[]{1, 1},
                MantleWriter.terrainReaches(new MantleComponent[]{carving, spill}));
    }

    @Test
    public void carvingWritesOnlyItsOwnChunk() {
        assertTrue(new MantleCarvingComponent(mock(EngineMantle.class)).getWriteRadius() == 0);
    }

    @Test
    public void contentAccessBeyondTheEagerWindowFinishesTheNeighbourhoodTerrainFirst() {
        Harness harness = new Harness();

        harness.generate();

        assertTrue(harness.runs.contains("OBJECT 0,0"));
        for (int x = 5 - 1; x <= 5 + 1; x++) {
            for (int z = -1; z <= 1; z++) {
                int pool = harness.runs.indexOf("RIVER_HYDROLOGY " + x + "," + z);
                assertTrue("pools ran at " + x + "," + z, pool >= 0);
                for (int carvedX = x - 1; carvedX <= x + 1; carvedX++) {
                    for (int carvedZ = z - 1; carvedZ <= z + 1; carvedZ++) {
                        int carve = harness.runs.indexOf("CARVED " + carvedX + "," + carvedZ);
                        assertTrue("carving at " + carvedX + "," + carvedZ + " precedes pools at " + x + "," + z, carve >= 0 && carve < pool);
                    }
                }
            }
        }
    }

    @Test
    public void plannedFlaggedAndHistoricalNeighboursAreLeftAlone() {
        Harness harness = new Harness();
        harness.flag(6, 0, MantleFlag.PLANNED);
        harness.flag(4, 1, ReservedFlag.CARVED);
        harness.flag(5, -1, ReservedFlag.RIVER_HYDROLOGY);
        harness.historical.add(Harness.key(5, 2));

        harness.generate();

        assertFalse(harness.runs.contains("CARVED 6,0"));
        assertFalse(harness.runs.contains("RIVER_HYDROLOGY 6,0"));
        assertFalse(harness.runs.contains("CARVED 4,1"));
        assertTrue(harness.runs.contains("RIVER_HYDROLOGY 4,1"));
        assertFalse(harness.runs.contains("RIVER_HYDROLOGY 5,-1"));
        assertTrue(harness.runs.contains("CARVED 5,-1"));
        assertFalse(harness.runs.contains("CARVED 5,2"));
        assertTrue(harness.runs.contains("CARVED 5,1"));
        assertTrue(harness.runs.contains("RIVER_HYDROLOGY 5,1"));
    }

    private static final class Harness {
        private final List<String> runs = new ArrayList<>();
        private final Map<Long, Set<MantleFlag>> flags = new HashMap<>();
        private final Map<Long, MantleChunk<Matter>> chunks = new HashMap<>();
        private final Set<Long> historical = new HashSet<>();
        private final Engine engine = mock(Engine.class);
        private final Mantle<Matter> mantle;

        @SuppressWarnings("unchecked")
        private Harness() {
            IrisDimension dimension = mock(IrisDimension.class);
            when(dimension.isUseMantle()).thenReturn(true);
            mantle = mock(Mantle.class);
            when(mantle.getChunk(anyInt(), anyInt())).thenAnswer(call -> chunk(call.getArgument(0), call.getArgument(1)));
            when(mantle.useChunk(anyInt(), anyInt())).thenAnswer(call -> chunk(call.getArgument(0), call.getArgument(1)));
            when(engine.getDimension()).thenReturn(dimension);
            EngineMantle engineMantle = mock(EngineMantle.class);
            when(engine.getMantle()).thenReturn(engineMantle);
            when(engineMantle.getEngine()).thenReturn(engine);
        }

        private void flag(int chunkX, int chunkZ, MantleFlag flag) {
            flags.computeIfAbsent(key(chunkX, chunkZ), ignored -> new HashSet<>()).add(flag);
        }

        @SuppressWarnings("unchecked")
        private MantleChunk<Matter> chunk(int chunkX, int chunkZ) {
            return chunks.computeIfAbsent(key(chunkX, chunkZ), key -> {
                MantleChunk<Matter> chunk = mock(MantleChunk.class);
                Set<MantleFlag> raised = flags.computeIfAbsent(key, ignored -> new HashSet<>());
                when(chunk.use()).thenReturn(chunk);
                when(chunk.isFlagged(any(MantleFlag.class))).thenAnswer(call -> raised.contains(call.<MantleFlag>getArgument(0)));
                doAnswer(call -> {
                    MantleFlag flag = call.getArgument(0);
                    if (!raised.contains(flag)) {
                        call.<Runnable>getArgument(1).run();
                        raised.add(flag);
                    }
                    return null;
                }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));
                return chunk;
            });
        }

        private void generate() {
            Recording carving = new Recording(ReservedFlag.CARVED, 0, 1, 0, 0, MatterGenerationPhase.TERRAIN, 0, runs);
            Recording pools = new Recording(ReservedFlag.RIVER_HYDROLOGY, 1, 16, 16, 16, MatterGenerationPhase.TERRAIN, 0, runs);
            Recording objects = new Recording(ReservedFlag.OBJECT, 2, 0, 0, 100, MatterGenerationPhase.CONTENT, 5, runs);
            List<MantlePass> passes = List.of(
                    new MantlePass(List.of(carving), 3, 33),
                    new MantlePass(List.of(pools), 1, 0),
                    new MantlePass(List.of(objects), 0, 0));
            IrisComplex complex = mock(IrisComplex.class);
            when(complex.allowsMantleChunkWrite(anyInt(), anyInt()))
                    .thenAnswer(call -> !historical.contains(key(call.getArgument(0), call.getArgument(1))));
            ChunkContext context = mock(ChunkContext.class);
            when(context.getComplex()).thenReturn(complex);
            MatterGenerator generator = new MatterGenerator() {
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
                    return 3;
                }

                @Override
                public int getRealRadius() {
                    return 0;
                }

                @Override
                public List<MantlePass> getComponents() {
                    return passes;
                }
            };
            generator.generateContentMatter(0, 0, false, context);
        }

        private static long key(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        }
    }

    private static final class Recording implements MantleComponent {
        private final MantleFlag flag;
        private final int priority;
        private final int output;
        private final int write;
        private final int input;
        private final MatterGenerationPhase phase;
        private final int accessOffset;
        private final List<String> runs;

        private Recording(MantleFlag flag, int priority, int output, int write, int input, MatterGenerationPhase phase, int accessOffset,
                          List<String> runs) {
            this.flag = flag;
            this.priority = priority;
            this.output = output;
            this.write = write;
            this.input = input;
            this.phase = phase;
            this.accessOffset = accessOffset;
            this.runs = runs;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public MatterGenerationPhase getGenerationPhase() {
            return phase;
        }

        @Override
        public int getRadius() {
            return output;
        }

        @Override
        public int getWriteRadius() {
            return write;
        }

        @Override
        public int getInputRadius() {
            return input;
        }

        @Override
        public int getEagerInputRadius(int inputRadius) {
            return phase == MatterGenerationPhase.CONTENT ? 0 : inputRadius;
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
            runs.add(flag.name() + " " + x + "," + z);
            if (accessOffset != 0) {
                writer.acquireChunk(x + accessOffset, z);
            }
        }
    }
}
