package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedChunkGenerator;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import java.lang.reflect.Field;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.world.level.chunk.ChunkGenerator;
import org.junit.Test;
import org.junit.BeforeClass;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedGenerationBootstrapBindingTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void publishedLevelRequiresCurrentGeneratorIdentity() {
        IrisModdedChunkGenerator generator = generator();
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeWorld level = world();
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(server.world("minecraft:overworld")).thenReturn(level);
        when(level.name()).thenReturn("minecraft:overworld");
        when(((ServerLevel) level.nativeHandle()).getChunkSource()).thenReturn(chunkSource);
        ChunkGenerator nativeGenerator = (ChunkGenerator) generator.nativeGenerator();
        when(chunkSource.getGenerator()).thenReturn(nativeGenerator);

        assertSame(level, generator.requirePublishedLevel(server, "minecraft:overworld"));

        ChunkGenerator otherGenerator = mock(ChunkGenerator.class);
        when(chunkSource.getGenerator()).thenReturn(otherGenerator);
        IllegalStateException mismatch = assertThrows(IllegalStateException.class,
                () -> generator.requirePublishedLevel(server, "minecraft:overworld"));
        assertTrue(mismatch.getMessage().contains("does not use Iris generator"));
    }

    @Test
    public void publishedLevelRejectsMissingServerAndWorld() {
        IrisModdedChunkGenerator generator = generator();
        IllegalStateException missingServer = assertThrows(IllegalStateException.class,
                () -> generator.requirePublishedLevel(null, "minecraft:overworld"));
        assertTrue(missingServer.getMessage().contains("server is unavailable"));

        NativeModdedServer server = mock(NativeModdedServer.class);
        IllegalStateException missingWorld = assertThrows(IllegalStateException.class,
                () -> generator.requirePublishedLevel(server, "minecraft:overworld"));
        assertTrue(missingWorld.getMessage().contains("no published NativeWorld"));
    }

    @Test
    public void staleSnapshotResolvesOnlyCanonicalOverworldOwnedByGenerator() {
        IrisModdedChunkGenerator generator = generator();
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeWorld overworld = world();
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(server.overworld()).thenReturn(overworld);
        when(((ServerLevel) overworld.nativeHandle()).getChunkSource()).thenReturn(chunkSource);
        ChunkGenerator nativeGenerator = (ChunkGenerator) generator.nativeGenerator();
        when(chunkSource.getGenerator()).thenReturn(nativeGenerator);

        assertSame(overworld, generator.resolveBoundLevel(server, List.of()));

        ChunkGenerator otherGenerator = mock(ChunkGenerator.class);
        when(chunkSource.getGenerator()).thenReturn(otherGenerator);
        assertNull(generator.resolveBoundLevel(server, List.of()));
        verify(server, never()).world("minecraft:the_nether");
    }

    @Test
    public void dynamicLevelStillResolvesFromSnapshotWithoutCanonicalLookup() {
        IrisModdedChunkGenerator generator = generator();
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeWorld dynamicLevel = world();
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(((ServerLevel) dynamicLevel.nativeHandle()).getChunkSource()).thenReturn(chunkSource);
        ChunkGenerator nativeGenerator = (ChunkGenerator) generator.nativeGenerator();
        when(chunkSource.getGenerator()).thenReturn(nativeGenerator);

        assertSame(dynamicLevel, generator.resolveBoundLevel(server, List.of(dynamicLevel)));
        verify(server, never()).overworld();
    }

    private static NativeWorld world() {
        NativeWorld world = mock(NativeWorld.class);
        when(world.nativeHandle()).thenReturn(mock(ServerLevel.class));
        return world;
    }

    private static IrisModdedChunkGenerator generator() {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class, CALLS_REAL_METHODS);
        NativeModdedChunkGenerator<?, ?, ?> nativeGenerator = mock(NativeModdedChunkGenerator.class, CALLS_REAL_METHODS);
        try {
            Field field = IrisModdedChunkGenerator.class.getDeclaredField("nativeGenerator");
            field.setAccessible(true);
            field.set(generator, nativeGenerator);
            return generator;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
