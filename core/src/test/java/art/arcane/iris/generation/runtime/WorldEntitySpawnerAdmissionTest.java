package art.arcane.iris.generation.runtime;

import art.arcane.iris.testsupport.BukkitTestServer;
import io.papermc.paper.InternalAPIBridge;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Registry;
import org.bukkit.World;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class WorldEntitySpawnerAdmissionTest {
    @BeforeClass
    public static void initializeGameRules() throws Exception {
        BukkitTestServer.install();
        AtomicReference<Object> registered = new AtomicReference<>();
        RegistryAccess access = mock(RegistryAccess.class, invocation -> {
            if (invocation.getMethod().getName().equals("getRegistry")) {
                if (registered.get() == null) {
                    registered.set(Proxy.newProxyInstance(Registry.class.getClassLoader(),
                            new Class<?>[]{Registry.class},
                            (proxy, method, arguments) -> method.getName().equals("getOrThrow")
                                    ? mock(GameRule.class) : null));
                }
                return registered.get();
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
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
