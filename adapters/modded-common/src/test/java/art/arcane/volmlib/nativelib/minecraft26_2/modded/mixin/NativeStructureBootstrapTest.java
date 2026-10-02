package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedChunkGenerator;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeStructureBootstrapTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void levelConstructionDefersNativeStructureInitialization() {
        ServerLevelStructureBootstrapMixin mixin = mock(ServerLevelStructureBootstrapMixin.class, CALLS_REAL_METHODS);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        NativeModdedChunkGenerator<?, ?, ?> generator = mock(NativeModdedChunkGenerator.class);
        ChunkGeneratorStructureState state = mock(ChunkGeneratorStructureState.class);
        when(mixin.getChunkSource()).thenReturn(chunks);
        when(chunks.getGenerator()).thenReturn(generator);

        mixin.iris$initializeStructureState(state);

        verify(state, never()).ensureStructuresGenerated();
    }

    @Test
    public void levelConstructionStillInitializesVanillaStructures() {
        ServerLevelStructureBootstrapMixin mixin = mock(ServerLevelStructureBootstrapMixin.class, CALLS_REAL_METHODS);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        ChunkGenerator generator = mock(ChunkGenerator.class);
        ChunkGeneratorStructureState state = mock(ChunkGeneratorStructureState.class);
        when(mixin.getChunkSource()).thenReturn(chunks);
        when(chunks.getGenerator()).thenReturn(generator);

        mixin.iris$initializeStructureState(state);

        verify(state).ensureStructuresGenerated();
    }

    @Test
    public void boundNativeWorldInitializesItsDeferredStructures() {
        NativeModdedChunkGenerator<?, ?, ?> generator = mock(NativeModdedChunkGenerator.class, CALLS_REAL_METHODS);
        NativeWorld world = mock(NativeWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        ChunkGeneratorStructureState state = mock(ChunkGeneratorStructureState.class);
        when(world.nativeHandle()).thenReturn(level);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getGeneratorState()).thenReturn(state);

        generator.initializeStructureState(world);

        verify(state).ensureStructuresGenerated();
    }
}
