package art.arcane.iris.engine;

import art.arcane.iris.engine.EngineRuntimeBuilder.RuntimeAssembly;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class EngineShutdownDrainTest {
    private static IrisPlatform previousPlatform;

    @BeforeClass
    public static void bindPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.block(anyString())).thenReturn(mock(PlatformBlockState.class));
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void failedPlannerDrainRetainsRuntimeStorage() {
        GenerationRuntime runtime = mock(GenerationRuntime.class);
        IrisComplex complex = mock(IrisComplex.class);
        EngineMantle mantle = mock(EngineMantle.class);
        CompletableFuture<Long> hash = new CompletableFuture<>();
        when(runtime.mode()).thenReturn(mock(EngineMode.class));
        when(runtime.complex()).thenReturn(complex);
        when(runtime.mantle()).thenReturn(mantle);
        when(runtime.hash32()).thenReturn(hash);
        IllegalStateException failure = new IllegalStateException("Planner still active");
        doThrow(failure).when(complex).close();

        assertSame(failure, new EngineShutdownSequence(null).closeGenerationRuntime(runtime, null));
        verifyNoInteractions(mantle);
        assertFalse(hash.isCancelled());
    }

    @Test
    public void successfulPlannerDrainPrecedesMantleRelease() {
        GenerationRuntime runtime = mock(GenerationRuntime.class);
        IrisComplex complex = mock(IrisComplex.class);
        EngineMantle mantle = mock(EngineMantle.class);
        CompletableFuture<Long> hash = new CompletableFuture<>();
        when(runtime.mode()).thenReturn(mock(EngineMode.class));
        when(runtime.complex()).thenReturn(complex);
        when(runtime.mantle()).thenReturn(mantle);
        when(runtime.hash32()).thenReturn(hash);

        assertNull(new EngineShutdownSequence(null).closeGenerationRuntime(runtime, null));
        InOrder releases = inOrder(complex, mantle);
        releases.verify(complex).close();
        releases.verify(mantle).saveAllNow();
        releases.verify(mantle).close();
        assertTrue(hash.isCancelled());
    }

    @Test
    public void failedAssemblyDrainRetainsMantleOwnership() {
        RuntimeAssembly assembly = mock(RuntimeAssembly.class);
        assembly.complex = mock(IrisComplex.class);
        assembly.mantle = mock(EngineMantle.class);
        assembly.ownsMantle = true;
        assembly.hash32 = new CompletableFuture<>();
        IllegalStateException failure = new IllegalStateException("Planner still active");
        doThrow(failure).when(assembly.complex).close();

        assertSame(failure, new EngineShutdownSequence(null).closeAssembly(assembly, null));
        verifyNoInteractions(assembly.mantle);
        assertTrue(assembly.ownsMantle);
        assertFalse(assembly.hash32.isCancelled());
    }
}
