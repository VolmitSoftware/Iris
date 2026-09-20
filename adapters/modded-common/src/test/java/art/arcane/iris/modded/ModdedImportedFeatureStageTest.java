package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeFeatureBiomeSource;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.nativegen.IrisImportedFeatureControl;
import org.junit.Test;
import org.junit.BeforeClass;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import static org.junit.Assert.assertFalse;
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
    public void closingRuntimeDoesNotBuildFeatureTables() {
        NativeFeatureBiomeSource source = mock(NativeFeatureBiomeSource.class);
        Engine engine = mock(Engine.class);
        when(engine.isClosing()).thenReturn(true);
        ModdedImportedFeatureStage stage = new ModdedImportedFeatureStage(source);

        stage.prepare(engine);
        stage.prepareWithoutWaiting(engine);

        verify(source, never()).packGeneration();
        verify(engine, never()).getDimension();
        assertFalse(stage.active());
    }
}
