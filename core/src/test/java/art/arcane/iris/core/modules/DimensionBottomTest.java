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

import art.arcane.iris.builtin.placement.ObjectPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.placement.PlacedBlock;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;
import com.volmit.world.TerrainSampler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IrisApi measures every height from the dimension's bottom, whatever the dimension's minimum height is. A
 * dimension reaching down to world Y -64 is the case that tells the two apart: a chunk stage, a mantle stage and a
 * placement stage each write at height 0 and must hit the bottom block of the dimension, with terrain samples and
 * bounds in the same heights.
 */
public class DimensionBottomTest {
    private static final int WORLD_MIN_HEIGHT = -64;
    private static final int DIMENSION_HEIGHT = 64;
    private static final int BOTTOM = 0;
    private static final int SURFACE_HEIGHT = 10;
    private static final BlockBox WHOLE_HEIGHT_OF_ONE_CHUNK = new BlockBox(0, BOTTOM, 0, 16, DIMENSION_HEIGHT, 16);
    /** A chunk stage sees the terrain of its chunk and of the 16 blocks around it. */
    private static final BlockBox WHOLE_HEIGHT_AROUND_ONE_CHUNK = new BlockBox(-16, BOTTOM, -16, 32, DIMENSION_HEIGHT, 32);
    private static final BlockState STONE = BlockState.parse("minecraft:stone");
    private static final MantleKey<String> MARKS = new MantleKey<>("test:bottom/mark", String.class);
    private static final MantleCodec<String> TEXT = new MantleCodec<>() {
        public int version() { return 1; }
        public void write(DataOutput output, String value) throws IOException { output.writeUTF(value); }
        public String read(DataInput input, int storedVersion) throws IOException { return input.readUTF(); }
    };

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final InMemoryMantle storage = new InMemoryMantle(DIMENSION_HEIGHT / 16);
    private final List<BlockBox> terrainBounds = new ArrayList<>();
    private final List<BlockBox> viewBounds = new ArrayList<>();
    private final List<Double> surfaces = new ArrayList<>();
    private Engine engine;
    private IrisData data;
    private LoadedPack pack;

    /** Writes its mark and block at height 0 in whichever pipeline it runs, and records the heights it was shown. */
    private final class BottomModule implements IrisModule {
        public ModuleInfo info() { return ModuleInfo.of("test:bottom", "Bottom", "1"); }

        public void install(StageGraph graph) {
            graph.define(Stage.chunk("test:bottom/chunk")).run(context -> {
                record(context.terrain());
                viewBounds.add(context.mantle().bounds());
                context.hunk(BlockState.class).set(0, BOTTOM, 0, STONE);
            });
            graph.define(Stage.mantle("test:bottom/mantle")).reach(0).writesMantle(MARKS).run(context -> {
                record(context.terrain());
                viewBounds.add(context.mantle().bounds());
                context.mantle().layer(MARKS).set(context.originX(), BOTTOM, context.originZ(), "bottom");
            });
            graph.define(Stage.placement("test:bottom/placement")).run(context -> {
                record(context.terrain());
                viewBounds.add(context.access().bounds());
                ObjectPlacement.current().anchoredAt(BOTTOM);
                context.access().set(new PlacedBlock(new BlockPosition(0, BOTTOM, 0), STONE, Optional.empty()));
                context.access().mantle().layer(MARKS).set(0, BOTTOM, 0, "bottom");
            });
        }

        public void register(FeatureRegistry registry) {
            registry.registerMantle(MARKS, MantleLifetime.GENERATION, TEXT);
        }
    }

    private void record(TerrainSampler terrain) {
        terrainBounds.add(terrain.bounds());
        surfaces.add(terrain.column(0, 0).surfaceY());
    }

