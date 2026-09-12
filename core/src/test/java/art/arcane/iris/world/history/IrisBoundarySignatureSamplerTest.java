package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisBoundarySignatureSamplerTest {
    @Test
    public void capturesActualPlatformChunkOnceAndCheckpointsAfterSampling() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        SavedTerrainChunk terrain = terrain();
        when(hooks.captureSavedTerrainChunk(engine, -2, 0)).thenReturn(CompletableFuture.completedFuture(terrain));
        when(hooks.flushSavedTerrainCapture(engine)).thenReturn(CompletableFuture.completedFuture(null));
        try (TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine)) {
            assertSame(terrain.column(-17, 8), capture.sample(-17, 8));
            assertEquals("example:physical", capture.sample(-32, 8).biomeAtSample(0));
            verify(hooks, never()).flushSavedTerrainCapture(engine);
        }
        verify(hooks).captureSavedTerrainChunk(engine, -2, 0);
        verify(hooks).flushSavedTerrainCapture(engine);
        verify(engine, never()).getComplex();
    }

    @Test
    public void platformCaptureAndSaveFailuresReachTheCaller() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        when(hooks.captureSavedTerrainChunk(engine, -2, 0))
                .thenReturn(CompletableFuture.failedFuture(new IOException("capture failure")));
        when(hooks.flushSavedTerrainCapture(engine))
                .thenReturn(CompletableFuture.failedFuture(new IOException("save failure")));
        TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine);
        assertThrows(IOException.class, () -> capture.sample(-17, 8));
        assertThrows(IOException.class, capture::close);
        verify(hooks).flushSavedTerrainCapture(engine);
    }

    @Test
    public void neverWaitsOnThePlatformTickThread() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        when(hooks.isMainThread()).thenReturn(true);
        assertThrows(IOException.class, () -> IrisBoundarySignatureSampler.INSTANCE.open(engine));
        assertThrows(IOException.class, () -> IrisBoundarySignatureSampler.checkpoint(engine));
        verify(hooks, never()).flushSavedTerrainCapture(engine);
    }

    private static SavedTerrainChunk terrain() throws IOException {
        return SavedTerrainChunk.captureBoundary(-2, 0, -16, 16, "minecraft:noise", new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int localX, int y, int localZ) {
                return new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);
            }

            @Override
            public String biome(int localX, int y, int localZ) {
                return "example:physical";
            }
        });
    }
}
