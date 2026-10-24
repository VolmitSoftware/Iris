package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.engine.modifier.ApiChunk;
import com.volmit.block.BlockState;
import com.volmit.host.ArrayHunk;
import com.volmit.host.ModuleLogs;
import com.volmit.hunk.Hunk;
import com.volmit.world.BiomeKind;
import com.volmit.world.TerrainSampler;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisDecoratorCaveContextTest {
    private static final int FLUID_HEIGHT = 63;

    @Test
    public void explicitCaveContextSkipsFluid() {
        TerrainSampler terrain = mock(TerrainSampler.class);
        doReturn(FLUID_HEIGHT).when(terrain).fluidHeight();

        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        doReturn(true).when(decorator).passesChanceGate(anyDouble(), anyDouble());
        doReturn(true).when(decorator).isStacking();
        doReturn(true).when(decorator).isForcePlace();
        doReturn(1).when(decorator).stackHeight(anyDouble(), anyDouble());

        DecoratedBiome cave = new DecoratedBiome("cave", null, BiomeKind.CAVE);
        BiomeDecorators decorators = mock(BiomeDecorators.class);
        doReturn(new ResolvedDecorator[]{decorator}).when(decorators).decorators(cave, DecorationPart.NONE);
        doReturn(new ResolvedDecorator[]{decorator}).when(decorators).decorators(cave, DecorationPart.CEILING);

        Hunk<BlockState> output = new ArrayHunk<>(BlockState.class, 1, 128, 1, new BlockState("minecraft:water"));
        DecorationScene scene = new DecorationScene(output, ApiChunk.blockRegistry(), terrain, null, decorators, 1L, ModuleLogs.discarding());

        new IrisSurfaceDecorator(scene).decorate(0, 0, 0, 0, 0, 0, 0, 0, cave, 10, 10);
        DecoratorCore.PlaceOpts surfaceOptions = DecoratorCore.SCRATCH_OPTS.get();
        assertTrue(surfaceOptions.caveSkipFluid);
        assertFalse(surfaceOptions.underwater);

        new IrisCeilingDecorator(scene).decorate(0, 0, 0, 0, 0, 0, 0, 0, cave, 10, 10);
        assertTrue(DecoratorCore.SCRATCH_OPTS.get().caveSkipFluid);
        assertSame(BiomeKind.CAVE, cave.kind());
    }

    @Test
    public void aSurfaceBiomeIsUnderwaterBelowTheFluidAndNeverSkipsFluid() {
        assertTrue(IrisSurfaceDecorator.isUnderwater(BiomeKind.LAND, 10, FLUID_HEIGHT));
        assertFalse(IrisSurfaceDecorator.isUnderwater(BiomeKind.CAVE, 10, FLUID_HEIGHT));
        assertFalse(IrisSurfaceDecorator.skipsFluid(BiomeKind.SEA));
        assertTrue(IrisSurfaceDecorator.skipsFluid(BiomeKind.CAVE));
    }
}
