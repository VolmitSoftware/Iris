package art.arcane.iris.core.modules;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
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
import static org.mockito.Mockito.when;

/** IrisApi's blocks are the bound platform's, through their keys; what the platform cannot answer is said so. */
public class IrisBlockRegistryTest {
    private final IrisBlockRegistry blocks = new IrisBlockRegistry();

    @Before
    public void bindPlatform() {
        PlatformBlockState stone = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("stone");
        when(stone.isSolid()).thenReturn(true);
        when(stone.isOre()).thenReturn(false);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.blockOrNull(eq("minecraft:stone"), anyBoolean())).thenReturn(stone);
        when(registries.blockOrNull(eq("minecraft:nothing"), anyBoolean())).thenReturn(null);
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
    }

    @Test
    public void traitsAnswerFromThePlatformsBlockState() {
        BlockState stone = BlockState.parse("minecraft:stone");

        assertTrue(blocks.hasTrait(stone, BlockTrait.SOLID));
        assertFalse(blocks.hasTrait(stone, BlockTrait.ORE));
    }

    @Test
    public void whatThePlatformLayerDoesNotOfferFailsInsteadOfGuessing() {
        BlockState stone = BlockState.parse("minecraft:stone");

        assertThrows(UnsupportedOperationException.class, () -> blocks.hasTrait(stone, BlockTrait.GRAVITY));
        assertThrows(UnsupportedOperationException.class, () -> blocks.inTag(stone, ResourceId.parse("minecraft:logs")));
        assertThrows(UnsupportedOperationException.class, () -> blocks.canAttach(stone, stone, BlockFace.UP));
    }
}
