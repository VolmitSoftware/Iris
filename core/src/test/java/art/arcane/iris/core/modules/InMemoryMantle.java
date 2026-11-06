package art.arcane.iris.core.modules;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.util.project.matter.IrisMatterSupport;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.volmit.data.Codec;
import com.volmit.geometry.BlockBox;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;

import java.io.DataOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A mantle held in memory, for tests of what stages write into it. Bind an engine to it before the engine's mantle is used. */
public final class InMemoryMantle {
    public static final int REACH_BLOCKS = 1024;

    private final int sections;
    private final Map<Long, MantleChunk<Matter>> chunks = new HashMap<>();

    /** @param sections how many 16 block sections high the mantle is */
    public InMemoryMantle(int sections) {
        IrisMatterSupport.ensureRegistered();
        this.sections = sections;
    }

    /** The carved voids of this mantle as a mantle stage writes them, over the columns from -{@link #REACH_BLOCKS} up to {@link #REACH_BLOCKS}. */
    public MantleLayer<CarvedVoid> carved() {
        BlockBox reach = new BlockBox(-REACH_BLOCKS, 0, -REACH_BLOCKS, REACH_BLOCKS, sections * 16, REACH_BLOCKS);
        return new CarvedVoids(reach, this::chunk, new PackResourceIds(new File("test")), true);
    }

    public MantleChunk<Matter> chunk(int chunkX, int chunkZ) {
        return chunks.computeIfAbsent(((long) chunkX << 32) ^ (chunkZ & 0xffffffffL), ignored ->
                new MantleChunk<>(sections, chunkX, chunkZ, new Sections(), MantleHooks.NONE));
    }

    /** Whether any chunk has been created, which is whether anything asked for a chunk. */
    public boolean isTouched() {
        return !chunks.isEmpty();
    }

    /** Makes {@code engine}'s mantle this one. The platform must be bound already: the engine mantle's constants read it. */
    @SuppressWarnings("unchecked")
    public void bindTo(Engine engine) {
        Mantle<Matter> mantle = mock(Mantle.class);
        when(mantle.getChunk(anyInt(), anyInt())).thenAnswer(call -> chunk(call.getArgument(0), call.getArgument(1)));
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(engine.getMantle()).thenReturn(engineMantle);
    }

    /** What the layer holds at a block of the mantle's storage coordinates, or null. */
    public <T> T value(MantleKey<T> key, Codec<T> codec, int x, int mantleY, int z) {
        MantleChunk<Matter> chunk = chunk(x >> 4, z >> 4);
        if (!chunk.exists(mantleY >> 4) || !chunk.get(mantleY >> 4).hasSlice(ModuleLayerValues.class)) {
            return null;
        }
        ModuleLayerValues values = chunk.get(mantleY >> 4).<ModuleLayerValues>getSlice(ModuleLayerValues.class).get(x & 15, mantleY & 15, z & 15);
        return values == null ? null : values.get(key, codec);
    }

    /** Iris's in-memory mantle sections, without the engine's static state that would bind a platform for every later test. */
    public static final class Sections implements MantleDataAdapter<Matter> {
        public Matter createSection() { return new IrisMatter(16, 16, 16); }
        public Matter readSection(CountingDataInputStream input) { throw new UnsupportedOperationException(); }
        public void writeSection(Matter section, DataOutputStream output) { throw new UnsupportedOperationException(); }
        public void trimSection(Matter section) { section.trimSlices(); }
        public boolean isSectionEmpty(Matter section) { return section.getSliceMap().isEmpty(); }
        public Class<?> classifyValue(Object value) { return value.getClass(); }
        public <T> void set(Matter section, int x, int y, int z, Class<?> type, T value) { throw new UnsupportedOperationException(); }
        public <T> void remove(Matter section, int x, int y, int z, Class<T> type) { throw new UnsupportedOperationException(); }
        public <T> T get(Matter section, int x, int y, int z, Class<T> type) { return section.<T>slice(type).get(x, y, z); }
        public boolean hasSlice(Matter section, Class<?> type) { return section.hasSlice(type); }
        public void deleteSlice(Matter section, Class<?> type) { section.deleteSlice(type); }

        public <T> void iterate(Matter section, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
            MatterSlice<T> slice = section.getSlice(type);
            if (slice != null) {
                slice.iterateSync(iterator);
            }
        }
    }
}
