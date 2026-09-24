package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.mantle.TerrainMatterView;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisCarveBoundaryFacesTest {
    @BeforeClass
    public static void registerMatter() {
        IrisMatterSupport.ensureRegistered();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void borderSnapshotsPreserveCornerOrderAndNeverNestChunkLocks() throws Exception {
        MantleChunk<Matter>[] chunks = new MantleChunk[5];
        Matter[][] matter = new Matter[5][2];
        MatterCavern[] values = new MatterCavern[5];
        for (int side = 0; side < chunks.length; side++) {
            int selected = side;
            chunks[side] = mock(MantleChunk.class);
            matter[side][0] = new IrisMatter(16, 16, 16);
            matter[side][1] = new IrisMatter(16, 16, 16);
            values[side] = new MatterCavern(true, "side-" + side, (byte) 0);
            when(chunks[side].exists(anyInt())).thenAnswer(call -> {
                assertTrue(Thread.holdsLock(chunks[selected]));
                for (int other = 0; other < chunks.length; other++) {
                    if (other != selected && chunks[other] != null) {
                        assertFalse(Thread.holdsLock(chunks[other]));
                    }
                }
                return true;
            });
            when(chunks[side].get(anyInt())).thenAnswer(call -> matter[selected][(int) call.getArgument(0)]);
            if (side == 0) {
                continue;
            }
            for (int y = 1; y < 32; y++) {
                for (int offset = 0; offset < 16; offset++) {
                    int x = side == 1 ? 15 : side == 2 ? 0 : offset;
                    int z = side == 3 ? 15 : side == 4 ? 0 : offset;
                    matter[side][y >> 4].slice(MatterCavern.class).set(x, y & 15, z, values[side]);
                }
            }
        }
        matter[0][0].slice(MatterCavern.class).set(0, 7, 0, values[0]);
        matter[0][0].slice(MatterCavern.class).set(0, 5, 0, values[0]);
        matter[0][0].slice(PreObjectMatterCell.class).set(0, 5, 0, PreObjectMatterCell.cavern(null));
        Mantle<Matter> mantle = mock(Mantle.class, RETURNS_DEEP_STUBS);
        TectonicPlate<Matter> plate = mock(TectonicPlate.class);
        when(mantle.getLoadedRegions().get(anyLong())).thenReturn(plate);
        when(plate.get(31, 0)).thenReturn(chunks[1]);
        when(plate.get(1, 0)).thenReturn(chunks[2]);
        when(plate.get(0, 31)).thenReturn(chunks[3]);
        when(plate.get(0, 1)).thenReturn(chunks[4]);
        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(0).maxHeight(48).build());
        IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        doReturn(engine).when(modifier).getEngine();
        CarveWallBuffer walls = new CarveWallBuffer(64);
        CarveColumnMask[] masks = new CarveColumnMask[256];
        Arrays.setAll(masks, index -> new CarveColumnMask());
        int[] surfaces = new int[256];
        Arrays.fill(surfaces, 30);
        Method method = IrisCarveModifier.class.getDeclaredMethod("addCrossChunkBoundaryWalls",
                Mantle.class, MantleChunk.class, CarveWallBuffer.class, CarveColumnMask[].class,
                int.class, int.class, int[].class);
        method.setAccessible(true);
        method.invoke(modifier, mantle, chunks[0], walls, masks, 0, 0, surfaces);
        CarveWallBuffer expectedWalls = new CarveWallBuffer(64);
        for (int y = 1; y < 32; y++) {
            for (int offset = 0; offset < 16; offset++) {
                addScalarWall(expectedWalls, chunks[0], chunks[1], 0, y, offset, 15, offset);
                addScalarWall(expectedWalls, chunks[0], chunks[2], 15, y, offset, 0, offset);
                addScalarWall(expectedWalls, chunks[0], chunks[3], offset, y, 0, offset, 15);
                addScalarWall(expectedWalls, chunks[0], chunks[4], offset, y, 15, offset, 0);
            }
        }
        for (int y = 1; y < 32; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    MatterCavern expected = expectedWalls.get(x, y, z);
                    assertSame("voxel " + x + "," + y + "," + z, expected, walls.get(x, y, z));
                    assertTrue(masks[x * 16 + z].contains(y) == (expected != null));
                }
            }
        }
    }

    private static void addScalarWall(CarveWallBuffer walls, MantleChunk<Matter> chunk,
                                      MantleChunk<Matter> neighbor, int x, int y, int z,
                                      int neighborX, int neighborZ) {
        if (TerrainMatterView.getComposedCavern(chunk, x, y, z) != null) {
            return;
        }
        MatterCavern value = TerrainMatterView.getComposedCavern(neighbor, neighborX, y, neighborZ);
        if (value != null) {
            walls.put(x, y, z, value);
        }
    }
}
