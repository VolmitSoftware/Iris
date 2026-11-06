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
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.util.project.matter.IrisMatterSupport;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.volmit.mantle.CarvedVoid;
import com.volmit.resource.ResourceId;
import com.volmit.block.BlockState;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.host.LoadedPack;
import com.volmit.host.MantleRegistration;
import com.volmit.host.ModuleHost;
import com.volmit.geometry.BlockBox;
import com.volmit.data.StorageCodec;
import com.volmit.mantle.MantleEdit;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleLifetime;
import com.volmit.stage.Stage;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
    private static final PackResourceIds IDS = new PackResourceIds(new java.io.File("pack"));
    private static final MantleKey<String> MARKS = new MantleKey<>(ResourceId.parse("iris:marks/mark"), String.class);
    private static final MantleKey<Integer> DEPTHS = new MantleKey<>(ResourceId.parse("iris:marks/depth"), Integer.class);
    private static final StorageCodec<String> TEXT = new StorageCodec<>() {
        public int version() { return 1; }
        public void write(DataOutput output, String value) throws IOException { output.writeUTF(value); }
        public String read(DataInput input, int storedVersion) throws IOException { return input.readUTF(); }
    };
    private static final StorageCodec<Integer> NUMBER = new StorageCodec<>() {
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
        return new IrisModuleMantle(bounds, this::chunk, LAYERS, PlannedBlocks.none(bounds), IDS);
    }

    /** The form the floating islands' sky biomes always had: the platform's id for the biome, as a custom inject, at the height above the bottom. */
    @Test
    public void aPlatformBiomeIsStoredAsTheBiomePassReadsIt() {
        PlatformBiomeWriter biomeWriter = mock(PlatformBiomeWriter.class);
        when(biomeWriter.biomeIdFor("minecraft:cherry_grove")).thenReturn(42);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.biomeWriter()).thenReturn(biomeWriter);
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            IrisModuleMantle mantle = mantle(new BlockBox(0, 0, 0, 16, HEIGHT, 16));

            mantle.setPlatformBiome(5, 37, 9, ResourceId.parse("minecraft:cherry_grove"));

            assertEquals(new MatterBiomeInject(true, 42, null), chunk(0, 0).get(5, 37, 9, MatterBiomeInject.class));
            assertNull(chunk(0, 0).get(5, 36, 9, MatterBiomeInject.class));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> mantle.setPlatformBiome(16, 37, 9, ResourceId.parse("minecraft:cherry_grove")));
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }

    @Test
    public void carvedVoidsAreKeptAsTheCavernsIrisReadsAndReadBackAsTheirFills() {
        IrisModuleMantle mantle = mantle(new BlockBox(-16, 0, -16, 32, HEIGHT, 32));

        mantle.carved().set(-3, 7, 20, CarvedVoid.OPEN);
        mantle.carved().set(5, 40, 5, CarvedVoid.FLUID);
        mantle.carved().set(6, 40, 5, CarvedVoid.LAVA);
        mantle.carved().set(7, 40, 5, CarvedVoid.AIR);

        assertEquals(new MatterCavern(true, "", (byte) 0), chunk(-1, 1).get(13, 7, 4, MatterCavern.class));
        assertEquals(new MatterCavern(true, "", (byte) 1), chunk(0, 0).get(5, 40, 5, MatterCavern.class));
        assertEquals(new MatterCavern(true, "", (byte) 2), chunk(0, 0).get(6, 40, 5, MatterCavern.class));
        assertEquals(new MatterCavern(true, "", (byte) 3), chunk(0, 0).get(7, 40, 5, MatterCavern.class));
        assertSame(CarvedVoid.OPEN, mantle.carved().get(-3, 7, 20));
        assertSame(CarvedVoid.FLUID, mantle.carved().get(5, 40, 5));
        assertSame(CarvedVoid.LAVA, mantle.carved().get(6, 40, 5));
        assertSame(CarvedVoid.AIR, mantle.carved().get(7, 40, 5));
        assertNull(mantle.carved().get(8, 40, 5));
        assertTrue(mantle.isCarved(5, 40, 5));
        assertFalse(mantle.isCarved(8, 40, 5));
    }

    @Test
    public void cavernsOfAnyOtherLiquidReadAsOpenAndKeepTheirCustomBiomeAsAPackId() {
        IrisModuleMantle mantle = mantle(new BlockBox(0, 0, 0, 16, HEIGHT, 16));
        chunk(0, 0).getOrCreate(0).<MatterCavern>slice(MatterCavern.class).set(1, 2, 3, new MatterCavern(true, "", (byte) 9));
        chunk(0, 0).getOrCreate(0).<MatterCavern>slice(MatterCavern.class).set(2, 2, 3, new MatterCavern(true, "cave/lush", (byte) 3));

        assertSame(CarvedVoid.OPEN, mantle.carved().get(1, 2, 3));
        assertEquals(CarvedVoid.AIR.withBiome(IDS.of("cave/lush")), mantle.carved().get(2, 2, 3));

        mantle.carved().set(3, 2, 3, CarvedVoid.LAVA.withBiome(IDS.of("cave/lush")));
        assertEquals(new MatterCavern(true, "cave/lush", (byte) 2), chunk(0, 0).get(3, 2, 3, MatterCavern.class));
    }

    @Test
    public void carvedVoidsVisitInOrderAndClearOnlyTheirArea() {
        IrisModuleMantle mantle = mantle(new BlockBox(-16, 0, -16, 32, HEIGHT, 32));
        mantle.carved().set(17, 5, 3, CarvedVoid.LAVA);
        mantle.carved().set(-2, 9, 3, CarvedVoid.OPEN);
        mantle.carved().set(-2, 4, 20, CarvedVoid.FLUID);
        mantle.carved().set(-2, 4, 3, CarvedVoid.AIR);
        List<String> visited = new java.util.ArrayList<>();

        mantle.carved().visit(new BlockBox(-16, 0, -16, 32, HEIGHT, 32), (x, y, z, carved) -> visited.add(x + "," + y + "," + z + " " + carved.fill()));

        assertEquals(List.of("-2,4,3 AIR", "-2,4,20 FLUID", "-2,9,3 OPEN", "17,5,3 LAVA"), visited);

        mantle.carved().clear(new BlockBox(-16, 0, -16, 0, HEIGHT, 32));
        assertNull(mantle.carved().get(-2, 9, 3));
        assertSame(CarvedVoid.LAVA, mantle.carved().get(17, 5, 3));
        mantle.carved().remove(17, 5, 3);
        assertFalse(mantle.isCarved(17, 5, 3));
    }

    @Test
    public void carvedVoidsOfAnAreaTooLargeToLayOutVisitInTheSameOrder() {
        BlockBox wide = new BlockBox(0, 0, 0, 1024, HEIGHT, 80);
        IrisModuleMantle mantle = mantle(wide);
        CarvedVoid lush = CarvedVoid.AIR.withBiome(IDS.of("cave/lush"));
        mantle.carved().set(1000, 5, 70, CarvedVoid.LAVA);
        mantle.carved().set(3, 9, 70, lush);
        mantle.carved().set(3, 9, 5, CarvedVoid.OPEN);
        mantle.carved().set(3, 2, 75, CarvedVoid.FLUID);
        mantle.carved().set(500, 63, 0, lush);
        List<String> visited = new java.util.ArrayList<>();

        mantle.carved().visit(wide, (x, y, z, carved) -> visited.add(x + "," + y + "," + z + " " + carved.fill() + carved.biome().map(id -> " " + id.path()).orElse("")));

        assertEquals(List.of("3,2,75 FLUID", "3,9,5 OPEN", "3,9,70 AIR cave/lush", "500,63,0 AIR cave/lush", "1000,5,70 LAVA"), visited);
    }

    @Test
    public void aVisitInsideAVisitReadsItsOwnArea() {
        IrisModuleMantle mantle = mantle(new BlockBox(0, 0, 0, 16, HEIGHT, 16));
        mantle.carved().set(1, 2, 3, CarvedVoid.OPEN);
        mantle.carved().set(4, 5, 6, CarvedVoid.LAVA);
        List<String> visited = new java.util.ArrayList<>();

        mantle.carved().visit(new BlockBox(0, 0, 0, 16, HEIGHT, 16), (x, y, z, outer) -> {
            visited.add("outer " + outer.fill());
            mantle.carved().visit(new BlockBox(4, 5, 6, 5, 6, 7), (nx, ny, nz, inner) -> visited.add("inner " + inner.fill()));
        });

        assertEquals(List.of("outer OPEN", "inner LAVA", "outer LAVA", "inner LAVA"), visited);
    }

    @Test
    public void carvedVoidsStayInsideTheMantleAnInvocationPrepared() {
        IrisModuleMantle mantle = mantle(new BlockBox(0, 0, 0, 16, HEIGHT, 16));

        assertThrows(IndexOutOfBoundsException.class, () -> mantle.carved().set(16, 5, 5, CarvedVoid.OPEN));
        assertThrows(IndexOutOfBoundsException.class, () -> mantle.carved().get(5, HEIGHT, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> mantle.carved().visit(new BlockBox(0, 0, 0, 17, 1, 1), (x, y, z, carved) -> { }));
    }

    @Test
    public void anEditOnlyReadsTheCarvedVoids() {
        IrisModuleMantle mantle = mantle(new BlockBox(0, 0, 0, 16, HEIGHT, 16));
        mantle.carved().set(1, 2, 3, CarvedVoid.OPEN);

        try (MantleEdit edit = mantle.edit()) {
            assertSame(CarvedVoid.OPEN, edit.carved().get(1, 2, 3));
            assertThrows(UnsupportedOperationException.class, () -> edit.carved().set(1, 2, 4, CarvedVoid.OPEN));
            assertThrows(UnsupportedOperationException.class, () -> edit.carved().remove(1, 2, 3));
        }
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
                () -> mantle.layer(new MantleKey<>(ResourceId.parse("iris:marks/other"), String.class)));
        assertEquals("Mantle layer iris:marks/other is not registered by any module of the pack", unregistered.getMessage());
        assertThrows(IllegalArgumentException.class, () -> mantle.layer(new MantleKey<>(ResourceId.parse("iris:marks/mark"), Integer.class)));
        assertThrows(IndexOutOfBoundsException.class, () -> mantle.layer(MARKS).set(16, 4, 0, "out"));
        assertThrows(IndexOutOfBoundsException.class, () -> mantle.layer(MARKS).visit(new BlockBox(0, 0, 0, 17, HEIGHT, 16), (x, y, z, v) -> { }));
    }

    @Test
    public void storedValuesDecodeWithTheirCodecAndUnknownLayersSurviveUnchanged() throws IOException {
        ModuleLayerValues written = ModuleLayerValues.EMPTY.with(MARKS, TEXT, "kept").with(DEPTHS, NUMBER, 4);
        ModuleLayerValues read = ModuleLayerValues.read(input(bytes(written)));

        assertEquals("kept", read.get(MARKS, TEXT));
        assertEquals(Integer.valueOf(4), read.get(DEPTHS, NUMBER));
        ModuleLayerValues withoutCodecs = ModuleLayerValues.read(input(bytes(written))).without(MARKS.id().toString());
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
        BlockBox bounds = new BlockBox(0, 0, 0, 32, HEIGHT, 16);
        IrisModuleMantle mantle = new IrisModuleMantle(bounds, (chunkX, chunkZ) -> chunkX == 0 ? chunk(chunkX, chunkZ) : null, LAYERS,
                PlannedBlocks.none(bounds), IDS);
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
        MantleKey<String> unknown = new MantleKey<>(ResourceId.parse("iris:gone/layer"), String.class);
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
        MantleKey<String> planned = new MantleKey<>(ResourceId.parse("iris:veins/planned"), String.class);
        IrisModule veins = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("iris:veins", "Veins", "1"); }
            public void setup(ModuleSetup setup) {
                setup.registry().registerMantle(planned, MantleLifetime.GENERATION, TEXT);
                setup.stages().define(Stage.mantle("iris:veins/plan")).reach(16).writesMantle(planned)
                        .run(context -> context.mantle().layer(planned).set(context.originX() + 20, 34, context.originZ() + 3, "vein"));
            }
        };
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        SeedManager seeds = mock(SeedManager.class);
        when(engine.getData()).thenReturn(data);
        when(data.getDataFolder()).thenReturn(folder.getRoot());
        IrisPackResources resources = new IrisPackResources(data, List::of);
        when(data.getPackResources()).thenReturn(resources);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(new IrisRegion());
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(new IrisBiome());
        when(engine.getMinHeight()).thenReturn(WORLD_MIN_HEIGHT);
        when(engine.getHeight()).thenReturn(SECTIONS * 16);

        try (LoadedPack pack = LoadedPack.load(folder.newFolder("pack").toPath(),
                ModuleHost.of(List.of(veins)).copyingJarsTo(folder.newFolder("copies").toPath()))) {
            ModuleMantleComponent.forPack(engine, pack, Set.of()).getFirst().generate(0, 0, this::chunk,
                    new IrisMantleChunk(engine, null, 0, 0, null));

            BlockBox neighborBounds = new BlockBox(16, 0, 0, 32, HEIGHT, 16);
            IrisModuleMantle neighbor = new IrisModuleMantle(neighborBounds, this::chunk, pack.catalog().mantle(), PlannedBlocks.none(neighborBounds), IDS);
            assertEquals("vein", neighbor.layer(planned).get(20, 34, 3));
        }
        assertTrue(chunks.containsKey(1L << 32));
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
