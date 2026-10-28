package art.arcane.iris.probe;

import art.arcane.iris.engine.object.prefab.ObjectTurn;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformFace;
import art.arcane.iris.spi.PlatformRegistries;
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
        ObjectTurn rotation = ObjectTurn.of(0, 90, 0);

        assertEquals("minecraft:oak_stairs[facing=west,half=bottom]",
                StubPlatform.rotateForTest(rotation, state("minecraft:oak_stairs[facing=north,half=bottom]")).key());
        assertEquals("minecraft:oak_log[axis=x]",
                StubPlatform.rotateForTest(rotation, state("minecraft:oak_log[axis=z]")).key());
        assertEquals("minecraft:oak_fence[east=false,north=false,south=false,west=true]",
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
    public void answersLogLeavesGravityTagsAndFaceSupport() {
        PlatformBlockState log = state("minecraft:oak_log[axis=y]");
        PlatformBlockState leaves = state("minecraft:oak_leaves[distance=7]");
        PlatformBlockState sand = state("minecraft:sand");
        PlatformBlockState stone = state("minecraft:stone");
        PlatformBlockState torch = state("minecraft:wall_torch");

        assertTrue(log.isLog());
        assertFalse(log.isLeaves());
        assertTrue(leaves.isLeaves());
        assertTrue(log.isTreeBlock() && leaves.isTreeBlock());
        assertFalse(stone.isTreeBlock());
        assertTrue(sand.hasGravity());
        assertFalse(stone.hasGravity());
        PlatformRegistries registries = IrisPlatforms.get().registries();
        assertTrue(registries.isInTag(log, "minecraft:logs"));
        assertFalse(registries.isInTag(leaves, "minecraft:logs"));
        assertFalse(registries.isInTag(log, "pack:unknown"));
        assertTrue(torch.canAttach(stone, PlatformFace.NORTH));
        assertFalse(torch.canAttach(state("minecraft:air"), PlatformFace.NORTH));
    }

    @Test
    public void mirrorsFacingSidesAndRotationLikeMinecraftDoes() {
        assertEquals("minecraft:oak_stairs[facing=west,half=bottom]", state("minecraft:oak_stairs[facing=east,half=bottom]").mirrorX().key());
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom]", state("minecraft:oak_stairs[facing=north,half=bottom]").mirrorX().key());
        assertEquals("minecraft:oak_stairs[facing=south,half=bottom]", state("minecraft:oak_stairs[facing=north,half=bottom]").mirrorZ().key());
        assertEquals("minecraft:oak_fence[east=false,north=true,south=false,west=false]",
                state("minecraft:oak_fence[north=true,east=false,south=false,west=false]").mirrorX().key());
        assertEquals("minecraft:oak_fence[east=false,north=false,south=true,west=false]",
                state("minecraft:oak_fence[north=true,east=false,south=false,west=false]").mirrorZ().key());
        assertEquals("minecraft:oak_sign[rotation=12]", state("minecraft:oak_sign[rotation=4]").mirrorX().key());
        assertEquals("minecraft:oak_sign[rotation=4]", state("minecraft:oak_sign[rotation=4]").mirrorZ().key());
        PlatformBlockState stone = state("minecraft:stone");
        assertSame("a block with nothing to mirror is itself", stone, stone.mirrorX());
    }

    private PlatformBlockState state(String key) {
        return IrisPlatforms.get().registries().block(key);
    }

}
