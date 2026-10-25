package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.runtime.EngineEffects;
import art.arcane.iris.generation.runtime.EngineMode;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.EngineWorldManager;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class EnginePlacementLifecycleTest {
    @Test
    public void runtimeAssemblyResolvesBeforePublicationAndKeepsOldPlansIntact() {
        var initial = assembly(1);
        EngineRuntime previous = initial.freezeRuntime(new GenerationRuntime.BiomeMaxes(0, 0, 0));
        var replacement = assembly(2);
        EngineRuntime next = replacement.freezeRuntime(new GenerationRuntime.BiomeMaxes(0, 0, 0));

        assertSame(initial.placementLifecycles, previous.generation().placementLifecycles());
        assertSame(replacement.placementLifecycles, next.generation().placementLifecycles());
        for (ObjectPlaceMode mode : ObjectPlaceMode.values()) {
            assertNotNull(next.generation().placementLifecycles().get(mode));
            assertNotSame(previous.generation().placementLifecycles().get(mode), next.generation().placementLifecycles().get(mode));
        }
        assertSame(initial.placementLifecycles, previous.generation().placementLifecycles());
    }

    @Test
    public void complexReplacementCarriesNewPlansAlongsideTheNewComplex() {
        var initial = assembly(1);
        EngineRuntime previous = initial.freezeRuntime(new GenerationRuntime.BiomeMaxes(0, 0, 0));
        var replacement = assembly(2);
        EngineRuntime next = previous.withGeneration(previous.generation().withComplex(2, replacement.complex, null, null,
                new GenerationRuntime.BiomeMaxes(1, 1, 1), replacement.placementLifecycles));

        assertSame(replacement.placementLifecycles, next.generation().placementLifecycles());
        assertSame(replacement.complex, next.generation().complex());
        assertSame(previous.worldManager(), next.worldManager());
        assertSame(initial.placementLifecycles, previous.generation().placementLifecycles());
    }

    private EngineRuntimeBuilder.RuntimeAssembly assembly(int id) {
        EngineTarget target = mock(EngineTarget.class);
        var world = mock(art.arcane.iris.world.IrisWorld.class);
        org.mockito.Mockito.when(target.getWorld()).thenReturn(world);
        org.mockito.Mockito.when(target.getData()).thenReturn(mock(art.arcane.iris.pack.loading.IrisData.class));
        org.mockito.Mockito.when(target.getDimension()).thenReturn(mock(art.arcane.iris.generation.terrain.IrisDimension.class));
        var assembly = new EngineRuntimeBuilder.RuntimeAssembly(id, target,
                java.nio.file.Path.of("build", "test-mantles", "placement-" + id));
        assembly.mantle = mock(art.arcane.iris.generation.mantle.EngineMantle.class);
        assembly.complex = mock(IrisComplex.class);
        assembly.effects = mock(EngineEffects.class);
        assembly.mode = mock(EngineMode.class);
        assembly.worldManager = mock(EngineWorldManager.class);
        assembly.hash32 = CompletableFuture.completedFuture(0L);
        return assembly;
    }
}
