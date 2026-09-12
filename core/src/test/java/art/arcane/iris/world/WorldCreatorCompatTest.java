package art.arcane.iris.world;

import org.bukkit.NamespacedKey;
import org.bukkit.WorldCreator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class WorldCreatorCompatTest {
    @Test
    public void keyedPathPreservesWorldKey() {
        NamespacedKey key = new NamespacedKey("iris", "compat_world");

        assertEquals(key, WorldCreatorCompat.ofKey(key).key());
    }

    @Test
    public void keyedCreatorIsBornUnderTheRequestedStartupName() {
        NamespacedKey key = new NamespacedKey("iris", "compat_world");

        WorldCreator creator = WorldCreatorCompat.ofKey(key, "world_iris_compat_world");

        assertEquals("world_iris_compat_world", creator.name());
        assertEquals(key, creator.key());
    }

    @Test
    public void keyedCreatorKeepsThePaperKeyNameWhenNoStartupNameIsGiven() {
        NamespacedKey key = new NamespacedKey("iris", "compat_world");

        WorldCreator creator = WorldCreatorCompat.ofKey(key);

        assertNotEquals("world_iris_compat_world", creator.name());
        assertEquals(key, creator.key());
    }

    @Test
    public void persistentNameIsTheExactConfiguredStartupName() {
        assertEquals(
                "world_iris_compat_world",
                WorldCreatorCompat.persistentWorldName(new NamespacedKey("iris", "compat_world"), "world")
        );
    }

    @Test
    public void logicalNameDerivesTheLogicalNameFromKey() {
        assertEquals("compat_world", IrisWorldStorage.logicalName(new NamespacedKey("iris", "compat_world"), "world"));
        assertEquals("world", IrisWorldStorage.logicalName(NamespacedKey.minecraft("overworld"), "world"));
        assertEquals("world_nether", IrisWorldStorage.logicalName(NamespacedKey.minecraft("the_nether"), "world"));
        assertEquals("world_the_end", IrisWorldStorage.logicalName(NamespacedKey.minecraft("the_end"), "world"));
    }

    @Test
    public void startupNameRoundTripsBackToTheWorldKey() {
        NamespacedKey key = new NamespacedKey("iris", "compat_world");

        String name = WorldCreatorCompat.persistentWorldName(key, "world");

        assertEquals("world_iris_compat_world", name);
        assertEquals(key, IrisWorldStorage.keyFromConfiguredWorldName(name, "world"));
    }
}
