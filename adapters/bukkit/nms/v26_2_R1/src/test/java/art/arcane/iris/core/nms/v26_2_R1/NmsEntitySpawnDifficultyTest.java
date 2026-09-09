package art.arcane.iris.core.nms.v26_2_R1;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NmsEntitySpawnDifficultyTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void peacefulRejectsEveryNativeForbiddenTypeBeforeBukkitCreatesIt() {
        CraftWorld world = mock(CraftWorld.class);
        when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        NMSBinding binding = mock(NMSBinding.class, CALLS_REAL_METHODS);
        Location location = new Location(world, 0.5, 80, 0.5);
        int rejected = 0;

        for (EntityType type : EntityType.values()) {
            if (type.getEntityClass() == null) {
                continue;
            }
            if (BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type.getKey().toString())).isAllowedInPeaceful()) {
                continue;
            }
            assertNull(type.name(), binding.spawnEntity(location, type, CreatureSpawnEvent.SpawnReason.NATURAL));
            rejected++;
        }

        assertTrue(rejected > 20);
        verify(world, never()).spawn(any(Location.class), any(Class.class), isNull(), any(CreatureSpawnEvent.SpawnReason.class));
    }

    @Test
    public void peacefulPreservesPassiveAndNativeMonsterCategoryExceptions() {
        CraftWorld world = mock(CraftWorld.class);
        when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        NMSBinding binding = mock(NMSBinding.class, CALLS_REAL_METHODS);
        Location location = new Location(world, 0.5, 80, 0.5);
        Entity created = mock(Entity.class);
        doReturn(created).when(world).spawn(eq(location), any(Class.class), isNull(), eq(CreatureSpawnEvent.SpawnReason.NATURAL));

        for (EntityType type : List.of(EntityType.COW, EntityType.VILLAGER, EntityType.WOLF, EntityType.PIGLIN, EntityType.SHULKER)) {
            assertSame(type.name(), created, binding.spawnEntity(location, type, CreatureSpawnEvent.SpawnReason.NATURAL));
        }
    }

    @Test
    public void nonPeacefulDifficultiesPreserveHostileSpawns() {
        CraftWorld world = mock(CraftWorld.class);
        NMSBinding binding = mock(NMSBinding.class, CALLS_REAL_METHODS);
        Location location = new Location(world, 0.5, 80, 0.5);
        Entity created = mock(Entity.class);
        doReturn(created).when(world).spawn(eq(location), any(Class.class), isNull(), eq(CreatureSpawnEvent.SpawnReason.NATURAL));

        for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
            when(world.getDifficulty()).thenReturn(difficulty);
            assertSame(created, binding.spawnEntity(location, EntityType.ZOMBIE, CreatureSpawnEvent.SpawnReason.NATURAL));
        }
    }

    @Test
    public void unrelatedBukkitSpawnFailuresStillPropagate() {
        CraftWorld world = mock(CraftWorld.class);
        when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        NMSBinding binding = mock(NMSBinding.class, CALLS_REAL_METHODS);
        Location location = new Location(world, 0.5, 80, 0.5);
        IllegalStateException failure = new IllegalStateException("Spawn rejected by platform");
        doThrow(failure).when(world).spawn(eq(location), any(Class.class), isNull(), eq(CreatureSpawnEvent.SpawnReason.NATURAL));

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> binding.spawnEntity(location, EntityType.COW, CreatureSpawnEvent.SpawnReason.NATURAL)));
    }
}
