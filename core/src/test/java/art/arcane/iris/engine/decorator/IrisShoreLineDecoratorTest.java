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

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisShoreLineDecoratorTest {
    private static final int FLUID_HEIGHT = 4;
    private static final BlockState AIR = ApiChunk.AIR;
    private static final BlockState GRASS_BLOCK = new BlockState("minecraft:grass_block");
    private static final BlockState WATER = new BlockState("minecraft:water");
    private static final BlockState POPPY = new BlockState("minecraft:poppy");
    private static final DecoratedBiome BIOME = new DecoratedBiome("shore", null, BiomeKind.SHORE);

    @Test
    public void carvedSurfaceRejectsShorelineDecoration() {
        Fixture fixture = createFixture(false);
        Hunk<BlockState> output = fixture.withSupport(AIR);

        fixture.shoreline.decorate(0, 0, 0, 1, -1, 0, 1, -1, BIOME, FLUID_HEIGHT, output.height());

        assertSame(AIR, output.get(0, FLUID_HEIGHT + 1, 0));
        verify(fixture.decorator).passesChanceGate(anyDouble(), anyDouble());
        verify(fixture.decorator, never()).blockAt(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    public void preservedFluidRejectsShorelineDecoration() {
        Fixture fixture = createFixture(false);
        Hunk<BlockState> output = fixture.withSupport(WATER);

        fixture.shoreline.decorate(0, 0, 0, 1, -1, 0, 1, -1, BIOME, FLUID_HEIGHT, output.height());

        assertSame(AIR, output.get(0, FLUID_HEIGHT + 1, 0));
        verify(fixture.decorator, never()).blockAt(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    public void sturdySurfacePlacesShorelineDecoration() {
        Fixture fixture = createFixture(false);
        Hunk<BlockState> output = fixture.withSupport(GRASS_BLOCK);

        fixture.shoreline.decorate(0, 0, 0, 1, -1, 0, 1, -1, BIOME, FLUID_HEIGHT, output.height());

        assertSame(POPPY, output.get(0, FLUID_HEIGHT + 1, 0));
    }

    @Test
    public void forcePlaceStillRejectsMissingSurface() {
        Fixture fixture = createFixture(true);
        Hunk<BlockState> output = fixture.withSupport(AIR);

        fixture.shoreline.decorate(0, 0, 0, 1, -1, 0, 1, -1, BIOME, FLUID_HEIGHT, output.height());

        assertSame(AIR, output.get(0, FLUID_HEIGHT + 1, 0));
    }

    private Fixture createFixture(boolean forcePlace) {
        TerrainSampler terrain = mock(TerrainSampler.class);
        when(terrain.fluidHeight()).thenReturn(FLUID_HEIGHT);
        when(terrain.surfaceY(anyInt(), anyInt())).thenReturn((double) FLUID_HEIGHT - 1);
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        BiomeDecorators decorators = mock(BiomeDecorators.class);
        when(decorators.decorators(BIOME, DecorationPart.SHORE_LINE)).thenReturn(new ResolvedDecorator[]{decorator});
        when(decorator.passesChanceGate(anyDouble(), anyDouble())).thenReturn(true);
        when(decorator.isForcePlace()).thenReturn(forcePlace);
        when(decorator.blockAt(anyDouble(), anyDouble(), anyDouble())).thenReturn(POPPY);
        Hunk<BlockState> blocks = new ArrayHunk<>(BlockState.class, 1, FLUID_HEIGHT + 3, 1, AIR);
        return new Fixture(new IrisShoreLineDecorator(new DecorationScene(blocks, ApiChunk.blocks(), terrain, null, decorators, 17L,
                ModuleLogs.discarding())), decorator, blocks);
    }

    private record Fixture(IrisShoreLineDecorator shoreline, ResolvedDecorator decorator, Hunk<BlockState> blocks) {
        /** The blocks with {@code support} at the fluid height, where the shoreline decorates from. */
        Hunk<BlockState> withSupport(BlockState support) {
            blocks.set(0, FLUID_HEIGHT, 0, support);
            return blocks;
        }
    }
}
