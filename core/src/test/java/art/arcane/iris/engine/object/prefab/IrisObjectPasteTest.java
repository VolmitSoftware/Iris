package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A paste writes the object's blocks around a spot, turned, with no placement rule. */
public class IrisObjectPasteTest {
    private static final int SPOT_X = 10;
    private static final int SPOT_Y = 64;
    private static final int SPOT_Z = 10;

    private PlatformBlockState stone;
    private PlatformBlockState air;
    private IObjectPlacer placer;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        stone = state("minecraft:stone", false);
        air = state("minecraft:air", true);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(call -> call.<String>getArgument(0).contains("air") ? air : stone);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        placer = mock(IObjectPlacer.class);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void writesEveryBlockAroundTheSpotAndLeavesAirOut() {
        IrisObject object = new IrisObject(3, 1, 1);
        object.setUnsigned(0, 0, 0, stone);
        object.setUnsigned(1, 0, 0, stone);
        object.setUnsigned(2, 0, 0, air);

        int written = object.paste(SPOT_X, SPOT_Y, SPOT_Z, placer, ObjectTurn.NONE);

        assertEquals(2, written);
        verify(placer).set(SPOT_X + offsetOf(object, 0), SPOT_Y, SPOT_Z, stone);
        verify(placer).set(SPOT_X + offsetOf(object, 1), SPOT_Y, SPOT_Z, stone);
        verify(placer, never()).set(anyInt(), anyInt(), anyInt(), eq(air));
    }

    @Test
    public void aQuarterTurnAboutYSwapsTheOffsets() {
        IrisObject object = new IrisObject(3, 1, 1);
        object.setUnsigned(0, 0, 0, stone);
        object.setUnsigned(2, 0, 0, stone);

        object.paste(SPOT_X, SPOT_Y, SPOT_Z, placer, ObjectTurn.of(0, 90, 0));

        int centre = object.getCenter().getBlockX();
        verify(placer).set(SPOT_X, SPOT_Y, SPOT_Z + centre, stone);
        verify(placer).set(SPOT_X, SPOT_Y, SPOT_Z - (2 - centre), stone);
        verify(placer, never()).setTile(anyInt(), anyInt(), anyInt(), any());
    }

    /** Where the block set at x = {@code index} lies from the spot: the object's blocks are kept around its centre. */
    private static int offsetOf(IrisObject object, int index) {
        return index - object.getCenter().getBlockX();
    }

    private static PlatformBlockState state(String key, boolean isAir) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isAir()).thenReturn(isAir);
        when(state.isSolid()).thenReturn(!isAir);
        return state;
    }
}
