package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.stage.IrisFloatingChildBiomeModifier;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisSlopeClip;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class FloatingIslandLockedLayersTest {
    @Test
    public void lockedBandsFollowIslandAltitudeInsteadOfThickness() {
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
        NativeBlockState third = mock(NativeBlockState.class);
        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes().setBiome("");
        IrisBiome parent = new IrisBiome().setLockLayers(true).setLockLayersMax(16)
                .setLayers(new KList<>(layer(first), layer(second), layer(third)))
                .setFloatingChildBiomes(new KList<>(entry));
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getSubterrainCell(anyInt(), anyInt(), anyInt())).thenReturn(null);
        when(engine.getComplex().allowsNewDiscreteContentAt(anyInt(), anyInt())).thenReturn(false);
        FloatingIslandBoundarySampler boundary = mock(FloatingIslandBoundarySampler.class);
        when(boundary.parent(anyInt(), anyInt())).thenReturn(parent);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getFloatingIslandBoundarySampler()).thenReturn(boundary);
        FloatingIslandSample thin = sample(entry, 18, 3);
        FloatingIslandSample thick = sample(entry, 17, 4);
        FloatingIslandSample raised = sample(entry, 19, 3);
        boolean[] gapMask = {true, false, false, false, false, false, false, false, true, false, true};
        FloatingIslandSample gapped = FloatingIslandSample.constructForTest(entry, 10, 11, 10, 3, gapMask, null);
        IrisFloatingChildBiomes mirroredEntry = new IrisFloatingChildBiomes().setBiome("")
                .setBottomPaletteMode(FloatingBottomPaletteMode.MIRROR_TOP);
        FloatingIslandSample mirrored = FloatingIslandSample.constructForTest(mirroredEntry, 10, 11, 10, 3, gapMask, null);
        NativeBlockState customBlock = mock(NativeBlockState.class);
        IrisFloatingChildBiomes customEntry = new IrisFloatingChildBiomes().setBiome("")
                .setBottomPaletteMode(FloatingBottomPaletteMode.CUSTOM).setBottomPalette(new KList<>(layer(customBlock)));
        FloatingIslandSample custom = FloatingIslandSample.constructForTest(customEntry, 10, 11, 10, 3, gapMask, null);
        IrisFloatingChildBiomes ordinaryEntry = new IrisFloatingChildBiomes().setBiome("");
        IrisBiome ordinaryParent = new IrisBiome().setLayers(new KList<>(layer(first), layer(second), layer(third)))
                .setFloatingChildBiomes(new KList<>(ordinaryEntry));
        when(boundary.parent(-10, -16)).thenReturn(ordinaryParent);
        FloatingIslandSample ordinary = FloatingIslandSample.constructForTest(ordinaryEntry, 10, 11, 10, 3, gapMask, null);
        Hunk<NativeBlockState> output = Hunk.newArrayHunk(16, 64, 16);
        try (MockedStatic<FloatingIslandSample> samples = mockStatic(FloatingIslandSample.class);
             MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("minecraft:stone")).thenReturn(first);
            samples.when(() -> FloatingIslandSample.sampleMemoized(eq(parent), eq(-16), eq(-16),
                    anyInt(), anyLong(), any(), eq(engine), eq(boundary))).thenReturn(thin);
            samples.when(() -> FloatingIslandSample.sampleMemoized(eq(parent), eq(-15), eq(-16),
                    anyInt(), anyLong(), any(), eq(engine), eq(boundary))).thenReturn(thick);
            samples.when(() -> FloatingIslandSample.sampleMemoized(eq(parent), eq(-14), eq(-16),
                    anyInt(), anyLong(), any(), eq(engine), eq(boundary))).thenReturn(raised);
            samples.when(() -> FloatingIslandSample.sampleMemoized(eq(parent), eq(-13), eq(-16),
                    anyInt(), anyLong(), any(), eq(engine), eq(boundary))).thenReturn(gapped);
            samples.when(() -> FloatingIslandSample.sampleMemoized(eq(parent), eq(-12), eq(-16),
                    anyInt(), anyLong(), any(), eq(engine), eq(boundary))).thenReturn(mirrored);
            samples.when(() -> FloatingIslandSample.sampleMemoized(eq(parent), eq(-11), eq(-16),
                    anyInt(), anyLong(), any(), eq(engine), eq(boundary))).thenReturn(custom);
            samples.when(() -> FloatingIslandSample.sampleMemoized(eq(ordinaryParent), eq(-10), eq(-16),
                    anyInt(), anyLong(), any(), eq(engine), eq(boundary))).thenReturn(ordinary);
            new IrisFloatingChildBiomeModifier(engine).onModify(-16, -16, output, false, context);
        }
        assertSame(first, output.get(0, 20, 0));
        assertSame(first, output.get(1, 20, 0));
        assertSame(third, output.get(2, 21, 0));
        assertSame(first, output.get(3, 20, 0));
        assertSame(third, output.get(3, 18, 0));
        assertSame(second, output.get(3, 10, 0));
        assertSame(first, output.get(4, 10, 0));
        assertSame(second, output.get(4, 18, 0));
        assertSame(customBlock, output.get(5, 10, 0));
        assertSame(second, output.get(5, 18, 0));
        assertSame(second, output.get(6, 18, 0));
        assertSame(third, output.get(6, 10, 0));
        for (int y = 18; y <= 20; y++) {
            assertSame(output.get(0, y, 0), output.get(1, y, 0));
            if (y >= 19) {
                assertSame(output.get(0, y, 0), output.get(2, y, 0));
            }
        }
    }

    private FloatingIslandSample sample(IrisFloatingChildBiomes entry, int baseY, int thickness) {
        boolean[] mask = new boolean[thickness];
        Arrays.fill(mask, true);
        return FloatingIslandSample.constructForTest(entry, baseY, thickness, thickness - 1, thickness, mask, null);
    }

    private IrisBiomePaletteLayer layer(NativeBlockState block) {
        IrisBiomePaletteLayer layer = mock(IrisBiomePaletteLayer.class);
        CNG height = mock(CNG.class);
        when(layer.getZoom()).thenReturn(1D);
        when(layer.getMinHeight()).thenReturn(1);
        when(layer.getMaxHeight()).thenReturn(1);
        when(layer.getSlopeCondition()).thenReturn(new IrisSlopeClip());
        when(layer.getHeightGenerator(any(), any())).thenReturn(height);
        when(height.fit(anyInt(), anyInt(), anyDouble(), anyDouble())).thenReturn(1);
        when(layer.get(any(), anyInt(), anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(block);
        when(layer.get(any(), anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(block);
        return layer;
    }
}
