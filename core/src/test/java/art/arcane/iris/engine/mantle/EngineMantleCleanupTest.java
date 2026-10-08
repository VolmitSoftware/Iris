package art.arcane.iris.engine.mantle;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.modules.GenerationLayers;
import art.arcane.iris.core.modules.ModuleLayerValues;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineMantleCleanupTest {
    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformBlockState block = mock(PlatformBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenReturn(block);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
        MantleSliceRetention.clearForTesting();
    }

    @Test
    public void retainedSlicesSurviveCoveredCleanup() {
        MantleSliceRetention.retain(MatterCavern.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();
        doReturn(true).when(fixture.engineMantle()).isCovered(7, -4);

        fixture.engineMantle().cleanupChunk(7, -4);

        verify(fixture.chunk(), never()).deleteSlices(MatterCavern.class);
        verify(fixture.chunk()).deleteSlices(PlatformBlockState.class);
    }

    @Test
    public void retainedSlicesSurvivePregenForceCleanup() {
        MantleSliceRetention.retain(TreeBlockMaterial.class.getCanonicalName());
        MantleSliceRetention.retain(String.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();

        fixture.engineMantle().forceCleanupChunk(7, -4);

        verify(fixture.chunk(), never()).deleteSlices(TreeBlockMaterial.class);
        verify(fixture.chunk(), never()).deleteSlices(String.class);
        verify(fixture.chunk()).deleteSlices(MatterCavern.class);
        verify(fixture.chunk()).deleteSlices(PlatformBlockState.class);
    }

    @Test
    public void bukkitBootRetentionsSurvivePregen() {
        // Exactly the slice types the Bukkit plugin registers at boot: markers and tree materials
        // must keep working in pregenerated chunks.
        MantleSliceRetention.retain(String.class.getCanonicalName());
        MantleSliceRetention.retain(TreeBlockMaterial.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();

        fixture.engineMantle().forceCleanupChunk(7, -4);

        verify(fixture.chunk(), never()).deleteSlices(String.class);
        verify(fixture.chunk(), never()).deleteSlices(TreeBlockMaterial.class);
    }

    @Test
    public void blockStateSliceIsNeverRetainable() {
        MantleSliceRetention.retain(PlatformBlockState.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();

        fixture.engineMantle().forceCleanupChunk(7, -4);

        verify(fixture.chunk()).deleteSlices(PlatformBlockState.class);
    }

    @Test
    public void coveredCleanupRetainsDeferredMaterializationSlices() {
        CleanupFixture fixture = cleanupFixture();
        doReturn(true).when(fixture.engineMantle()).isCovered(7, -4);

        fixture.engineMantle().cleanupChunk(7, -4);

        verifyCleanup(fixture.chunk());
    }

    @Test
    public void forcedCleanupRetainsDeferredMaterializationSlices() {
        CleanupFixture fixture = cleanupFixture();

        fixture.engineMantle().forceCleanupChunk(7, -4);

        verifyCleanup(fixture.chunk());
    }

    @Test
    public void bothCleanupsRemoveThePacksGenerationLayers() {
        CleanupFixture fixture = cleanupFixture();
        GenerationLayers layers = mock(GenerationLayers.class);
        doReturn(layers).when(fixture.engineMantle()).getGenerationLayers();
        doReturn(true).when(fixture.engineMantle()).isCovered(7, -4);

        fixture.engineMantle().cleanupChunk(7, -4);
        fixture.engineMantle().forceCleanupChunk(7, -4);

        verify(layers, times(2)).removeFrom(fixture.chunk());
    }

    @Test
    public void retainedModuleLayersSurviveCleanup() {
        MantleSliceRetention.retain(ModuleLayerValues.class.getCanonicalName());
        CleanupFixture fixture = cleanupFixture();
        GenerationLayers layers = mock(GenerationLayers.class);
        doReturn(layers).when(fixture.engineMantle()).getGenerationLayers();

        fixture.engineMantle().forceCleanupChunk(7, -4);

        verify(layers, never()).removeFrom(any());
    }

    @SuppressWarnings("unchecked")
    private CleanupFixture cleanupFixture() {
        EngineMantle engineMantle = mock(EngineMantle.class, CALLS_REAL_METHODS);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(mantle.getChunk(7, -4)).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        doAnswer(invocation -> {
            Runnable cleanup = invocation.getArgument(1);
            cleanup.run();
            return null;
        }).when(chunk).raiseFlagUnchecked(eq(MantleFlag.CLEANED), any());
        return new CleanupFixture(engineMantle, chunk);
    }

    private void verifyCleanup(MantleChunk<Matter> chunk) {
        verify(chunk).deleteSlices(PlatformBlockState.class);
        verify(chunk).deleteSlices(MatterCavern.class);
        verify(chunk, never()).deleteSlices(TileWrapper.class);
        verify(chunk, never()).deleteSlices(Identifier.class);
        InOrder order = inOrder(chunk);
        order.verify(chunk).raiseFlagUnchecked(eq(MantleFlag.CLEANED), any());
        order.verify(chunk).release();
    }

    private record CleanupFixture(EngineMantle engineMantle, MantleChunk<Matter> chunk) {
    }
}
