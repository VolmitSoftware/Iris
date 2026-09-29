package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisEngineMaintenanceRefusalTest {
    @Test
    @SuppressWarnings("unchecked")
    public void maintenanceThatCannotRunMantleStagesRefusesTheChunkInsteadOfGeneratingIt() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(hooks.shouldRefuseGeneration(engine)).thenReturn(true);
        Field platformHooks = IrisEngine.class.getDeclaredField("platformHooks");
        platformHooks.setAccessible(true);
        platformHooks.set(engine, hooks);
        IrisWorld world = mock(IrisWorld.class);
        when(world.name()).thenReturn("world");
        when(engine.getWorld()).thenReturn(world);
        doCallRealMethod().when(engine).generate(anyInt(), anyInt(), any(), any(), anyBoolean());
        Hunk<NativeBlockState> blocks = mock(Hunk.class);
        Hunk<NativeBiome> biomes = mock(Hunk.class);

        GenerationSessionException refused = assertThrows(GenerationSessionException.class,
                () -> engine.generate(32, 48, blocks, biomes, false));

        assertTrue(refused.isExpectedTeardown());
        verify(engine, never()).acquireGenerationLease(anyString());
        verify(engine, never()).getMode();
        verifyNoInteractions(blocks, biomes);
    }
}
