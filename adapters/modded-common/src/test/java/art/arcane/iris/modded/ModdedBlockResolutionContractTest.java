package art.arcane.iris.modded;

import art.arcane.iris.generation.decoration.DecoratorPlatformHooks;
import art.arcane.iris.generation.decoration.IrisSpeleothems;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
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
    public void cactusPlacementRequiresNativeCactusSupport() {
        assertTrue(ModdedBlockResolution.canPlaceOnto(Blocks.CACTUS, Blocks.CACTUS));
        assertTrue(ModdedBlockResolution.canPlaceOnto(Blocks.CACTUS, Blocks.SAND));
        assertTrue(ModdedBlockResolution.canPlaceOnto(Blocks.CACTUS, Blocks.RED_SAND));
        assertFalse(ModdedBlockResolution.canPlaceOnto(Blocks.CACTUS, Blocks.STONE));
        assertTrue(ModdedBlockResolution.isDecorant(Blocks.CACTUS.defaultBlockState()));
    }

    @Test
    public void sugarCanePlacementRequiresNativeSubstrate() {
        for (Block block : new Block[]{Blocks.SUGAR_CANE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT,
                Blocks.PODZOL, Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK,
                Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.SAND, Blocks.RED_SAND, Blocks.SUSPICIOUS_SAND}) {
            assertTrue(block.toString(), ModdedBlockResolution.canPlaceOnto(Blocks.SUGAR_CANE, block));
        }
        for (Block block : new Block[]{Blocks.STONE, Blocks.GRAVEL, Blocks.CLAY, Blocks.DIRT_PATH,
                Blocks.FARMLAND, Blocks.AIR, Blocks.WATER}) {
            assertFalse(block.toString(), ModdedBlockResolution.canPlaceOnto(Blocks.SUGAR_CANE, block));
        }
    }

    @Test
    public void sulfurAndDripstoneTipsReceivePostLoadUpdates() {
        for (Block block : new Block[]{Blocks.SULFUR_SPIKE, Blocks.POINTED_DRIPSTONE}) {
            for (SpeleothemThickness thickness : SpeleothemThickness.values()) {
                assertEquals(thickness == SpeleothemThickness.TIP,
                        ModdedBlockResolution.isUpdatable(block.defaultBlockState()
                                .setValue(SpeleothemBlock.THICKNESS, thickness)));
            }
        }
        assertFalse(ModdedBlockResolution.isUpdatable(Blocks.STONE.defaultBlockState()));
    }
    @Test
    public void nativeSpikesUseModdedSupportFaces() {
        ModdedDecoratorHooks hooks = new ModdedDecoratorHooks();
        DecoratorPlatformHooks.Bindings previous = DecoratorPlatformHooks.bind(hooks, hooks);
        try {
            for (String material : new String[]{"minecraft:sulfur_spike", "minecraft:pointed_dripstone"}) {
                for (boolean upward : new boolean[]{true, false}) {
                    Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 3, 1);
                    PlatformBlockState spike = ModdedBlockResolution.get(material
                            + "[vertical_direction=" + (upward ? "up" : "down") + ",thickness=tip]");
                    int supportY = upward ? 0 : 2;
                    output.set(0, 1, 0, spike);
                    output.set(0, supportY, 0, ModdedBlockResolution.get("minecraft:stone_slab[type=top]"));
                    assertEquals(upward, IrisSpeleothems.isSupported(spike, output, 0, 0, 1));
                    output.set(0, supportY, 0, ModdedBlockResolution.get("minecraft:stone_slab[type=bottom]"));
                    assertEquals(!upward, IrisSpeleothems.isSupported(spike, output, 0, 0, 1));
                }
            }
        } finally {
            DecoratorPlatformHooks.restore(previous);
        }
    }

}
