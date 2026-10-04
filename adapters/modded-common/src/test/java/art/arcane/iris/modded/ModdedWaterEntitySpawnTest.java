package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityRuntime;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnedEntity;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.pack.value.IrisPosition;

import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisSpawnGroup;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.world.entity.IrisEntitySpawn.SpawnContext;
import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedWaterEntitySpawnTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                state.initCache();
            }
        }
        BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, Map.of(
                BlockTags.BLOCKS_MOTION, List.of(Blocks.OAK_FENCE.builtInRegistryHolder(),
                        Blocks.STONE.builtInRegistryHolder())))).apply();
    }

    @Test
    public void tadpoleVolumeRequiresWaterAndAcceptsAquaticPlants() {
        ServerLevel level = mock(ServerLevel.class);
        IrisEntity entity = new IrisEntity().setType("tadpole").setSurface(IrisSurface.WATER);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.WATER.defaultBlockState());
        assertTrue(ModdedEntitySpawner.isAreaClearForSpawn(new NativeEntityRuntime(level), entity, 0, 78, 0));
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.SEAGRASS.defaultBlockState());
        assertTrue(ModdedEntitySpawner.isAreaClearForSpawn(new NativeEntityRuntime(level), entity, 0, 78, 0));
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        assertFalse(ModdedEntitySpawner.isAreaClearForSpawn(new NativeEntityRuntime(level), entity, 0, 79, 0));
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.LAVA.defaultBlockState());
        assertFalse(ModdedEntitySpawner.isAreaClearForSpawn(new NativeEntityRuntime(level), entity, 0, 78, 0));
    }

    @Test
    public void lavaSurfaceRequiresLavaInsteadOfAirOrWater() {
        ServerLevel level = mock(ServerLevel.class);
        IrisEntity entity = new IrisEntity().setType("tadpole").setSurface(IrisSurface.LAVA);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.LAVA.defaultBlockState());
        assertTrue(ModdedEntitySpawner.isAreaClearForSpawn(new NativeEntityRuntime(level), entity, 0, 78, 0));
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.WATER.defaultBlockState());
        assertFalse(ModdedEntitySpawner.isAreaClearForSpawn(new NativeEntityRuntime(level), entity, 0, 78, 0));
    }

    @Test
    public void nativeLandClearanceAcceptsEveryAirBlockAndRejectsStone() {
        ServerLevel level = mock(ServerLevel.class);
        NativeEntityRuntime runtime = new NativeEntityRuntime(level);
        IrisEntity entity = new IrisEntity().setType("zombie").setSurface(IrisSurface.LAND);
        for (Block air : List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR)) {
            when(level.getBlockState(any(BlockPos.class))).thenReturn(air.defaultBlockState());
            assertTrue(ModdedEntitySpawner.isAreaClearForSpawn(runtime, entity, -1, -49, -1));
        }
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos position = invocation.getArgument(0);
            return position.getY() == -48 ? Blocks.STONE.defaultBlockState() : Blocks.CAVE_AIR.defaultBlockState();
        });
        assertFalse(ModdedEntitySpawner.isAreaClearForSpawn(runtime, entity, -1, -49, -1));
    }

    @Test
    public void waterloggedSolidBlocksStillObstructWaterSpawns() {
        assertTrue(ModdedWorldManager.matchesSurface(IrisSurface.WATER,
                ModdedBlockState.of(Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), null)));
        assertFalse(ModdedWorldManager.matchesSurface(IrisSurface.WATER,
                ModdedBlockState.of(Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), null)));
    }

    @Test
    public void fractionalFluidVolumeIncludesHorizontalEdgesAndCenteredHeight() {
        Set<BlockPos> checked = new HashSet<>();
        assertTrue(NativeEntityRuntime.isFluidAreaClearForSpawn(-1, 78, -1, 1.3F, 0.8F, (x, y, z) -> {
            checked.add(new BlockPos(x, y, z));
            return true;
        }));
        assertEquals(18, checked.size());
        assertTrue(checked.contains(new BlockPos(-2, 79, -2)));
        assertTrue(checked.contains(new BlockPos(0, 78, 0)));
        assertFalse(NativeEntityRuntime.isFluidAreaClearForSpawn(-1, 78, -1, 1.3F, 0.8F,
                (x, y, z) -> !new BlockPos(x, y, z).equals(new BlockPos(-2, 79, -2))));
    }

    @Test
    public void exactUpperBoundaryDoesNotRequireWaterOutsideTheBody() {
        Set<BlockPos> checked = new HashSet<>();
        assertTrue(NativeEntityRuntime.isFluidAreaClearForSpawn(0, 78, 0, 1F, 0.5F, (x, y, z) -> {
            checked.add(new BlockPos(x, y, z));
            return true;
        }));
        assertEquals(Set.of(new BlockPos(0, 78, 0)), checked);
    }

    @Test
    public void spawnAndRiseStartUseFloorHeightOnLandAndCenterHeightInFluids() {
        for (IrisSurface surface : List.of(IrisSurface.LAND, IrisSurface.WATER, IrisSurface.LAVA)) {
            for (boolean rise : new boolean[]{false, true}) {
                Engine engine = mock(Engine.class);
                NativeEntityRuntime runtime = mock(NativeEntityRuntime.class);
                NativeSpawnedEntity created = mock(NativeSpawnedEntity.class);
                IrisEntity entity = new IrisEntity().setSpecialType("test:mob")
                        .setSurface(surface).setSpawnEffectRiseOutOfGround(rise);
                when(runtime.chunksSafe(-1, 0)).thenReturn(true);
                when(runtime.hasPlayersNearby(any(NativeEntityRuntime.Position.class), eq(32D))).thenReturn(true);
                when(runtime.spawnCustom(eq("test:mob"), any(NativeEntityRuntime.Position.class), any()))
                        .thenReturn(created);

                assertSame(created, ModdedEntitySpawner.spawn(engine, entity, runtime, -1, -49, 0, new RNG(1L)));

                double targetY = -49 + (surface.isFluid() ? 0.5 : 0) - (rise ? 5 : 0);
                verify(runtime).spawnCustom(eq("test:mob"),
                        eq(new NativeEntityRuntime.Position(-0.5, targetY, 0.5)), any());
            }
        }
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

    @Test
    public void caveFloorMarkerSpawnsAboveSupportAtNegativeWorldHeight() throws ReflectiveOperationException {
        Engine engine = mock(Engine.class);
        EngineMantle mantle = mock(EngineMantle.class);
        when(engine.getMantle()).thenReturn(mantle);
        when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
        when(mantle.findMarkers(-2, 3, MarkerMatter.CAVE_FLOOR))
                .thenReturn(new KList<>(new IrisPosition(-25, -49, 57)));
        NativeWorld world = mock(NativeWorld.class);
        when(world.minHeight()).thenReturn(-64);
        when(world.maxHeight()).thenReturn(320);
        when(world.nativeHandle()).thenReturn(mock(ServerLevel.class));
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                ModdedBlockState.of((int) invocation.getArgument(1) <= -50
                        ? Blocks.STONE.defaultBlockState() : Blocks.CAVE_AIR.defaultBlockState(), null));
        IrisEntity entity = new IrisEntity().setSurface(IrisSurface.LAND);
        IrisEntitySpawn entry = spy(new IrisEntitySpawn().setMinSpawns(1).setMaxSpawns(1));
        doReturn(entity).when(entry).getRealEntity(engine);
        IrisSpawner spawner = new IrisSpawner().setGroup(IrisSpawnGroup.CAVE);
        Method spawn = ModdedWorldManager.class.getDeclaredMethod("spawnEntry", NativeWorld.class,
                IrisEntitySpawn.class, int.class, int.class, int.class, RNG.class, SpawnContext.class);
        spawn.setAccessible(true);
        ModdedWorldManager manager = new ModdedWorldManager(engine);
        try (MockedStatic<ModdedEntitySpawner> entities = mockStatic(ModdedEntitySpawner.class)) {
            entities.when(() -> ModdedEntitySpawner.isAreaClearForSpawn(any(NativeEntityRuntime.class),
                    eq(entity), eq(-25), eq(-49), eq(57))).thenReturn(true);
            entities.when(() -> ModdedEntitySpawner.spawn(eq(engine), eq(entity), any(NativeEntityRuntime.class),
                    eq(-25), eq(-49), eq(57), any(RNG.class))).thenReturn(mock(NativeSpawnedEntity.class));

            assertEquals(1, spawn.invoke(manager, world, entry, -2, 3, 1, new RNG(1L), new SpawnContext(spawner, null, false)));

            entities.verify(() -> ModdedEntitySpawner.spawn(eq(engine), eq(entity), any(NativeEntityRuntime.class),
                    eq(-25), eq(-49), eq(57), any(RNG.class)));
        } finally {
            manager.close();
        }
    }
}
