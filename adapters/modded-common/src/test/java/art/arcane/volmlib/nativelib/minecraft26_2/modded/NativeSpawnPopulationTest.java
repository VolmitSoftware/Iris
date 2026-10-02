package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.gamerules.GameRules;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeSpawnPopulationTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void categoryCountsExcludePlayersAndNeighboringChunks() {
        ServerLevel level = mock(ServerLevel.class);
        NativeWorld world = mock(NativeWorld.class);
        when(world.nativeHandle()).thenReturn(level);
        when(level.getMinY()).thenReturn(-64);
        when(level.getMaxY()).thenReturn(320);
        LivingEntity frog = mob(MobCategory.CREATURE, -4, 7);
        LivingEntity zombie = mob(MobCategory.MONSTER, -4, 7);
        LivingEntity neighbor = mob(MobCategory.MONSTER, -3, 7);
        Player player = mock(Player.class);
        when(player.blockPosition()).thenReturn(new BlockPos(-60, 50, 120));
        List<Entity> entities = List.of(frog, frog, frog, zombie, neighbor, player);
        when(level.getEntities(isNull(Entity.class), any(AABB.class), any()))
                .thenAnswer(invocation -> {
                    Predicate<? super Entity> filter = invocation.getArgument(2);
                    return entities.stream().filter(filter).toList();
                });
        assertEquals(Map.of("creature", 3, "monster", 1),
                NativeSpawnQueries.livingEntityCategories(world, -4, 7));
    }

    @Test
    public void ambientAdmissionRequiresMobSpawningAndTickingForRecurringAttempts() {
        ServerLevel level = mock(ServerLevel.class);
        NativeWorld world = mock(NativeWorld.class);
        GameRules rules = mock(GameRules.class);
        when(world.nativeHandle()).thenReturn(level);
        when(level.getMinY()).thenReturn(-64);
        when(level.getGameRules()).thenReturn(rules);
        when(rules.get(GameRules.SPAWN_MOBS)).thenReturn(false);
        assertFalse(NativeSpawnQueries.ambientAllowed(world, -4, 7, true));
        assertFalse(NativeSpawnQueries.ambientAllowed(world, -4, 7, false));
        when(rules.get(GameRules.SPAWN_MOBS)).thenReturn(true);
        assertTrue(NativeSpawnQueries.ambientAllowed(world, -4, 7, true));
        assertFalse(NativeSpawnQueries.ambientAllowed(world, -4, 7, false));
        when(level.isPositionEntityTicking(new BlockPos(-56, -64, 120))).thenReturn(true);
        assertTrue(NativeSpawnQueries.ambientAllowed(world, -4, 7, false));
    }

    private static LivingEntity mob(MobCategory category, int chunkX, int chunkZ) {
        EntityType<?> type = mock(EntityType.class);
        when(type.getCategory()).thenReturn(category);
        LivingEntity entity = mock(LivingEntity.class);
        doReturn(type).when(entity).getType();
        when(entity.blockPosition()).thenReturn(new BlockPos((chunkX << 4) + 8, 32, (chunkZ << 4) + 8));
        return entity;
    }
}
