package art.arcane.iris.modded;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.junit.Test;

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
    @Test
    public void publishedLevelRequiresCurrentGeneratorIdentity() {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class, CALLS_REAL_METHODS);
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getChunkSource()).thenReturn(chunkSource);
        when(chunkSource.getGenerator()).thenReturn(generator);

        assertSame(level, generator.requirePublishedLevel(server, Level.OVERWORLD));

        ChunkGenerator otherGenerator = mock(ChunkGenerator.class);
        when(chunkSource.getGenerator()).thenReturn(otherGenerator);
        IllegalStateException mismatch = assertThrows(IllegalStateException.class,
                () -> generator.requirePublishedLevel(server, Level.OVERWORLD));
        assertTrue(mismatch.getMessage().contains("does not use Iris generator"));
    }

    @Test
    public void publishedLevelRejectsMissingServerAndWorld() {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class, CALLS_REAL_METHODS);
        IllegalStateException missingServer = assertThrows(IllegalStateException.class,
                () -> generator.requirePublishedLevel(null, Level.OVERWORLD));
        assertTrue(missingServer.getMessage().contains("server is unavailable"));

        MinecraftServer server = mock(MinecraftServer.class);
        IllegalStateException missingWorld = assertThrows(IllegalStateException.class,
                () -> generator.requirePublishedLevel(server, Level.OVERWORLD));
        assertTrue(missingWorld.getMessage().contains("no published ServerLevel"));
    }

    @Test
    public void staleSnapshotResolvesOnlyCanonicalOverworldOwnedByGenerator() {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class, CALLS_REAL_METHODS);
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel overworld = mock(ServerLevel.class);
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(overworld);
        when(overworld.getChunkSource()).thenReturn(chunkSource);
        when(chunkSource.getGenerator()).thenReturn(generator);

        assertSame(overworld, generator.resolveBoundLevel(server, List.of()));

        ChunkGenerator otherGenerator = mock(ChunkGenerator.class);
        when(chunkSource.getGenerator()).thenReturn(otherGenerator);
        assertNull(generator.resolveBoundLevel(server, List.of()));
        verify(server, never()).getLevel(Level.NETHER);
    }

    @Test
    public void dynamicLevelStillResolvesFromSnapshotWithoutCanonicalLookup() {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class, CALLS_REAL_METHODS);
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel dynamicLevel = mock(ServerLevel.class);
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(dynamicLevel.getChunkSource()).thenReturn(chunkSource);
        when(chunkSource.getGenerator()).thenReturn(generator);

        assertSame(dynamicLevel, generator.resolveBoundLevel(server, List.of(dynamicLevel)));
        verify(server, never()).getLevel(Level.OVERWORLD);
    }
}
