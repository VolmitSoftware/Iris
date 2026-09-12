package art.arcane.iris.nativegen;

import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.GenerationActivation;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IrisStaticObjectLayer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public final class NativeGenerationWriteGuardTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void staticObjectsProtectRelativeHeightBeforeStackSampling() {
        IrisStaticObjectLayer objects = mock(IrisStaticObjectLayer.class);
        DimensionStackContext stack = mock(DimensionStackContext.class);
        when(objects.contains(-3, 74, -7)).thenReturn(true);

        Predicate<BlockPos> protectedPositions = NativeGenerationWriteGuard.protectedPositions(objects, stack, -64);

        assertTrue(protectedPositions.test(new BlockPos(-3, 10, -7)));
        verifyNoInteractions(stack);
        assertFalse(NativeGenerationWriteGuard.protectedPositions(objects, null, -64)
                .test(new BlockPos(-3, 11, -7)));
    }

    @Test
    public void emptyStaticLayerSkipsContainsAndCachesEachSignedColumn() {
        IrisStaticObjectLayer objects = mock(IrisStaticObjectLayer.class);
        DimensionStackContext stack = mock(DimensionStackContext.class);
        DimensionStackLayout negative = mock(DimensionStackLayout.class);
        DimensionStackLayout positive = mock(DimensionStackLayout.class);
        when(objects.isEmpty()).thenReturn(true);
        when(stack.sample(-1, -1)).thenReturn(negative);
        when(stack.sample(-1, 1)).thenReturn(positive);
        when(negative.isHostFeatureProtectedY(64)).thenReturn(true);
        when(positive.isHostFeatureProtectedY(65)).thenReturn(true);

        Predicate<BlockPos> protectedPositions = NativeGenerationWriteGuard.protectedPositions(objects, stack, -64);

        assertTrue(protectedPositions.test(new BlockPos(-1, 0, -1)));
        assertFalse(protectedPositions.test(new BlockPos(-1, 1, -1)));
        assertTrue(protectedPositions.test(new BlockPos(-1, 1, 1)));
        verify(stack, times(1)).sample(-1, -1);
        verify(stack, times(1)).sample(-1, 1);
        verify(objects, never()).contains(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void preTerrainSavedStampDistinguishesEarlierNativeStarts() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationActivation active = mock(GenerationActivation.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.activeActivation()).thenReturn(active);
        when(active.activationId()).thenReturn(4L);
        assertTrue(NativeGenerationWriteGuard.isHistoricalStructure(engine, 3));
        assertFalse(NativeGenerationWriteGuard.isHistoricalStructure(engine, 0));
        assertFalse(NativeGenerationWriteGuard.isHistoricalStructure(engine, 4));
        assertFalse(NativeGenerationWriteGuard.isHistoricalStructure(engine, 5));
    }

    @Test
    public void partialNeighborsMayFinishButCompletedHistoricalNeighborRejectsPass() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        WorldGenLevel region = mock(WorldGenLevel.class);
        ChunkAccess partial = mock(ChunkAccess.class);
        when(partial.getPersistedStatus()).thenReturn(ChunkStatus.NOISE);
        when(region.getChunk(anyInt(), anyInt())).thenReturn(partial);
        assertTrue(NativeGenerationWriteGuard.allowsDecoration(engine, region, new ChunkPos(0, 0)));
        ChunkAccess complete = mock(ChunkAccess.class);
        when(complete.getPersistedStatus()).thenReturn(ChunkStatus.FULL);
        when(region.getChunk(1, -1)).thenReturn(complete);
        assertFalse(NativeGenerationWriteGuard.allowsDecoration(engine, region, new ChunkPos(0, 0)));
    }

    @Test
    public void historicalNoiseMayFinishFeaturesButNeverReplaysNoiseOrCompletedStage() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        ChunkAccess chunk = mock(ChunkAccess.class);
        when(chunk.getPos()).thenReturn(new ChunkPos(-2, 3));
        when(chunk.getPersistedStatus()).thenReturn(ChunkStatus.NOISE);
        assertTrue(NativeGenerationWriteGuard.allowsPendingStage(engine, chunk, ChunkStatus.FEATURES));
        assertFalse(NativeGenerationWriteGuard.allowsPendingStage(engine, chunk, ChunkStatus.NOISE));
        when(chunk.getPersistedStatus()).thenReturn(ChunkStatus.FEATURES);
        assertFalse(NativeGenerationWriteGuard.allowsPendingStage(engine, chunk, ChunkStatus.FEATURES));
        when(chunk.getPersistedStatus()).thenReturn(ChunkStatus.FULL);
        assertFalse(NativeGenerationWriteGuard.allowsPendingStage(engine, chunk, ChunkStatus.SPAWN));
    }
}
