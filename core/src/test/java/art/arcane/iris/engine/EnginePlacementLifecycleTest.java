package art.arcane.iris.engine;

import art.arcane.iris.engine.framework.EngineEffects;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.EngineWorldManager;
import art.arcane.iris.engine.object.placement.ObjectPlaceMode;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class EnginePlacementLifecycleTest {
    @Test
    public void runtimeAssemblyResolvesBeforePublicationAndKeepsOldPlansIntact() {
        var initial = assembly(1);
        EngineRuntime previous = initial.freeze(new EngineRuntime.BiomeMaxes(0, 0, 0));
        var replacement = assembly(2);
        EngineRuntime next = replacement.freeze(new EngineRuntime.BiomeMaxes(0, 0, 0));

        assertSame(initial.placementLifecycles, previous.placementLifecycles());
        assertSame(replacement.placementLifecycles, next.placementLifecycles());
        for (ObjectPlaceMode mode : ObjectPlaceMode.values()) {
            assertNotNull(next.placementLifecycles().get(mode));
            assertNotSame(previous.placementLifecycles().get(mode), next.placementLifecycles().get(mode));
        }
        assertSame(initial.placementLifecycles, previous.placementLifecycles());
    }

    @Test
    public void complexReplacementCarriesNewPlansAlongsideTheNewComplex() {
        var initial = assembly(1);
        EngineRuntime previous = initial.freeze(new EngineRuntime.BiomeMaxes(0, 0, 0));
        var replacement = assembly(2);
        EngineRuntime next = previous.withComplex(2, replacement.complex, null,
                new EngineRuntime.BiomeMaxes(1, 1, 1), replacement.placementLifecycles);

        assertSame(replacement.placementLifecycles, next.placementLifecycles());
        assertSame(replacement.complex, next.complex());
        assertSame(previous.worldManager(), next.worldManager());
        assertSame(initial.placementLifecycles, previous.placementLifecycles());
    }

    private EngineRuntimeBuilder.RuntimeAssembly assembly(int id) {
        var assembly = new EngineRuntimeBuilder.RuntimeAssembly(id, mock(EngineTarget.class));
        assembly.complex = mock(IrisComplex.class);
        assembly.effects = mock(EngineEffects.class);
        assembly.mode = mock(EngineMode.class);
        assembly.worldManager = mock(EngineWorldManager.class);
        assembly.hash32 = CompletableFuture.completedFuture(0L);
        return assembly;
    }
}
