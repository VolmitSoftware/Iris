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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisSeaSurfaceDecoratorBoundsTest {
    private static final int WORLD_HEIGHT = 8;
    private static final int TALL_STACK = 30;
    private static final double TOP_THRESHOLD = 0.9;
    private static final BlockState AIR = ApiChunk.AIR;
    private static final BlockState BODY = new BlockState("minecraft:kelp_plant");
    private static final BlockState TOP = new BlockState("minecraft:kelp");
    private static final DecoratedBiome BIOME = new DecoratedBiome("ocean", null, BiomeKind.SEA);

    @Test
    public void stackClippedByTheCeilingEndsOnTheTopBlock() {
        Hunk<BlockState> blocks = decorate(4, TALL_STACK);

        assertSame(BODY, blocks.get(0, 5, 0));
        assertSame(BODY, blocks.get(0, 6, 0));
        assertSame(TOP, blocks.get(0, 7, 0));
    }

    @Test
    public void stackOfOneBelowTheCeilingIsTheTopBlock() {
        Hunk<BlockState> blocks = decorate(6, 1);

        assertSame(TOP, blocks.get(0, 7, 0));
    }

    @Test
    public void baseAtTheCeilingPlacesNothing() {
        Hunk<BlockState> blocks = decorate(WORLD_HEIGHT - 1, TALL_STACK);

        for (int y = 0; y < WORLD_HEIGHT; y++) {
            assertSame(AIR, blocks.get(0, y, 0));
        }
    }

    private Hunk<BlockState> decorate(int height, int stackHeight) {
        TerrainSampler terrain = mock(TerrainSampler.class);
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        BiomeDecorators decorators = mock(BiomeDecorators.class);
        when(decorators.decorators(BIOME, DecorationPart.SEA_SURFACE)).thenReturn(new ResolvedDecorator[]{decorator});
        when(decorator.passesChanceGate(anyDouble(), anyDouble())).thenReturn(true);
        when(decorator.isStacking()).thenReturn(true);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(stackHeight);
        when(decorator.topThreshold()).thenReturn(TOP_THRESHOLD);
        when(decorator.blockAt(anyDouble(), anyDouble(), anyDouble())).thenReturn(BODY);
        when(decorator.topBlockAt(anyDouble(), anyDouble(), anyDouble())).thenReturn(TOP);
        Hunk<BlockState> blocks = new ArrayHunk<>(BlockState.class, 1, WORLD_HEIGHT, 1, AIR);
        DecorationScene scene = new DecorationScene(blocks, ApiChunk.blockRegistry(), terrain, null, decorators, 17L,
                ModuleLogs.discarding());

        new IrisSeaSurfaceDecorator(scene).decorate(0, 0, 0, 1, -1, 0, 1, -1, BIOME, height, WORLD_HEIGHT);
        return blocks;
    }
}
