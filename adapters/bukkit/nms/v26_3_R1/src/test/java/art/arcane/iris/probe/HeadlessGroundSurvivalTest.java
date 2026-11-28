package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.common.block.GroundSurvival;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Both platforms ask the server's own canSurvive rule where a plant may stand, against the real block tags. */
public final class HeadlessGroundSurvivalTest {
    private static final Logger NATIVE_LOG = Logger.getLogger("VolmLib-Native");

    @BeforeClass
    public static void bootstrap() {
        HeadlessNativeBootstrap.initialize();
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
                Blocks.FARMLAND, Blocks.AIR, Blocks.WATER}) {
            assertPlacement(false, Blocks.SUGAR_CANE, ground);
        }
    }

    @Test
    public void netherGroundCoverAcceptsNyliumAndSoulSoilButRejectsBarrenBlocks() {
        for (Block plant : new Block[]{Blocks.CRIMSON_ROOTS, Blocks.WARPED_ROOTS, Blocks.NETHER_SPROUTS}) {
            for (Block ground : new Block[]{Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.SOUL_SOIL}) {
                assertPlacement(true, plant, ground);
            }
            for (Block ground : new Block[]{Blocks.NETHERRACK, Blocks.STONE, Blocks.GRAVEL, Blocks.AIR}) {
                assertPlacement(false, plant, ground);
            }
        }
    }

    @Test
    public void blocksThatRestOnNoGroundStillFit() {
        assertPlacement(true, Blocks.WALL_TORCH, Blocks.STONE);
        assertPlacement(true, Blocks.VINE, Blocks.SAND);
        assertPlacement(true, Blocks.COCOA, Blocks.STONE);
    }

    @Test
    public void deadBushStaysOffGrassAsPacksExpect() {
        assertTrue(moddedPlacement(Blocks.DEAD_BUSH, Blocks.SAND));
        assertFalse(moddedPlacement(Blocks.DEAD_BUSH, Blocks.GRASS_BLOCK));
    }

    @Test
    public void plantsThatReadMoreOfTheLevelAreAnsweredByTheirOwnRule() {
        List<LogRecord> warnings = warningsDuring(() -> {
            assertPlacement(true, Blocks.BAMBOO, Blocks.GRASS_BLOCK);
            assertPlacement(false, Blocks.BAMBOO, Blocks.STONE);
            assertPlacement(true, Blocks.KELP, Blocks.STONE);
            assertPlacement(false, Blocks.KELP, Blocks.MAGMA_BLOCK);
            assertPlacement(true, Blocks.SEAGRASS, Blocks.SAND);
            assertPlacement(false, Blocks.SEAGRASS, Blocks.MAGMA_BLOCK);
            assertPlacement(true, Blocks.LILY_PAD, Blocks.WATER);
            assertPlacement(false, Blocks.LILY_PAD, Blocks.STONE);
            assertPlacement(true, Blocks.SWEET_BERRY_BUSH, Blocks.GRASS_BLOCK);
            assertPlacement(false, Blocks.SWEET_BERRY_BUSH, Blocks.STONE);
            assertPlacement(true, Blocks.BROWN_MUSHROOM, Blocks.STONE);
            assertPlacement(true, Blocks.RED_MUSHROOM, Blocks.MYCELIUM);
            assertPlacement(false, Blocks.BROWN_MUSHROOM, Blocks.GLASS);
            assertPlacement(true, Blocks.CRIMSON_FUNGUS, Blocks.CRIMSON_NYLIUM);
            assertPlacement(false, Blocks.WARPED_FUNGUS, Blocks.NETHERRACK);
            assertPlacement(true, Blocks.WHEAT, Blocks.FARMLAND);
            assertPlacement(false, Blocks.CARROTS, Blocks.DIRT);
        });
        assertEquals(messages(warnings), 0, warnings.size());
    }

    @Test
    public void everyVanillaBlockIsAnsweredWithoutFailingOpen() {
        GroundSurvival survival = new GroundSurvival();
        List<LogRecord> warnings = warningsDuring(() -> {
            for (Block plant : BuiltInRegistries.BLOCK) {
                if (needsARunningPaperServer(plant)) {
                    continue;
                }
                for (Block ground : new Block[]{Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.WATER}) {
                    survival.canSurviveOn(plant.defaultBlockState(), ground.defaultBlockState());
                }
            }
        });
        assertEquals(messages(warnings), 0, warnings.size());
    }

    @Test
    public void aRuleThatThrowsLetsThePlantStandAndWarnsOncePerBlock() {
        BlockState firstState = throwingStateOf(Blocks.SHORT_GRASS);
        BlockState secondState = throwingStateOf(Blocks.SHORT_GRASS);
        GroundSurvival survival = new GroundSurvival();
        List<LogRecord> warnings = warningsDuring(() -> {
            for (BlockState plant : new BlockState[]{firstState, secondState}) {
                for (Block ground : new Block[]{Blocks.STONE, Blocks.SAND, Blocks.GRASS_BLOCK}) {
                    assertTrue(survival.canSurviveOn(plant, ground.defaultBlockState()));
                }
            }
        });
        assertEquals(messages(warnings), 1, warnings.size());
    }

    // Paper's chorus plant reads Paper's global configuration, which only a running server loads.
    private static boolean needsARunningPaperServer(Block plant) {
        return plant == Blocks.CHORUS_PLANT;
    }

    private static BlockState throwingStateOf(Block block) {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        when(state.canSurvive(any(), any())).thenThrow(new IllegalStateException("custom rule failed"));
        return state;
    }

    private static void assertPlacement(boolean expected, Block plant, Block ground) {
        String pair = plant + " on " + ground;
        assertEquals("modded " + pair, expected, moddedPlacement(plant, ground));
        if (!ground.defaultBlockState().isAir()) {
            assertEquals("bukkit native " + pair, expected,
                    new GroundSurvival().canSurviveOn(plant.defaultBlockState(), ground.defaultBlockState()));
        }
    }

    private static boolean moddedPlacement(Block plant, Block ground) {
        return wrap(plant).canPlaceOnto(wrap(ground));
    }

    private static ModdedBlockState wrap(Block block) {
        return ModdedBlockState.deferred(block.defaultBlockState(), Map.of(), "iris:test");
    }

    private static List<LogRecord> warningsDuring(Runnable checks) {
        List<LogRecord> warnings = new CopyOnWriteArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        NATIVE_LOG.addHandler(capture);
        try {
            checks.run();
        } finally {
            NATIVE_LOG.removeHandler(capture);
        }
        return warnings;
    }

    private static String messages(List<LogRecord> warnings) {
        StringBuilder text = new StringBuilder();
        for (LogRecord warning : warnings) {
            text.append(warning.getMessage()).append(" (").append(warning.getThrown()).append(")\n");
        }
        return text.toString();
    }
}