    @Before
    public void loadPackOfADimensionBelowWorldYZero() throws IOException {
        UsableBlockPlatform.bind();
        pack = LoadedPack.load(folder.newFolder("pack").toPath(),
                ModuleHost.of(List.of(new BottomModule())).copyingJarsTo(folder.newFolder("copies").toPath()));
        engine = mock(Engine.class);
        data = mock(IrisData.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> (double) SURFACE_HEIGHT));
        when(complex.getSlopeStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 0D));
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getMinHeight()).thenReturn(WORLD_MIN_HEIGHT);
        when(engine.getHeight()).thenReturn(DIMENSION_HEIGHT);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(new IrisRegion());
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(new IrisBiome());
        BuiltInPack.seedWorld(engine, 7L);
        PackModuleSet moduleSet = mock(PackModuleSet.class);
        when(moduleSet.pack()).thenReturn(pack);
        when(data.getModules()).thenReturn(moduleSet);
        when(data.getDataFolder()).thenReturn(folder.getRoot());
        when(data.getEngine()).thenReturn(engine);
        storage.bindTo(engine);
    }

    @After
    public void unload() {
        pack.close();
        UsableBlockPlatform.unbind();
    }

    @Test
    public void aChunkStageWritingAtHeightZeroHitsTheBottomBlockOfTheChunk() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, DIMENSION_HEIGHT, 16);

        ModuleChunkGeneration.generate(pack, engine, 0, 0, blocks, null, false, null, B::getState);

        assertEquals("minecraft:stone", blocks.get(0, BOTTOM, 0).key());
        assertEquals("the chunk's block hunk starts at the bottom, not at the world's minimum height", 1, countBlocks(blocks));
        assertEquals(List.of(WHOLE_HEIGHT_AROUND_ONE_CHUNK), terrainBounds);
        assertEquals("the mantle reaches as far around the chunk as the terrain", List.of(WHOLE_HEIGHT_AROUND_ONE_CHUNK), viewBounds);
        assertEquals("the terrain samples count from the same bottom", List.of(Double.valueOf(SURFACE_HEIGHT)), surfaces);
    }

    @Test
    public void aMantleStageWritingAtHeightZeroHitsTheBottomOfTheMantle() {
        ModuleMantleComponent.forPack(engine, pack, Set.of()).stream()
                .filter(component -> component.stage().stage().id().toString().equals("test:bottom/mantle"))
                .forEach(component -> component.generate(0, 0, storage::chunk));

        assertEquals("bottom", storage.value(MARKS, TEXT, 0, BOTTOM, 0));
        assertSamplesAndBoundsAgree();
    }

    @Test
    public void aPlacementStageWritingAtHeightZeroHitsTheBottomBlockAndTheBottomOfTheMantle() {
        IObjectPlacer placer = mock(IObjectPlacer.class);
        when(placer.getEngine()).thenReturn(engine);
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, B.getState("minecraft:stone"));

        ModuleObjectPlacement.place(object, 0, BOTTOM, 0, placer, new IrisObjectPlacement(), new RNG(2L), null, null, data);

        verify(placer).set(eq(0), eq(BOTTOM), eq(0), any(PlatformBlockState.class));
        assertEquals("bottom", storage.value(MARKS, TEXT, 0, BOTTOM, 0));
        BlockBox terrain = terrainBounds.getFirst();
        assertEquals(BOTTOM, terrain.minY());
        assertEquals(DIMENSION_HEIGHT, terrain.maxY());
        assertEquals(List.of(Double.valueOf(SURFACE_HEIGHT)), surfaces);
        assertTrue("a placement may reach beyond the dimension, so its view holds at least the terrain's heights",
                viewBounds.getFirst().contains(terrain));
    }

    private void assertSamplesAndBoundsAgree() {
        assertEquals("a mantle stage sees the terrain around its chunk, as a chunk stage does", List.of(WHOLE_HEIGHT_AROUND_ONE_CHUNK), terrainBounds);
        assertEquals(List.of(WHOLE_HEIGHT_OF_ONE_CHUNK), viewBounds);
        assertEquals("the terrain samples count from the same bottom", List.of(Double.valueOf(SURFACE_HEIGHT)), surfaces);
        assertTrue(terrainBounds.getFirst().contains(0, SURFACE_HEIGHT, 0));
    }

    private static int countBlocks(Hunk<PlatformBlockState> blocks) {
        int count = 0;
        for (int y = 0; y < blocks.getHeight(); y++) {
            for (int x = 0; x < blocks.getWidth(); x++) {
                for (int z = 0; z < blocks.getDepth(); z++) {
                    count += blocks.get(x, y, z) != null ? 1 : 0;
                }
            }
        }
        return count;
    }
}
