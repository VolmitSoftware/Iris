package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.WorldCreatorCompat;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WorldLifecycleRequestTest {
    @Test
    public void requestCarriesTheStartupNameTheCreatorWasBornWith() {
        NamespacedKey worldKey = new NamespacedKey("iris", "mvtest");
        WorldCreator creator = WorldCreatorCompat.ofKey(worldKey, "world_iris_mvtest")
                .environment(World.Environment.NORMAL)
                .seed(99L);

        WorldLifecycleRequest request = WorldLifecycleRequest.fromCreator(
                creator,
                false,
                false,
                WorldLifecycleCaller.CREATE);

        assertEquals("world_iris_mvtest", request.worldName());
        assertEquals(worldKey, request.worldKey());
    }

    @Test
    public void rebuiltCreatorKeepsBothTheStartupNameAndTheWorldKey() {
        NamespacedKey worldKey = new NamespacedKey("iris", "mvtest");
        WorldLifecycleRequest request = WorldLifecycleRequest.fromCreator(
                WorldCreatorCompat.ofKey(worldKey, "world_iris_mvtest")
                        .environment(World.Environment.NORMAL)
                        .seed(99L),
                false,
                false,
                WorldLifecycleCaller.CREATE);

        WorldCreator rebuilt = request.toWorldCreator();

        assertEquals("world_iris_mvtest", rebuilt.name());
        assertEquals(worldKey, rebuilt.key());
        assertEquals(99L, rebuilt.seed());
    }
}
