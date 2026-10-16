package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityRuntime;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedLoader;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntitySpawns;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedEntitySpawnDifficultyTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void sharedPlatformSpawnReturnsFalseForPeacefulHostiles() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        ModdedPlatform platform = new ModdedPlatform(mock(NativeModdedLoader.class));

        assertFalse(platform.spawnEntity(new ModdedPlatformWorld(level), "minecraft:zombie", 0.5, 80, 0.5));

        verify(level, never()).addFreshEntity(any(Entity.class));
        verify(level, never()).enabledFeatures();
    }

    @Test
    public void ambientSpawnReturnsNullForPeacefulHostilesBeforeApplyingConfiguration() {
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        IrisEntity configured = new IrisEntity().setType("zombie").setSpawnEffectRiseOutOfGround(false);

        assertNull(ModdedEntitySpawner.spawn(mock(Engine.class), configured, new NativeEntityRuntime(level), 0, 80, 0, new RNG(1L)));

        verify(level, never()).addFreshEntity(any(Entity.class));
        verify(level, never()).enabledFeatures();
    }

    @Test
    public void peacefulRejectsNativeHostilesBeforeCreation() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);

        for (EntityType<?> type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
                EntityType.SPIDER, EntityType.ENDERMAN, EntityType.BLAZE, EntityType.GHAST,
                EntityType.PHANTOM, EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.WITHER,
                EntityType.PIGLIN_BRUTE, EntityType.ZOGLIN, EntityType.WARDEN)) {
            assertNull(NativeEntitySpawns.spawn(type, level, BlockPos.ZERO, MobSpawnType.NATURAL));
        }

        verify(level, never()).enabledFeatures();
        verify(level, never()).addFreshEntityWithPassengers(any(Entity.class));
    }

    @Test
    public void peacefulPreservesPassiveAndNativeMonsterCategoryExceptions() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        IllegalStateException reachedNativeFactory = new IllegalStateException("Native entity factory reached");
        when(level.enabledFeatures()).thenThrow(reachedNativeFactory);

        for (EntityType<?> type : List.of(EntityType.COW, EntityType.VILLAGER, EntityType.WOLF,
                EntityType.PIGLIN, EntityType.HOGLIN, EntityType.SHULKER, EntityType.ENDER_DRAGON)) {
            assertSame(reachedNativeFactory, assertThrows(IllegalStateException.class,
                    () -> NativeEntitySpawns.spawn(type, level, BlockPos.ZERO, MobSpawnType.NATURAL)));
        }
    }

    @Test
    public void nonPeacefulDifficultiesPreserveHostileSpawns() {
        ServerLevel level = mock(ServerLevel.class);
        Entity created = mock(Entity.class);

        for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
            EntityType<?> type = spy(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("minecraft:zombie")));
            when(level.getDifficulty()).thenReturn(difficulty);
            doReturn(created).when(type).spawn(level, BlockPos.ZERO, MobSpawnType.NATURAL);

            assertSame(created, NativeEntitySpawns.spawn(type, level, BlockPos.ZERO, MobSpawnType.NATURAL));
            verify(type).spawn(level, BlockPos.ZERO, MobSpawnType.NATURAL);
        }
    }

    @Test
    public void unrelatedNativeSpawnFailuresStillPropagate() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        EntityType<?> type = spy(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("minecraft:cow")));
        IllegalStateException failure = new IllegalStateException("Spawn rejected by platform");
        doThrow(failure).when(type).spawn(level, BlockPos.ZERO, MobSpawnType.NATURAL);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> NativeEntitySpawns.spawn(type, level, BlockPos.ZERO, MobSpawnType.NATURAL)));
    }
}
