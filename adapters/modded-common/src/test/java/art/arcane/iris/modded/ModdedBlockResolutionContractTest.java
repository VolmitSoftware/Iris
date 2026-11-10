package art.arcane.iris.modded;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The SPI splits block resolution into a null-returning lookup and an air-falling-back lookup. Modded used to
 * collapse both onto air, so an unknown key produced no output at all.
 */
public class ModdedBlockResolutionContractTest {
    private static final String UNKNOWN = "minecraft:definitely_not_a_real_block";

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void getOrNullReturnsNullForUnknownKey() {
        assertNull(ModdedBlockResolution.getOrNull(UNKNOWN));
        assertNull(ModdedBlockResolution.getOrNull(UNKNOWN, true));
    }

    @Test
    public void getFallsBackToAirForUnknownKey() {
        ModdedBlockState state = ModdedBlockResolution.get(UNKNOWN);
        assertNotNull(state);
        assertEquals(Blocks.AIR, state.handle().getBlock());
    }

    @Test
    public void getOrNullResolvesKnownKeyWithProperties() {
        ModdedBlockState state = ModdedBlockResolution.getOrNull("minecraft:oak_log[axis=x]", true);
        assertNotNull(state);
        assertEquals(Blocks.OAK_LOG, state.handle().getBlock());
    }

    @Test
    public void unknownPropertyFallsBackToDefaultState() {
        ModdedBlockState state = ModdedBlockResolution.getOrNull("minecraft:oak_log[not_a_property=x]", true);
        assertNotNull(state);
        assertEquals(Blocks.OAK_LOG, state.handle().getBlock());
    }

    @Test
    public void copperChestVariantsReceiveIrisLootAndPostLoadUpdates() {
        for (String key : new String[]{"copper_chest", "exposed_copper_chest", "weathered_copper_chest",
                "oxidized_copper_chest", "waxed_copper_chest", "waxed_exposed_copper_chest",
                "waxed_weathered_copper_chest", "waxed_oxidized_copper_chest"}) {
            ModdedBlockState state = ModdedBlockResolution.getOrNull("minecraft:" + key);
            assertNotNull(key, state);
            assertTrue(key, state.isStorage());
            assertTrue(key, state.isStorageChest());
            assertTrue(key, state.isUpdatable());
        }
    }

    @Test
    public void furnacesAreStorageButNotLootChests() {
        for (Block block : new Block[]{Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER}) {
            assertTrue(block.toString(), ModdedBlockResolution.isStorage(block.defaultBlockState()));
            assertFalse(block.toString(), ModdedBlockResolution.isStorageChest(block.defaultBlockState()));
        }
        for (String key : new String[]{"stone", "ender_chest", "copper_block"}) {
            ModdedBlockState state = ModdedBlockResolution.getOrNull("minecraft:" + key);
            assertNotNull(key, state);
            assertFalse(key, state.isStorage());
            assertFalse(key, state.isStorageChest());
        }
    }

    @Test
    public void cactusPlacementRequiresNativeCactusSupport() {
        assertTrue(ModdedBlockResolution.canPlaceOnto(Blocks.CACTUS, Blocks.CACTUS));
        assertTrue(ModdedBlockResolution.canPlaceOnto(Blocks.CACTUS, Blocks.SAND));
        assertTrue(ModdedBlockResolution.canPlaceOnto(Blocks.CACTUS, Blocks.RED_SAND));
        assertFalse(ModdedBlockResolution.canPlaceOnto(Blocks.CACTUS, Blocks.STONE));
        assertTrue(ModdedBlockResolution.isDecorant(Blocks.CACTUS.defaultBlockState()));
    }
}
