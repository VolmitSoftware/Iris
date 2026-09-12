package art.arcane.iris.modded;

import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisSpawnGroup;
import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModdedWaterEntitySpawnTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void tadpoleVolumeRequiresWaterAndAcceptsAquaticPlants() {
        ServerLevel level = mock(ServerLevel.class);
        IrisEntity entity = new IrisEntity().setType("tadpole").setSurface(IrisSurface.WATER);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.WATER.defaultBlockState());
        assertTrue(ModdedEntitySpawner.isAreaClearForSpawn(level, entity, 0, 78, 0));
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.SEAGRASS.defaultBlockState());
        assertTrue(ModdedEntitySpawner.isAreaClearForSpawn(level, entity, 0, 78, 0));
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        assertFalse(ModdedEntitySpawner.isAreaClearForSpawn(level, entity, 0, 79, 0));
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.LAVA.defaultBlockState());
        assertFalse(ModdedEntitySpawner.isAreaClearForSpawn(level, entity, 0, 78, 0));
    }

    @Test
    public void lavaSurfaceRequiresLavaInsteadOfAirOrWater() {
        ServerLevel level = mock(ServerLevel.class);
        IrisEntity entity = new IrisEntity().setType("tadpole").setSurface(IrisSurface.LAVA);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.LAVA.defaultBlockState());
        assertTrue(ModdedEntitySpawner.isAreaClearForSpawn(level, entity, 0, 78, 0));
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.WATER.defaultBlockState());
        assertFalse(ModdedEntitySpawner.isAreaClearForSpawn(level, entity, 0, 78, 0));
    }

    @Test
    public void waterloggedSolidBlocksStillObstructWaterSpawns() {
        assertTrue(ModdedWorldManager.matchesSurface(IrisSurface.WATER,
                Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)));
        assertFalse(ModdedWorldManager.matchesSurface(IrisSurface.WATER,
                Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)));
    }

    @Test
    public void fractionalFluidVolumeIncludesHorizontalEdgesAndCenteredHeight() {
        Set<BlockPos> checked = new HashSet<>();
        assertTrue(ModdedEntitySpawner.isFluidAreaClearForSpawn(-1, 78, -1, 1.3F, 0.8F, position -> {
            checked.add(position);
            return true;
        }));
        assertEquals(18, checked.size());
        assertTrue(checked.contains(new BlockPos(-2, 79, -2)));
        assertTrue(checked.contains(new BlockPos(0, 78, 0)));
        assertFalse(ModdedEntitySpawner.isFluidAreaClearForSpawn(-1, 78, -1, 1.3F, 0.8F,
                position -> !position.equals(new BlockPos(-2, 79, -2))));
    }

    @Test
    public void exactUpperBoundaryDoesNotRequireWaterOutsideTheBody() {
        Set<BlockPos> checked = new HashSet<>();
        assertTrue(ModdedEntitySpawner.isFluidAreaClearForSpawn(0, 78, 0, 1F, 0.5F, position -> {
            checked.add(position);
            return true;
        }));
        assertEquals(Set.of(new BlockPos(0, 78, 0)), checked);
    }

    @Test
    public void mixedSpawnerUsesFluidDepthWithoutChangingLandMembers() {
        RNG rng = new RNG(1L);
        assertEquals(Integer.valueOf(78), IrisEntitySpawn.selectSurfaceSpawnY(
                IrisSpawnGroup.NORMAL, IrisSurface.WATER, 77, 78, rng));
        assertEquals(Integer.valueOf(79), IrisEntitySpawn.selectSurfaceSpawnY(
                IrisSpawnGroup.NORMAL, IrisSurface.LAND, 77, 78, rng));
        assertNull(IrisEntitySpawn.selectSurfaceSpawnY(IrisSpawnGroup.NORMAL, IrisSurface.WATER, 78, 78, rng));
        assertNull(IrisEntitySpawn.selectSurfaceSpawnY(IrisSpawnGroup.UNDERWATER, IrisSurface.WATER, 78, 78, rng));
    }
}
