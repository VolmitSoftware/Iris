package art.arcane.iris.generation.decoration;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DecoratorStackSamplingTest {
    @Test
    public void coordinateNoiseIsSampledOncePerBodyAndTopWithIdenticalVoxelAndRngOutput() {
        for (long seed : new long[]{1L, 78264193L, -517L}) {
            for (int height : new int[]{1, 2, 8, 30}) {
                for (int direction = 0; direction < 3; direction++) {
                    for (int coordinate : new int[]{-37, 0, 13}) {
                        assertEquivalentStack(seed, height, direction, coordinate);
                    }
                }
            }
        }
    }

    private static void assertEquivalentStack(long seed, int height, int direction, int coordinate) {
        IrisData data = mock(IrisData.class);
        RNG rng = new RNG(seed);
        RNG controlRng = new RNG(seed);
        CNG variance = new CNG(new RNG(seed + 11L));
        CNG controlVariance = new CNG(new RNG(seed + 11L));
        NativeBlockState[] body = {block("minecraft:stone"), block("minecraft:dirt")};
        NativeBlockState[] top = {block("minecraft:granite"), block("minecraft:andesite")};
        IrisDecorator decorator = spy(new IrisDecorator().setForcePlace(true)
                .setStackMin(height).setStackMax(height).setTopThreshold(0.4D));
        doReturn(body).when(decorator).getBlockDataArray(data);
        doReturn(top).when(decorator).getBlockDataTopsArray(data);
        doReturn(variance).when(decorator).getVarianceGenerator(rng, data);
        NativeBlockState air = block("minecraft:air");
        doReturn(true).when(air).isAir();
        NativeBlockState support = block("minecraft:stone");
        BlockData supportHandle = mock(BlockData.class);
        doReturn(true).when(support).isSolid();
        doReturn(supportHandle).when(support).nativeHandle();
        when(supportHandle.isFaceSturdy(any(), eq(BlockSupport.FULL))).thenReturn(true);
        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, height + 2, 1);
        for (int y = 0; y < output.getHeight(); y++) {
            output.set(0, y, 0, air);
        }
        int supportY = direction == 1 ? height + 1 : 0;
        output.set(0, supportY, 0, support);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();
        int z = coordinate * 3 - 7;
        switch (direction) {
            case 0 -> DecoratorCore.placeStackUp(decorator, 0, 0, coordinate, z, 0, height,
                    output, rng, data, opts);
            case 1 -> DecoratorCore.placeStackDown(decorator, 0, 0, coordinate, z, height, 0,
                    output, rng, data, height, opts, null);
            case 2 -> assertEquals(height, DecoratorCore.placeFloatingStacked(decorator, 0, 0,
                    coordinate, z, 0, height + 1, output, rng, data, null));
            default -> throw new IllegalArgumentException("Unknown stack direction");
        }
        boolean usedBody = false;
        boolean usedTop = false;
        for (int i = 0; i < height; i++) {
            double threshold = height == 1 ? direction == 2 ? 0D : 1D : (double) i / (height - 1);
            boolean topSegment = threshold >= decorator.getTopThreshold();
            NativeBlockState[] palette = topSegment ? top : body;
            NativeBlockState expected = palette[Math.abs(controlVariance.fit(0, palette.length - 1, z, coordinate))];
            int y = direction == 1 ? height - i : i + 1;
            assertSame("seed=" + seed + ", direction=" + direction + ", y=" + y, expected, output.get(0, y, 0));
            usedBody |= !topSegment;
            usedTop |= topSegment;
        }
        assertSame(support, output.get(0, supportY, 0));
        verify(decorator, times(usedBody ? 1 : 0)).pickBlockData(rng, data, coordinate, z);
        verify(decorator, times(usedTop ? 1 : 0)).pickBlockDataTop(rng, data, coordinate, z);
        assertEquals(controlRng.nextLong(), rng.nextLong());
    }

    private static NativeBlockState block(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        doReturn(key).when(state).key();
        when(state.canPlaceOnto(any())).thenReturn(true);
        return state;
    }
}
