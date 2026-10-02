package art.arcane.iris.generation.runtime;

import art.arcane.iris.platform.bukkit.BukkitEntityType;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class WorldEntitySpawnerPopulationTest {
    @Test
    public void frogsAndPlayersDoNotConsumeMonsterCapacity() {
        Engine engine = mock(Engine.class);
        LivingEntity frog = mock(LivingEntity.class);
        LivingEntity zombie = mock(LivingEntity.class);
        Player player = mock(Player.class);
        when(frog.getType()).thenReturn(EntityType.FROG);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        BukkitEntityType creatureType = mock(BukkitEntityType.class);
        BukkitEntityType monsterType = mock(BukkitEntityType.class);
        when(creatureType.spawnCategory()).thenReturn("creature");
        when(monsterType.spawnCategory()).thenReturn("monster");
        IrisEntitySpawn entry = mock(IrisEntitySpawn.class);
        IrisEntity definition = mock(IrisEntity.class);
        when(definition.spawnCategory()).thenReturn("monster");
        when(entry.getRealEntity(engine)).thenReturn(definition);
        when(entry.getReferenceSpawner()).thenReturn(new IrisSpawner().setMaxEntitiesPerChunk(2));
        try (MockedStatic<BukkitEntityType> types = mockStatic(BukkitEntityType.class)) {
            types.when(() -> BukkitEntityType.of(EntityType.FROG)).thenReturn(creatureType);
            types.when(() -> BukkitEntityType.of(EntityType.ZOMBIE)).thenReturn(monsterType);
            WorldEntitySpawner.ChunkCounter counter = new WorldEntitySpawner.ChunkCounter(
                    new Entity[]{frog, frog, frog, zombie, player});
            assertEquals(1, counter.remainingCapacity(entry, engine));
            when(definition.spawnCategory()).thenReturn("creature");
            assertEquals(0, counter.remainingCapacity(entry, engine));
        }
    }

    @Test
    public void ambientAndWaterPopulationsHaveIndependentBudgets() {
        IrisSpawner spawner = new IrisSpawner().setMaxEntitiesPerChunk(3);
        IrisEntity entity = mock(IrisEntity.class);
        Map<String, Integer> counts = Map.of("monster", 8, "ambient", 2, "water_creature", 3);
        when(entity.spawnCategory()).thenReturn("ambient");
        assertEquals(1, spawner.remainingCapacity(entity, counts));
        when(entity.spawnCategory()).thenReturn("water_creature");
        assertEquals(0, spawner.remainingCapacity(entity, counts));
        when(entity.spawnCategory()).thenReturn("creature");
        assertEquals(3, spawner.remainingCapacity(entity, counts));
        when(entity.spawnCategory()).thenReturn("misc");
        assertEquals(0, spawner.remainingCapacity(entity, counts));
        assertEquals(0, spawner.remainingCapacity(null, counts));
        assertEquals(0, spawner.setMaxEntitiesPerChunk(0).remainingCapacity(entity, counts));
    }
}
