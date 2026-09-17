package art.arcane.iris.engine.object.world.biome.custom;

import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustomSpawn;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IrisBiomeCustomSpawnTest {
    @Test
    public void normalizesNamespacedEntityKey() {
        IrisBiomeCustomSpawn spawn = new IrisBiomeCustomSpawn().setType(" Minecraft:Slime ");

        assertEquals("minecraft:slime", spawn.getTypeKey());
    }

    @Test
    public void prefixesBareEntityKey() {
        IrisBiomeCustomSpawn spawn = new IrisBiomeCustomSpawn().setType("Slime");

        assertEquals("minecraft:slime", spawn.getTypeKey());
    }

    @Test
    public void returnsNullForBlankEntityKey() {
        IrisBiomeCustomSpawn spawn = new IrisBiomeCustomSpawn().setType("  ");

        assertNull(spawn.getTypeKey());
    }
}
