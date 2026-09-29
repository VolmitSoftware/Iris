package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeFeatureBiomeSource;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationClosedException;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.nativegen.IrisImportedFeatureControl;
import org.junit.Test;
import org.junit.BeforeClass;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedImportedFeatureStageTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void disabledFeaturesRecheckAfterPackRepointAndRuntimeEviction() {
        NativeFeatureBiomeSource source = mock(NativeFeatureBiomeSource.class);
        when(source.packGeneration()).thenReturn(1L);
        IrisImportedFeatureControl control = spy(new IrisImportedFeatureControl());
        IrisDimension dimension = new IrisDimension().setImportedFeatures(control);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getCacheID()).thenReturn(7);
        ModdedImportedFeatureStage stage = new ModdedImportedFeatureStage(source);

        stage.prepare(engine);
        stage.prepare(engine);
        verify(control, times(1)).shouldGenerateFeatures();

        when(source.packGeneration()).thenReturn(2L);
        stage.prepare(engine);
        verify(control, times(2)).shouldGenerateFeatures();

        stage.evictRuntime(7);
        stage.prepare(engine);
        verify(control, times(3)).shouldGenerateFeatures();

        stage.invalidate();
        stage.prepare(engine);
        verify(control, times(4)).shouldGenerateFeatures();
        verify(source, never()).orderedPossibleBiomes();
        assertFalse(stage.active());
    }

    @Test
    public void aSealedRuntimeWithoutASettledTableRefusesDecorationInsteadOfSkippingFeatures() {
        NativeFeatureBiomeSource source = mock(NativeFeatureBiomeSource.class);
        when(source.packGeneration()).thenReturn(1L);
        Engine engine = mock(Engine.class);
        when(engine.getCacheID()).thenReturn(7);
        when(engine.isClosing()).thenReturn(true);
        ModdedImportedFeatureStage stage = new ModdedImportedFeatureStage(source);

        stage.prepareWithoutWaiting(engine);
        assertThrows(GenerationClosedException.class, () -> stage.prepare(engine));

        verify(engine, never()).getDimension();
        assertFalse(stage.active());
    }

    @Test
    public void aSealedRuntimeKeepsDecoratingWithTheTableItAlreadySettled() {
        NativeFeatureBiomeSource source = mock(NativeFeatureBiomeSource.class);
        when(source.packGeneration()).thenReturn(1L);
        Engine engine = disabledFeatures(7);
        ModdedImportedFeatureStage stage = new ModdedImportedFeatureStage(source);
        stage.bind(mock(IrisModdedChunkGenerator.class));
        stage.prepare(engine);

        when(engine.isClosing()).thenReturn(true);
        stage.prepare(engine);

        assertNull(stage.placementTable(engine));
    }

    @Test
    public void aTableRetiredBetweenPrepareAndPlacementFailsTheChunk() {
        NativeFeatureBiomeSource source = mock(NativeFeatureBiomeSource.class);
        when(source.packGeneration()).thenReturn(1L);
        Engine engine = disabledFeatures(7);
        ModdedImportedFeatureStage stage = new ModdedImportedFeatureStage(source);
        stage.bind(mock(IrisModdedChunkGenerator.class));

        stage.prepare(engine);
        stage.evictRuntime(7);
        assertThrows(GenerationClosedException.class, () -> stage.placementTable(engine));

        stage.prepare(engine);
        when(source.packGeneration()).thenReturn(2L);
        assertThrows(GenerationClosedException.class, () -> stage.placementTable(engine));
    }

    @Test
    public void anUnboundGeneratorAnswersVanillaGenerationSettings() {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class);
        when(generator.boundEngineOrNull()).thenReturn(null);
        ModdedImportedFeatureStage stage = new ModdedImportedFeatureStage(mock(NativeFeatureBiomeSource.class));
        stage.bind(generator);

        assertNull(stage.currentTable());
    }

    @Test
    public void aFailedBindFailsTheGenerationSettingsInsteadOfDroppingImportedFeatures() {
        IllegalStateException refused = new IllegalStateException("Iris generator 'overworld:overworld' failed to bind");
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class);
        when(generator.boundEngineOrNull()).thenThrow(refused);
        ModdedImportedFeatureStage stage = new ModdedImportedFeatureStage(mock(NativeFeatureBiomeSource.class));
        stage.bind(generator);

        assertSame(refused, assertThrows(IllegalStateException.class, stage::currentTable));
    }

    private static Engine disabledFeatures(int runtimeIdentity) {
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(new IrisDimension().setImportedFeatures(new IrisImportedFeatureControl()));
        when(engine.getCacheID()).thenReturn(runtimeIdentity);
        return engine;
    }
}
