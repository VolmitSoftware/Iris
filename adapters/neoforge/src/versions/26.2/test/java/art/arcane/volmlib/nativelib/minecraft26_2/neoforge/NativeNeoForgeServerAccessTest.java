package art.arcane.volmlib.nativelib.minecraft26_2.neoforge;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerAccess;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerLevels;
import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeNeoForgeServerAccessTest {
    private static final ResourceKey<Level> RUNTIME_DIMENSION =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("irisworldgen", "runtime"));

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @After
    public void forgetLevelSnapshot() {
        ModdedServerLevels.forget();
    }

    @Test
    public void runtimeLevelsJoinAndLeaveTheServerTickView() throws ReflectiveOperationException {
        ModdedServerAccess access = new NativeNeoForgeLoader(
                new NativeLoaderOptions("irisworldgen", "treefeller", BooleanSupplier::getAsBoolean)).serverAccess();
        MinecraftServer server = serverWithLiveLevelMap();
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(RUNTIME_DIMENSION);

        assertNull(access.putLevelIfAbsent(server, RUNTIME_DIMENSION, level));
        assertTrue(access.hasLevel(server, RUNTIME_DIMENSION));
        assertTrue(ticks(server, level));
        assertSame(level, ModdedServerLevels.level(server, RUNTIME_DIMENSION));

        assertSame(level, access.removeLevel(server, RUNTIME_DIMENSION));
        assertFalse(access.hasLevel(server, RUNTIME_DIMENSION));
        assertFalse(ticks(server, level));
        assertNull(ModdedServerLevels.level(server, RUNTIME_DIMENSION));

        assertNull(access.putLevel(server, RUNTIME_DIMENSION, level));
        assertTrue(ticks(server, level));
        assertSame(level, ModdedServerLevels.level(server, RUNTIME_DIMENSION));
    }

    private static MinecraftServer serverWithLiveLevelMap() throws ReflectiveOperationException {
        MinecraftServer server = mock(MinecraftServer.class);
        Field levels = MinecraftServer.class.getDeclaredField("levels");
        levels.setAccessible(true);
        levels.set(server, new LinkedHashMap<ResourceKey<Level>, ServerLevel>());
        doCallRealMethod().when(server).getAllLevels();
        doCallRealMethod().when(server).markWorldsDirty();
        return server;
    }

    private static boolean ticks(MinecraftServer server, ServerLevel expected) throws ReflectiveOperationException {
        Method worldArray = MinecraftServer.class.getDeclaredMethod("getWorldArray");
        worldArray.setAccessible(true);
        for (ServerLevel level : (ServerLevel[]) worldArray.invoke(server)) {
            if (level == expected) {
                return true;
            }
        }
        return false;
    }
}
