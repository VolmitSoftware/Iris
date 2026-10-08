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
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.project.matter.IrisMatterSupport;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.host.LoadedPack;
import com.volmit.host.MantleRegistration;
import com.volmit.host.ModuleHost;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisModuleMantleTest {
    private static final int MIN_Y = -64;
    private static final int SECTIONS = 4;
    private static final MantleKey<String> MARKS = new MantleKey<>("iris:marks/mark", String.class);
    private static final MantleKey<Integer> DEPTHS = new MantleKey<>("iris:marks/depth", Integer.class);
    private static final MantleCodec<String> TEXT = new MantleCodec<>() {
        public int version() { return 1; }
        public void write(DataOutput output, String value) throws IOException { output.writeUTF(value); }
        public String read(DataInput input, int storedVersion) throws IOException { return input.readUTF(); }
    };
    private static final MantleCodec<Integer> NUMBER = new MantleCodec<>() {
        public int version() { return 0; }
        public void write(DataOutput output, Integer value) throws IOException { output.writeInt(value); }
        public Integer read(DataInput input, int storedVersion) throws IOException { return input.readInt(); }
    };
    private static final List<MantleRegistration<?>> LAYERS = List.of(
            new MantleRegistration<>(MARKS, MantleLifetime.GENERATION, TEXT, 1),
            new MantleRegistration<>(DEPTHS, MantleLifetime.PERSISTENT, NUMBER, 0));

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Map<Long, MantleChunk<Matter>> chunks = new HashMap<>();

    @Before
    public void registerSlices() {
        IrisMatterSupport.ensureRegistered();
    }

    private MantleChunk<Matter> chunk(int chunkX, int chunkZ) {
        return chunks.computeIfAbsent(((long) chunkX << 32) ^ (chunkZ & 0xffffffffL), ignored ->
                new MantleChunk<>(SECTIONS, chunkX, chunkZ, new Sections(), MantleHooks.NONE));
    }

    private IrisModuleMantle mantle(BlockBox bounds) {
        return new IrisModuleMantle(bounds, MIN_Y, this::chunk, LAYERS);
    }

    @Test
    public void layersShareABlockAndReadInWorldCoordinatesAcrossChunks() {
        IrisModuleMantle mantle = mantle(new BlockBox(-16, MIN_Y, -16, 32, 0, 32));

        mantle.layer(MARKS).set(-1, -60, 20, "west");
        mantle.layer(MARKS).set(17, -1, 3, "east");
        mantle.layer(DEPTHS).set(17, -1, 3, 9);

        assertEquals("west", mantle.layer(MARKS).get(-1, -60, 20));
        assertEquals("east", mantle.layer(MARKS).get(17, -1, 3));
        assertEquals(Integer.valueOf(9), mantle.layer(DEPTHS).get(17, -1, 3));
        mantle.layer(MARKS).remove(17, -1, 3);
        assertNull(mantle.layer(MARKS).get(17, -1, 3));
        assertEquals(Integer.valueOf(9), mantle.layer(DEPTHS).get(17, -1, 3));
        assertEquals("another invocation sees the same mantle", Integer.valueOf(9),
                mantle(new BlockBox(16, MIN_Y, 0, 32, 0, 16)).layer(DEPTHS).get(17, -1, 3));
    }

    @Test
    public void visitsInXThenYThenZOrderAndClearsOnlyItsArea() {
        IrisModuleMantle mantle = mantle(new BlockBox(-16, MIN_Y, -16, 32, 0, 32));
        mantle.layer(MARKS).set(20, -50, 0, "c");
        mantle.layer(MARKS).set(-3, -10, 5, "a");
        mantle.layer(MARKS).set(20, -60, 9, "b");
        mantle.layer(DEPTHS).set(0, -20, 0, 1);
        List<String> visited = new ArrayList<>();

        mantle.layer(MARKS).visit(new BlockBox(-16, MIN_Y, -16, 32, 0, 32), (x, y, z, value) -> visited.add(value + "@" + x + "," + y + "," + z));
        mantle.layer(MARKS).clear(new BlockBox(16, MIN_Y, -16, 32, 0, 32));

        assertEquals(List.of("a@-3,-10,5", "b@20,-60,9", "c@20,-50,0"), visited);
        assertNull(mantle.layer(MARKS).get(20, -50, 0));
        assertEquals("a", mantle.layer(MARKS).get(-3, -10, 5));
        assertEquals(Integer.valueOf(1), mantle.layer(DEPTHS).get(0, -20, 0));
    }

    @Test
    public void refusesLayersNobodyRegisteredWrongTypesAndBlocksOutsideItsBounds() {
        IrisModuleMantle mantle = mantle(new BlockBox(0, MIN_Y, 0, 16, 0, 16));

        IllegalArgumentException unregistered = assertThrows(IllegalArgumentException.class,
                () -> mantle.layer(new MantleKey<>("iris:marks/other", String.class)));
        assertEquals("Mantle layer iris:marks/other is not registered by any module of the pack", unregistered.getMessage());
        assertThrows(IllegalArgumentException.class, () -> mantle.layer(new MantleKey<>("iris:marks/mark", Integer.class)));
        assertThrows(IndexOutOfBoundsException.class, () -> mantle.layer(MARKS).set(16, -60, 0, "out"));
        assertThrows(IndexOutOfBoundsException.class, () -> mantle.layer(MARKS).visit(new BlockBox(0, MIN_Y, 0, 17, 0, 16), (x, y, z, v) -> { }));
    }

    @Test
    public void storedValuesDecodeWithTheirCodecAndUnknownLayersSurviveUnchanged() throws IOException {
        ModuleLayerValues written = ModuleLayerValues.EMPTY.with(MARKS, TEXT, "kept").with(DEPTHS, NUMBER, 4);
        ModuleLayerValues read = ModuleLayerValues.read(input(bytes(written)));

        assertEquals("kept", read.get(MARKS, TEXT));
        assertEquals(Integer.valueOf(4), read.get(DEPTHS, NUMBER));
        ModuleLayerValues withoutCodecs = ModuleLayerValues.read(input(bytes(written))).without(MARKS.id());
        assertArrayEquals(bytes(ModuleLayerValues.EMPTY.with(DEPTHS, NUMBER, 4)), bytes(withoutCodecs));
    }

    @Test
    public void mantleStagesWriteAroundTheirChunkThroughIrisChunks() throws IOException {
        MantleKey<String> planned = new MantleKey<>("iris:veins/planned", String.class);
        IrisModule veins = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("iris:veins", "Veins", "1"); }
            public void install(StageGraph graph) {
                graph.define(Stage.mantle("iris:veins/plan")).reach(16).writesMantle(planned)
                        .run(context -> context.mantle().layer(planned).set(context.originX() + 20, -30, context.originZ() + 3, "vein"));
            }
            public void register(FeatureRegistry registry) { registry.registerMantle(planned, MantleLifetime.GENERATION, TEXT); }
        };
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        SeedManager seeds = mock(SeedManager.class);
        when(engine.getData()).thenReturn(data);
        when(data.getDataFolder()).thenReturn(folder.getRoot());
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(new IrisRegion());
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(new IrisBiome());
        when(engine.getMinHeight()).thenReturn(MIN_Y);
        when(engine.getHeight()).thenReturn(SECTIONS * 16);

        try (LoadedPack pack = LoadedPack.load(folder.newFolder("pack").toPath(),
                ModuleHost.of(List.of(veins)).copyingJarsTo(folder.newFolder("copies").toPath()))) {
            ModuleMantleComponent.forPack(engine, pack, Set.of()).getFirst().generate(0, 0, this::chunk);

            IrisModuleMantle neighbor = new IrisModuleMantle(new BlockBox(16, MIN_Y, 0, 32, 0, 16), MIN_Y, this::chunk, pack.catalog().mantle());
            assertEquals("vein", neighbor.layer(planned).get(20, -30, 3));
        }
        assertTrue(chunks.containsKey(1L << 32));
    }

    /** Iris's in-memory mantle sections, without the engine's static state that would bind a platform for every later test. */
    private static final class Sections implements MantleDataAdapter<Matter> {
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

    private static byte[] bytes(ModuleLayerValues values) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        values.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private static DataInputStream input(byte[] bytes) {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
}
