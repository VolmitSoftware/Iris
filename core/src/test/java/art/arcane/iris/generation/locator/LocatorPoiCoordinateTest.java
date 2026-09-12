package art.arcane.iris.generation.locator;

import art.arcane.iris.world.history.ChunkGenerationSemantics;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LocatorPoiCoordinateTest {
    @Test
    public void poiLocatorQueriesCandidateChunkCoordinates() {
        Engine engine = mock(Engine.class);
        ChunkGenerationSemantics.BlockPosition position = new ChunkGenerationSemantics.BlockPosition(50, 64, -30);
        when(engine.getPOIsAt(3, -2)).thenReturn(Set.of(new ChunkGenerationSemantics.PointOfInterest("buried_treasure", position)));

        boolean matched = Locator.poi("buried_treasure").matches(engine, new Position2(3, -2));

        assertTrue(matched);
        verify(engine).getPOIsAt(3, -2);
    }
}
