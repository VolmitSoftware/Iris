/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The wall patcher fires on any neighbour more than two blocks lower, and neighbour heights come from
 * trueHeight, which excludes fluid. Every coastal column therefore sees a seaward drop, so without a
 * shore check the beach surface is replaced with the wall palette.
 */
public class IrisPostModifierWallShoreTest {
    private static final int PLANE_WIDTH = 3;
    private static final int SURFACE = 64;
    private static final int HUNK_HEIGHT = 80;
    private static final NativeBlockState GROUND = state("minecraft:sand");
    private static final NativeBlockState WALL = state("minecraft:stone");
    private static final NativeBlockState AIR = state("minecraft:air");

    @Test
    public void shoreColumnsKeepTheirSurfaceBlockBesideASeawardDrop() throws ReflectiveOperationException {
        assertSame(GROUND, surfaceAfterPost(true, true, false));
    }

    @Test
    public void inlandColumnsStillGetWallPatchedBesideADrop() throws ReflectiveOperationException {
        assertSame(WALL, surfaceAfterPost(false, true, false));
    }

    @Test
    public void disabledWallsAndRiverColumnsAreUntouched() throws ReflectiveOperationException {
        assertSame(GROUND, surfaceAfterPost(false, false, false));
        assertSame(GROUND, surfaceAfterPost(false, true, true));
    }

    @Test
    public void wallPatchingNeverReplacesAProtectedHydrologyCell() throws ReflectiveOperationException {
        assertSame(GROUND, surfaceAfterPost(false, true, false, true));
    }

    /**
     * Drives the private per-column pass directly. The heights plane is the only input that decides
     * whether the wall patcher fires, and building it through onModify would need a live mantle.
     */
    private NativeBlockState surfaceAfterPost(boolean shore, boolean walls, boolean river) throws ReflectiveOperationException {
        return surfaceAfterPost(shore, walls, river, false);
    }

    @SuppressWarnings("unchecked")
    private NativeBlockState surfaceAfterPost(boolean shore, boolean walls, boolean river, boolean sealed) throws ReflectiveOperationException {
        Hunk<NativeBlockState> data = Hunk.newHunk(1, HUNK_HEIGHT, 1);

        for (int y = 0; y < HUNK_HEIGHT; y++) {
            data.set(0, y, 0, y <= SURFACE ? GROUND : AIR);
        }

        IrisBiomePaletteLayer wallLayer = mock(IrisBiomePaletteLayer.class);
        doReturn(new KList<IrisBlockData>().qadd(new IrisBlockData())).when(wallLayer).getPalette();
        doReturn(WALL).when(wallLayer).get(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), any());

        IrisBiome biome = mock(IrisBiome.class);
        doReturn(wallLayer).when(biome).getWall();
        doReturn(shore).when(biome).isShore();

        ChunkedDataCache<IrisBiome> biomes = mock(ChunkedDataCache.class);
        doReturn(biome).when(biomes).get(0, 0);
        ChunkContext context = mock(ChunkContext.class);
        doReturn(biomes).when(context).getBiome();

        ProceduralStream<WaterRole> roles = mock(ProceduralStream.class);
        doReturn(river ? WaterRole.CHANNEL : WaterRole.NONE).when(roles).get(anyDouble(), anyDouble());
        ProceduralStream<Double> waterSurface = mock(ProceduralStream.class);
        doReturn(0D).when(waterSurface).get(anyDouble(), anyDouble());

        IrisComplex complex = mock(IrisComplex.class);
        doReturn(roles).when(complex).getWaterRoleStream();
        doReturn(waterSurface).when(complex).getWaterHeadStream();

        Engine engine = mock(Engine.class);
        doReturn(complex).when(engine).getComplex();
        doReturn(mock(IrisData.class)).when(engine).getData();

        if (sealed) {
            doReturn(sealedMantle()).when(engine).getMantle();
        }

        IrisPostModifier modifier = mock(IrisPostModifier.class, CALLS_REAL_METHODS);
        doReturn(engine).when(modifier).getEngine();
        Field rng = IrisPostModifier.class.getDeclaredField("rng");
        rng.setAccessible(true);
        rng.set(modifier, new RNG(88L));

        // Only the +x neighbour is low, so neither nib pass can fire and the wall patcher is the sole writer.
        int[] heights = new int[PLANE_WIDTH * PLANE_WIDTH];
        Arrays.fill(heights, SURFACE);
        heights[5] = SURFACE - 4;

        Method post = IrisPostModifier.class.getDeclaredMethod("post", int.class, int.class, Hunk.class,
                int.class, int.class, ChunkContext.class, int[].class, boolean[].class, int.class, boolean.class, boolean.class);
        post.setAccessible(true);
        boolean[] rivers = IrisPostModifier.waterPlane(roles, 0, 0, PLANE_WIDTH - 2, PLANE_WIDTH - 2);
        post.invoke(modifier, 0, 0, data, 0, 0, context, heights, rivers, PLANE_WIDTH, walls, false);
        return data.get(0, SURFACE, 0);
    }

    @SuppressWarnings("unchecked")
    private static EngineMantle sealedMantle() {
        MatterSlice<HydrologyCaveCell> cells = mock(MatterSlice.class);
        doReturn(HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD)).when(cells).get(0, SURFACE & 15, 0);
        Matter section = mock(Matter.class);
        doReturn(true).when(section).hasSlice(HydrologyCaveCell.class);
        doReturn(cells).when(section).getSlice(HydrologyCaveCell.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        doReturn(true).when(chunk).exists(SURFACE >> 4);
        doReturn(section).when(chunk).get(SURFACE >> 4);
        TectonicPlate<Matter> plate = mock(TectonicPlate.class);
        doReturn(chunk).when(plate).get(0, 0);
        Mantle<Matter> mantle = mock(Mantle.class);
        doReturn(HUNK_HEIGHT).when(mantle).getWorldHeight();
        doReturn(plate).when(mantle).getLoadedRegion(0, 0);
        EngineMantle engineMantle = mock(EngineMantle.class);
        doReturn(mantle).when(engineMantle).getMantle();
        return engineMantle;
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        doReturn(key).when(state).key();
        doReturn(key).when(state).materialKey();
        return state;
    }
}
