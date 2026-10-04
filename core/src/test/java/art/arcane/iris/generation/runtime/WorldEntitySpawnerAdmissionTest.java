package art.arcane.iris.generation.runtime;

import art.arcane.iris.testsupport.BukkitTestServer;
import io.papermc.paper.InternalAPIBridge;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.Chunk;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class WorldEntitySpawnerAdmissionTest {
    @BeforeClass
    public static void initializeGameRules() throws Exception {
        BukkitTestServer.install();
        RegistryAccess access = BukkitTestServer.registryAccess();
        InternalAPIBridge bridge = mock(InternalAPIBridge.class);
        try (MockedStatic<RegistryAccess> registries = mockStatic(RegistryAccess.class);
             MockedStatic<InternalAPIBridge> internals = mockStatic(InternalAPIBridge.class)) {
            registries.when(RegistryAccess::registryAccess).thenReturn(access);
            internals.when(InternalAPIBridge::get).thenReturn(bridge);
            Class.forName(GameRules.class.getName());
        }
    }

    @Test
    public void gameruleDisablesInitialAndRecurringAmbientSpawns() {
        Chunk chunk = mock(Chunk.class);
        World world = mock(World.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getLoadLevel()).thenReturn(Chunk.LoadLevel.ENTITY_TICKING);
        when(world.getGameRuleValue(GameRules.SPAWN_MOBS)).thenReturn(false);
        assertFalse(WorldEntitySpawner.ambientAllowed(chunk, false));
        assertFalse(WorldEntitySpawner.ambientAllowed(chunk, true));
    }

    @Test
    public void recurringSpawnsRequireEntityTickingChunks() {
        Chunk chunk = mock(Chunk.class);
        World world = mock(World.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getGameRuleValue(GameRules.SPAWN_MOBS)).thenReturn(true);
        for (Chunk.LoadLevel loadLevel : Chunk.LoadLevel.values()) {
            when(chunk.getLoadLevel()).thenReturn(loadLevel);
            if (loadLevel == Chunk.LoadLevel.ENTITY_TICKING) {
                assertTrue(WorldEntitySpawner.ambientAllowed(chunk, false));
            } else {
                assertFalse(WorldEntitySpawner.ambientAllowed(chunk, false));
            }
        }
    }

    @Test
    public void initialAmbientSpawnsRetainChunkPopulationAdmission() {
        Chunk chunk = mock(Chunk.class);
        World world = mock(World.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getGameRuleValue(GameRules.SPAWN_MOBS)).thenReturn(true);
        when(chunk.getLoadLevel()).thenReturn(Chunk.LoadLevel.TICKING);
        assertTrue(WorldEntitySpawner.ambientAllowed(chunk, true));
    }
}
