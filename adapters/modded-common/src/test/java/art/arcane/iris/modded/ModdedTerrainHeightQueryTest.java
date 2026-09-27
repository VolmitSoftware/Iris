package art.arcane.iris.modded;

import art.arcane.iris.generation.runtime.Engine;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public final class ModdedTerrainHeightQueryTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void nativeQueriesUseLiveHeightsWithoutGeneratingTerrain() {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class, CALLS_REAL_METHODS);
        Engine engine = mock(Engine.class);
        assertNull(generator.resolvedColumn(engine, -1, -33));
        verifyNoInteractions(engine);

        when(engine.getHeight(-1, -33, true)).thenReturn(12, 18);
        when(engine.getHeight(-1, -33, false)).thenReturn(24);
        assertEquals(12, generator.terrainHeight(engine, -1, -33, true, true));
        assertEquals(18, generator.terrainHeight(engine, -1, -33, true, true));
        assertEquals(24, generator.terrainHeight(engine, -1, -33, false, true));
        verify(engine, never()).getComplex();
        verify(engine, never()).getMode();
    }
}
