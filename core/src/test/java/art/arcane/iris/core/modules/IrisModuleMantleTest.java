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
import com.volmit.mantle.MantleEdit;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;
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
    /** A dimension whose bottom is below world Y 0: IrisApi heights must not depend on it. */
    private static final int WORLD_MIN_HEIGHT = -64;
    private static final int SECTIONS = 4;
    private static final int HEIGHT = SECTIONS * 16;
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
                new MantleChunk<>(SECTIONS, chunkX, chunkZ, new InMemoryMantle.Sections(), MantleHooks.NONE));
    }

    private IrisModuleMantle mantle(BlockBox bounds) {
        return new IrisModuleMantle(bounds, this::chunk, LAYERS);
    }

    @Test
    public void layersShareABlockAndReadInWorldCoordinatesAcrossChunks() {
        IrisModuleMantle mantle = mantle(new BlockBox(-16, 0, -16, 32, HEIGHT, 32));

        mantle.layer(MARKS).set(-1, 4, 20, "west");
        mantle.layer(MARKS).set(17, 63, 3, "east");
        mantle.layer(DEPTHS).set(17, 63, 3, 9);

        assertEquals("west", mantle.layer(MARKS).get(-1, 4, 20));
        assertEquals("east", mantle.layer(MARKS).get(17, 63, 3));
        assertEquals(Integer.valueOf(9), mantle.layer(DEPTHS).get(17, 63, 3));
        mantle.layer(MARKS).remove(17, 63, 3);
        assertNull(mantle.layer(MARKS).get(17, 63, 3));
        assertEquals(Integer.valueOf(9), mantle.layer(DEPTHS).get(17, 63, 3));
        assertEquals("another invocation sees the same mantle", Integer.valueOf(9),
                mantle(new BlockBox(16, 0, 0, 32, HEIGHT, 16)).layer(DEPTHS).get(17, 63, 3));
    }

    @Test
    public void visitsInXThenYThenZOrderAndClearsOnlyItsArea() {
        IrisModuleMantle mantle = mantle(new BlockBox(-16, 0, -16, 32, HEIGHT, 32));
        mantle.layer(MARKS).set(20, 14, 0, "c");
        mantle.layer(MARKS).set(-3, 54, 5, "a");
        mantle.layer(MARKS).set(20, 4, 9, "b");
        mantle.layer(DEPTHS).set(0, 44, 0, 1);
        List<String> visited = new ArrayList<>();

        mantle.layer(MARKS).visit(new BlockBox(-16, 0, -16, 32, HEIGHT, 32), (x, y, z, value) -> visited.add(value + "@" + x + "," + y + "," + z));
        mantle.layer(MARKS).clear(new BlockBox(16, 0, -16, 32, HEIGHT, 32));

        assertEquals(List.of("a@-3,54,5", "b@20,4,9", "c@20,14,0"), visited);
        assertNull(mantle.layer(MARKS).get(20, 14, 0));
        assertEquals("a", mantle.layer(MARKS).get(-3, 54, 5));
        assertEquals(Integer.valueOf(1), mantle.layer(DEPTHS).get(0, 44, 0));
    }

    @Test
    public void refusesLayersNobodyRegisteredWrongTypesAndBlocksOutsideItsBounds() {
        IrisModuleMantle mantle = mantle(new BlockBox(0, 0, 0, 16, HEIGHT, 16));

        IllegalArgumentException unregistered = assertThrows(IllegalArgumentException.class,
                () -> mantle.layer(new MantleKey<>("iris:marks/other", String.class)));
        assertEquals("Mantle layer iris:marks/other is not registered by any module of the pack", unregistered.getMessage());
        assertThrows(IllegalArgumentException.class, () -> mantle.layer(new MantleKey<>("iris:marks/mark", Integer.class)));
        assertThrows(IndexOutOfBoundsException.class, () -> mantle.layer(MARKS).set(16, 4, 0, "out"));
        assertThrows(IndexOutOfBoundsException.class, () -> mantle.layer(MARKS).visit(new BlockBox(0, 0, 0, 17, HEIGHT, 16), (x, y, z, v) -> { }));
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
    public void anEditBuffersChangesAcrossLayersUntilItCommits() {
        IrisModuleMantle mantle = mantle(new BlockBox(-16, 0, -16, 32, HEIGHT, 32));
        mantle.layer(MARKS).set(1, 4, 1, "stored");
        mantle.layer(MARKS).set(2, 4, 1, "removed");
        List<String> visited = new ArrayList<>();

        try (MantleEdit edit = mantle.edit()) {
            edit.layer(MARKS).set(20, 14, 0, "added");
            edit.layer(MARKS).remove(2, 4, 1);
            edit.layer(DEPTHS).set(20, 14, 0, 3);
            edit.layer(MARKS).visit(new BlockBox(-16, 0, -16, 32, HEIGHT, 32), (x, y, z, value) -> visited.add(value + "@" + x));

            assertEquals("added", edit.layer(MARKS).get(20, 14, 0));
            assertNull(edit.layer(MARKS).get(2, 4, 1));
            assertNull("the mantle is unchanged until the commit", mantle.layer(MARKS).get(20, 14, 0));
            assertEquals("removed", mantle.layer(MARKS).get(2, 4, 1));
            edit.commit();
        }

        assertEquals(List.of("stored@1", "added@20"), visited);
        assertEquals("added", mantle.layer(MARKS).get(20, 14, 0));
        assertEquals(Integer.valueOf(3), mantle.layer(DEPTHS).get(20, 14, 0));
        assertNull(mantle.layer(MARKS).get(2, 4, 1));
        assertEquals("stored", mantle.layer(MARKS).get(1, 4, 1));
    }

    @Test
    public void aClosedEditChangesNothingAndAnOpenEditKeepsTheMantleReadOnly() {
        IrisModuleMantle mantle = mantle(new BlockBox(0, 0, 0, 16, HEIGHT, 16));
        MantleEdit edit = mantle.edit();
        MantleLayer<String> marks = edit.layer(MARKS);
        marks.set(1, 4, 1, "discarded");

        assertThrows(IllegalStateException.class, () -> mantle.layer(MARKS).set(0, 4, 0, "direct"));
        assertThrows(IllegalStateException.class, mantle::edit);
        assertThrows(UnsupportedOperationException.class, edit::edit);
        assertThrows(IndexOutOfBoundsException.class, () -> marks.set(16, 4, 0, "out"));
        edit.close();
        edit.close();

        assertNull(mantle.layer(MARKS).get(1, 4, 1));
        IllegalStateException closed = assertThrows(IllegalStateException.class, () -> marks.get(1, 4, 1));
        assertEquals("This mantle edit is closed; open a new one with edit()", closed.getMessage());
        assertThrows(IllegalStateException.class, edit::commit);
        mantle.layer(MARKS).set(0, 4, 0, "direct");
        mantle.edit().commit();
    }

    @Test
    public void aCommitThatCannotReachEveryChunkPublishesNothing() {
        IrisModuleMantle mantle = new IrisModuleMantle(new BlockBox(0, 0, 0, 32, HEIGHT, 16),
                (chunkX, chunkZ) -> chunkX == 0 ? chunk(chunkX, chunkZ) : null, LAYERS);
        MantleEdit edit = mantle.edit();
        edit.layer(MARKS).set(1, 4, 1, "first");
        edit.layer(MARKS).set(20, 4, 1, "unprepared");

        assertThrows(IllegalStateException.class, edit::commit);
        assertNull(mantle.layer(MARKS).get(1, 4, 1));
        assertThrows(IllegalStateException.class, edit::commit);
        assertThrows(IllegalStateException.class, () -> mantle.layer(MARKS).set(2, 4, 1, "still open"));
        edit.close();
        mantle.layer(MARKS).set(2, 4, 1, "after close");
    }

    @Test
    public void cleanupRemovesGenerationLayersAndKeepsPersistentAndUnknownOnes() throws IOException {
        IrisModuleMantle mantle = mantle(new BlockBox(0, 0, 0, 16, HEIGHT, 16));
        mantle.layer(MARKS).set(1, 4, 1, "short-lived");
        mantle.layer(DEPTHS).set(1, 4, 1, 7);
        mantle.layer(MARKS).set(2, 54, 3, "alone");
        MantleKey<String> unknown = new MantleKey<>("iris:gone/layer", String.class);
        ModuleLayerValues fromDisk = ModuleLayerValues.read(input(bytes(ModuleLayerValues.EMPTY.with(unknown, TEXT, "kept")
                .with(MARKS, TEXT, "old"))));
        chunk(0, 0).getOrCreate(34 >> 4).slice(ModuleLayerValues.class).set(4, 34 & 15, 4, fromDisk);

        GenerationLayers.of(LAYERS).removeFrom(chunk(0, 0));

        assertNull(mantle.layer(MARKS).get(1, 4, 1));
        assertEquals(Integer.valueOf(7), mantle.layer(DEPTHS).get(1, 4, 1));
        assertNull("a block left without values holds nothing", chunk(0, 0).get(54 >> 4)
                .<ModuleLayerValues>slice(ModuleLayerValues.class).get(2, 54 & 15, 3));
        assertNull(mantle.layer(MARKS).get(4, 34, 4));
        ModuleLayerValues left = chunk(0, 0).get(34 >> 4).<ModuleLayerValues>slice(ModuleLayerValues.class).get(4, 34 & 15, 4);
        assertEquals("a layer no module registers stays", "kept", left.get(unknown, TEXT));
    }

    @Test
    public void mantleStagesWriteAroundTheirChunkThroughIrisChunks() throws IOException {
        MantleKey<String> planned = new MantleKey<>("iris:veins/planned", String.class);
        IrisModule veins = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("iris:veins", "Veins", "1"); }
            public void install(StageGraph graph) {
                graph.define(Stage.mantle("iris:veins/plan")).reach(16).writesMantle(planned)
                        .run(context -> context.mantle().layer(planned).set(context.originX() + 20, 34, context.originZ() + 3, "vein"));
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
        when(engine.getMinHeight()).thenReturn(WORLD_MIN_HEIGHT);
        when(engine.getHeight()).thenReturn(SECTIONS * 16);

        try (LoadedPack pack = LoadedPack.load(folder.newFolder("pack").toPath(),
                ModuleHost.of(List.of(veins)).copyingJarsTo(folder.newFolder("copies").toPath()))) {
            ModuleMantleComponent.forPack(engine, pack, Set.of()).getFirst().generate(0, 0, this::chunk);

            IrisModuleMantle neighbor = new IrisModuleMantle(new BlockBox(16, 0, 0, 32, HEIGHT, 16), this::chunk, pack.catalog().mantle());
            assertEquals("vein", neighbor.layer(planned).get(20, 34, 3));
        }
        assertTrue(chunks.containsKey(1L << 32));
    }

    @Test
    public void aMantleStageFindsTheSnapshotsPackResources() throws IOException {
        List<Object> found = new ArrayList<>();
        IrisModule finder = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("iris:finder", "Finder", "1"); }
            public void install(StageGraph graph) {
                graph.define(Stage.mantle("iris:finder/oak")).reach(0).run(context ->
                        found.add(context.resources().find(PackResourceFixture.oakKey(folder.getRoot())).orElseThrow()));
            }
        };
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        SeedManager seeds = mock(SeedManager.class);
        when(engine.getData()).thenReturn(data);
        when(data.getDataFolder()).thenReturn(folder.getRoot());
        PackResourceFixture.serve(data);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(new IrisRegion());
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(new IrisBiome());
        when(engine.getMinHeight()).thenReturn(WORLD_MIN_HEIGHT);
        when(engine.getHeight()).thenReturn(SECTIONS * 16);

        try (LoadedPack pack = LoadedPack.load(folder.newFolder("finder-pack").toPath(),
                ModuleHost.of(List.of(finder)).copyingJarsTo(folder.newFolder("finder-copies").toPath()))) {
            ModuleMantleComponent.forPack(engine, pack, Set.of()).getFirst().generate(0, 0, this::chunk);
        }

        assertEquals(List.of(PackResourceFixture.OAK), found);
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
