package art.arcane.iris.core.modules;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformFace;
import art.arcane.iris.spi.PlatformRegistries;
import com.volmit.block.BlockFace;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.resource.ResourceId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** IrisApi's blocks are the bound platform's, through their keys, and so are its traits, tags and face support. */
public class IrisBlockRegistryTest {
    private final IrisBlockRegistry blocks = new IrisBlockRegistry(mock(art.arcane.iris.core.loader.IrisData.class));
    private PlatformRegistries registries;
    private PlatformBlockState stone;
    private PlatformBlockState sand;

    @Before
    public void bindPlatform() {
        stone = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("stone");
        when(stone.isSolid()).thenReturn(true);
        when(stone.isOre()).thenReturn(false);
        sand = mock(PlatformBlockState.class);
        when(sand.key()).thenReturn("minecraft:sand");
        when(sand.hasGravity()).thenReturn(true);
        PlatformBlockState log = mock(PlatformBlockState.class);
        when(log.key()).thenReturn("minecraft:oak_log");
        when(log.isLog()).thenReturn(true);
        PlatformBlockState leaves = mock(PlatformBlockState.class);
        when(leaves.key()).thenReturn("minecraft:oak_leaves");
        when(leaves.isLeaves()).thenReturn(true);
        registries = mock(PlatformRegistries.class);
        when(registries.blockOrNull(eq("minecraft:stone"), anyBoolean())).thenReturn(stone);
        when(registries.blockOrNull(eq("minecraft:sand"), anyBoolean())).thenReturn(sand);
        when(registries.blockOrNull(eq("minecraft:oak_log"), anyBoolean())).thenReturn(log);
        when(registries.blockOrNull(eq("minecraft:oak_leaves"), anyBoolean())).thenReturn(leaves);
        when(registries.blockOrNull(eq("minecraft:nothing"), anyBoolean())).thenReturn(null);
        when(registries.isInTag(log, "minecraft:logs")).thenReturn(true);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void aKeyWithoutANamespaceIsMinecrafts() {
        assertEquals(BlockState.parse("minecraft:stone"), blocks.resolve("stone"));
    }

    @Test
    public void aBlockThePlatformDoesNotKnowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> blocks.resolve("minecraft:nothing"));
        assertThrows(IllegalArgumentException.class, () -> blocks.hasTrait(BlockState.parse("minecraft:nothing"), BlockTrait.LOG));
        assertThrows(IllegalArgumentException.class, () -> blocks.inTag(BlockState.parse("minecraft:nothing"), ResourceId.parse("minecraft:logs")));
    }

    @Test
    public void traitsAnswerFromThePlatformsBlockState() {
        BlockState stone = BlockState.parse("minecraft:stone");

        assertTrue(blocks.hasTrait(stone, BlockTrait.SOLID));
        assertFalse(blocks.hasTrait(stone, BlockTrait.ORE));
    }

    @Test
    public void gravityLogAndLeavesComeFromThePlatformToo() {
        BlockState sand = BlockState.parse("minecraft:sand");
        BlockState log = BlockState.parse("minecraft:oak_log");
        BlockState leaves = BlockState.parse("minecraft:oak_leaves");

        assertTrue(blocks.hasTrait(sand, BlockTrait.GRAVITY));
        assertFalse(blocks.hasTrait(sand, BlockTrait.LOG));
        assertTrue(blocks.hasTrait(log, BlockTrait.LOG));
        assertFalse(blocks.hasTrait(log, BlockTrait.GRAVITY));
        assertTrue(blocks.hasTrait(leaves, BlockTrait.LEAVES));
        assertFalse(blocks.hasTrait(leaves, BlockTrait.LOG));
    }

    @Test
    public void tagsAreTheRegistrysMembership() {
        assertTrue(blocks.inTag(BlockState.parse("minecraft:oak_log"), ResourceId.parse("minecraft:logs")));
        assertFalse(blocks.inTag(BlockState.parse("minecraft:stone"), ResourceId.parse("minecraft:logs")));
        assertFalse("a tag nobody knows has no members", blocks.inTag(BlockState.parse("minecraft:oak_log"), ResourceId.parse("pack:nothing")));
    }

    @Test
    public void everyFaceAsksThePlatformAboutThatFace() {
        BlockState torch = BlockState.parse("minecraft:stone");
        BlockState support = BlockState.parse("minecraft:sand");
        for (BlockFace face : BlockFace.values()) {
            when(stone.canAttach(sand, PlatformFace.valueOf(face.name()))).thenReturn(face == BlockFace.NORTH);
        }

        for (BlockFace face : BlockFace.values()) {
            assertEquals(face.name(), face == BlockFace.NORTH, blocks.canAttach(torch, support, face));
            verify(stone).canAttach(sand, PlatformFace.valueOf(face.name()));
        }
    }
}
