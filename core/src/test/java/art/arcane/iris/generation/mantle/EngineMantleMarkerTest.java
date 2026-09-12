package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterMarker;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class EngineMantleMarkerTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    @SuppressWarnings("unchecked")
    public void markerCoordinatesIncludeWorldMinimumHeight() {
        EngineMantle engineMantle = mock(EngineMantle.class, CALLS_REAL_METHODS);
        Engine engine = mock(Engine.class);
        EnginePlatformHooks platformHooks = mock(EnginePlatformHooks.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MatterMarker marker = new MatterMarker("test");
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(engine.getPlatformHooks()).thenReturn(platformHooks);
        when(engine.getMinHeight()).thenReturn(-64);
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterMarker> consumer = invocation.getArgument(3);
            consumer.accept(2, 7, 3, marker);
            return null;
        }).when(mantle).iterateChunk(eq(4), eq(-2), eq(MatterMarker.class), any());

        KList<IrisPosition> positions = engineMantle.findMarkers(4, -2, marker);

        assertEquals(1, positions.size());
        assertEquals(66, positions.get(0).getX());
        assertEquals(-57, positions.get(0).getY());
        assertEquals(-29, positions.get(0).getZ());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void platformHookCanSkipUnsafeMarkerRead() {
        EngineMantle engineMantle = mock(EngineMantle.class, CALLS_REAL_METHODS);
        Engine engine = mock(Engine.class);
        EnginePlatformHooks platformHooks = mock(EnginePlatformHooks.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MatterMarker marker = new MatterMarker("test");
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(engine.getPlatformHooks()).thenReturn(platformHooks);
        when(platformHooks.shouldSkipMantleMarkerRead(engine, 4, -2)).thenReturn(true);

        KList<IrisPosition> positions = engineMantle.findMarkers(4, -2, marker);

        assertTrue(positions.isEmpty());
        verifyNoInteractions(mantle);
    }
}
