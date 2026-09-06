package art.arcane.iris.probe;

import art.arcane.iris.engine.decorator.IrisSpeleothems;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class StubPlatformStateTest {
    @BeforeClass
    public static void bindPlatform() {
        IrisPlatforms.unbind();
        IrisPlatforms.bind(new StubPlatform());
        StubPlatform.bindGenerationStateHandlers();
    }

    @Test
    public void rotatesFacingAxisAndConnectedFaces() {
        IrisObjectRotation rotation = IrisObjectRotation.of(0, 90, 0);

        assertEquals("minecraft:oak_stairs[facing=west,half=bottom]",
                StubPlatform.rotateForTest(rotation, state("minecraft:oak_stairs[facing=north,half=bottom]")).key());
        assertEquals("minecraft:oak_log[axis=x]",
                StubPlatform.rotateForTest(rotation, state("minecraft:oak_log[axis=z]")).key());
        assertEquals("minecraft:oak_fence[north=false,east=false,south=false,west=true]",
                StubPlatform.rotateForTest(rotation,
                        state("minecraft:oak_fence[north=true,east=false,south=false,west=false]")).key());
    }

    @Test
    public void propertyUpdatesAndMergesPreserveCanonicalState() {
        PlatformBlockState base = state("minecraft:oak_leaves[distance=7,persistent=false]");
        PlatformBlockState updated = base.withProperty("distance", "2").withProperty("persistent", "true");

        assertEquals("minecraft:oak_leaves[distance=2,persistent=true]", updated.key());

        PlatformBlockState merged = StubPlatform.mergeForTest(
                base, state("minecraft:oak_leaves[persistent=true]"));
        assertEquals("minecraft:oak_leaves[distance=7,persistent=true]", merged.key());
    }

    @Test
    public void noOpStateTransformsReuseTheCanonicalInstance() {
        PlatformBlockState stone = state("minecraft:stone");
        PlatformBlockState leaves = state("minecraft:oak_leaves[distance=7,persistent=false]");

        assertSame(stone, StubPlatform.rotateForTest(IrisObjectRotation.of(0, 90, 0), stone));
        assertSame(leaves, leaves.withProperty("distance", "7"));
        assertSame(leaves, StubPlatform.mergeForTest(
                leaves,
                state("minecraft:oak_leaves[persistent=false]")
        ));
    }

    @Test
    public void classifiesVanillaFluidStates() {
        PlatformBlockState water = state("minecraft:water[level=0]");
        PlatformBlockState lava = state("minecraft:lava[level=0]");
        PlatformBlockState stone = state("minecraft:stone");

        assertTrue(water.isFluid());
        assertTrue(water.isWater());
        assertFalse(water.isSolid());
        assertTrue(lava.isFluid());
        assertFalse(lava.isWater());
        assertFalse(lava.isSolid());
        assertFalse(stone.isFluid());
        assertTrue(stone.isSolid());
    }

    @Test
    public void classifiesCanonicalWaterloggedProperty() {
        assertTrue(state("minecraft:brain_coral_fan[waterlogged=true]").isWaterLogged());
        assertFalse(state("minecraft:brain_coral_fan[waterlogged=false]").isWaterLogged());
        assertFalse(state("minecraft:brain_coral_fan").isWaterLogged());
    }

    @Test
    public void resolvesSpikeSupportWithoutServerClasses() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(1, 3, 1);
        PlatformBlockState spike = state("minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]");
        blocks.set(0, 0, 0, state("minecraft:stone"));
        assertTrue(IrisSpeleothems.isSupported(spike, blocks, 0, 0, 1));
        blocks.set(0, 0, 0, state("minecraft:water[level=0]"));
        assertFalse(IrisSpeleothems.isSupported(spike, blocks, 0, 0, 1));
        blocks.set(0, 0, 0, state("minecraft:air"));
        assertFalse(IrisSpeleothems.isSupported(spike, blocks, 0, 0, 1));
    }

    private PlatformBlockState state(String key) {
        return IrisPlatforms.get().registries().block(key);
    }

}
