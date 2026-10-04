package art.arcane.iris.world.entity;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class IrisEntityLootSeedTest {
    @Test
    public void lootKeyDependsOnSpawnSeedRatherThanEntityHashOrWorldIdentity() {
        IrisEntity first = new IrisEntity().setCustomName("First");
        IrisEntity second = new IrisEntity().setCustomName("Second");
        assertNotEquals(first.hashCode(), second.hashCode());
        LootTable firstTable = bind(first, new RNG(1337L), mock(World.class));
        LootTable secondTable = bind(second, new RNG(1337L), mock(World.class));
        assertEquals(firstTable.getKey(), secondTable.getKey());
        assertEquals("iris:loot-22371b531cd5acd3", firstTable.getKey().toString());
        assertNotEquals(firstTable.getKey(), bind(first, new RNG(1338L), mock(World.class)).getKey());
    }

    @Test
    public void priorCustomizationConsumptionDoesNotChangeTheLootIdentity() {
        IrisEntity definition = new IrisEntity();
        RNG consumed = new RNG(1337L);
        for (int draw = 0; draw < 100; draw++) {
            consumed.nextLong();
        }
        assertEquals(bind(definition, new RNG(1337L), mock(World.class)).getKey(),
                bind(definition, consumed, mock(World.class)).getKey());
    }

    private static LootTable bind(IrisEntity definition, RNG rng, World world) {
        Engine engine = mock(Engine.class);
        Lootable lootable = mock(Lootable.class);
        IrisEntity.BukkitOps.bindLoot(definition, engine, lootable, new Location(world, -31.5, -63.5, 48.5), rng);
        ArgumentCaptor<LootTable> table = ArgumentCaptor.forClass(LootTable.class);
        verify(lootable).setLootTable(table.capture());
        return table.getValue();
    }
}
