package art.arcane.iris.probe;

import art.arcane.iris.modded.ModdedGroundSurvival;
import art.arcane.iris.platform.bukkit.ServerGroundSurvival;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Both platforms ask the server's own canSurvive rule where a plant may stand, against the real block tags. */
public final class HeadlessGroundSurvivalTest {
    private static ServerGroundSurvival bukkit;

    @BeforeClass
    public static void bootstrap() throws ReflectiveOperationException {
        HeadlessNativeBootstrap.initialize();
        bukkit = ServerGroundSurvival.connect(HeadlessGroundSurvivalTest.class.getClassLoader());
    }

    @Test
    public void grassStandsOnSoilButNotOnStone() {
        assertPlacement(true, Blocks.SHORT_GRASS, Blocks.GRASS_BLOCK);
        assertPlacement(true, Blocks.SHORT_GRASS, Blocks.PODZOL);
        assertPlacement(false, Blocks.SHORT_GRASS, Blocks.STONE);
        assertPlacement(false, Blocks.POPPY, Blocks.STONE);
        assertPlacement(false, Blocks.FERN, Blocks.OAK_LEAVES);
    }

    @Test
    public void cactusNeedsSandOrCactus() {
        for (Block ground : new Block[]{Blocks.CACTUS, Blocks.SAND, Blocks.RED_SAND}) {
            assertPlacement(true, Blocks.CACTUS, ground);
        }
        assertPlacement(false, Blocks.CACTUS, Blocks.STONE);
    }

    @Test
    public void sugarCaneNeedsItsSubstrate() {
        for (Block ground : new Block[]{Blocks.SUGAR_CANE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT,
                Blocks.PODZOL, Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK,
                Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.SAND, Blocks.RED_SAND, Blocks.SUSPICIOUS_SAND}) {
            assertPlacement(true, Blocks.SUGAR_CANE, ground);
        }
        for (Block ground : new Block[]{Blocks.STONE, Blocks.GRAVEL, Blocks.CLAY, Blocks.DIRT_PATH,
                Blocks.FARMLAND, Blocks.WATER}) {
            assertPlacement(false, Blocks.SUGAR_CANE, ground);
        }
    }

    @Test
    public void netherGroundCoverAcceptsNyliumAndSoulSoilButRejectsBarrenBlocks() {
        for (Block plant : new Block[]{Blocks.CRIMSON_ROOTS, Blocks.WARPED_ROOTS, Blocks.NETHER_SPROUTS}) {
            for (Block ground : new Block[]{Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.SOUL_SOIL}) {
                assertPlacement(true, plant, ground);
            }
            for (Block ground : new Block[]{Blocks.NETHERRACK, Blocks.STONE, Blocks.GRAVEL}) {
                assertPlacement(false, plant, ground);
            }
        }
    }

    @Test
    public void blocksThatRestOnNoGroundStillFit() {
        assertPlacement(true, Blocks.WALL_TORCH, Blocks.STONE);
        assertPlacement(true, Blocks.VINE, Blocks.SAND);
    }

    private static void assertPlacement(boolean expected, Block plant, Block ground) {
        String pair = plant + " on " + ground;
        assertEquals("modded " + pair, expected,
                new ModdedGroundSurvival().canSurviveOn(plant.defaultBlockState(), ground.defaultBlockState()));
        assertEquals("bukkit " + pair, expected, bukkit.canSurviveOn(plant.defaultBlockState(), ground.defaultBlockState()));
    }
}
