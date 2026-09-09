package art.arcane.iris.modded;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisEntity;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
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
        ModdedPlatform platform = new ModdedPlatform(mock(ModdedLoader.class));

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

        assertNull(ModdedEntitySpawner.spawn(mock(Engine.class), configured, level, 0, 80, 0, new RNG(1L)));

        verify(level, never()).addFreshEntity(any(Entity.class));
        verify(level, never()).enabledFeatures();
    }

    @Test
    public void peacefulRejectsEveryNativeForbiddenTypeBeforeCreation() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        int rejected = 0;

        for (EntityType<?> registered : BuiltInRegistries.ENTITY_TYPE) {
            if (registered.isAllowedInPeaceful()) {
                continue;
            }
            EntityType<?> type = spy(registered);
            assertNull(ModdedEntitySpawner.spawnNative(type, level, BlockPos.ZERO, EntitySpawnReason.NATURAL));
            verify(type, never()).spawn(level, BlockPos.ZERO, EntitySpawnReason.NATURAL);
            rejected++;
        }

        assertTrue(rejected > 20);
    }

    @Test
    public void peacefulPreservesPassiveAndNativeMonsterCategoryExceptions() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        Entity created = mock(Entity.class);

        for (String key : List.of("cow", "villager", "wolf", "piglin", "shulker")) {
            EntityType<?> type = spy(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:" + key)));
            doReturn(created).when(type).spawn(level, BlockPos.ZERO, EntitySpawnReason.NATURAL);

            assertSame(key, created, ModdedEntitySpawner.spawnNative(type, level, BlockPos.ZERO, EntitySpawnReason.NATURAL));
            verify(type).spawn(level, BlockPos.ZERO, EntitySpawnReason.NATURAL);
        }
    }

    @Test
    public void nonPeacefulDifficultiesPreserveHostileSpawns() {
        ServerLevel level = mock(ServerLevel.class);
        Entity created = mock(Entity.class);

        for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
            EntityType<?> type = spy(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:zombie")));
            when(level.getDifficulty()).thenReturn(difficulty);
            doReturn(created).when(type).spawn(level, BlockPos.ZERO, EntitySpawnReason.NATURAL);

            assertSame(created, ModdedEntitySpawner.spawnNative(type, level, BlockPos.ZERO, EntitySpawnReason.NATURAL));
            verify(type).spawn(level, BlockPos.ZERO, EntitySpawnReason.NATURAL);
        }
    }

    @Test
    public void unrelatedNativeSpawnFailuresStillPropagate() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        EntityType<?> type = spy(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow")));
        IllegalStateException failure = new IllegalStateException("Spawn rejected by platform");
        doThrow(failure).when(type).spawn(level, BlockPos.ZERO, EntitySpawnReason.NATURAL);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> ModdedEntitySpawner.spawnNative(type, level, BlockPos.ZERO, EntitySpawnReason.NATURAL)));
    }
}
