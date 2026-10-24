package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.objects.ObjectBuilder;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectHost.Declaration;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.Level;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Object placing builds its placements' noise and its procedural objects' variants before generation asks. */
public class ObjectWarmUpTest {
    private static final ResourceId REGION = new ResourceId("test", "region");
    private static final ResourceId BIOME = new ResourceId("test", "biome");

    @Test
    public void everyPlacementAndProceduralObjectOfBiomesAndRegionsIsBuilt() {
        ObjectHost host = mock(ObjectHost.class);
        when(host.builder()).thenReturn(mock(ObjectBuilder.class, RETURNS_MOCKS));
        PackWorld world = worldWithStates(host);
        ObjectPlacement biomePlacement = placement();
        ObjectPlacement regionPlacement = placement();
        when(host.declarations(ObjectSettings.class)).thenReturn(List.of(
                new Declaration<>(Level.REGION, REGION, objects(regionPlacement)),
                new Declaration<>(Level.BIOME, BIOME, objects(biomePlacement))));
        when(host.declarations(ProceduralObjectSettings.class)).thenReturn(List.of(
                new Declaration<>(Level.REGION, REGION, oneTree("region-oak")),
                new Declaration<>(Level.BIOME, BIOME, oneTree("biome-oak"))));

        new ObjectWarmUp(world).warm();

        verify(host).warm(eq(biomePlacement), any(RNG.class));
        verify(host).warm(eq(regionPlacement), any(RNG.class));
        verify(host, times(2)).builder();
    }

    @SuppressWarnings("unchecked")
    private static PackWorld worldWithStates(ObjectHost host) {
        PackWorld world = mock(PackWorld.class);
        Map<Class<?>, Object> states = new HashMap<>();
        when(world.extension(ObjectHost.class)).thenReturn(Optional.of(host));
        when(world.seed(IrisSeedPurposes.COMPONENT)).thenReturn(1337L);
        when(world.state(any(), any())).thenAnswer(invocation -> states.computeIfAbsent(invocation.getArgument(0),
                ignored -> invocation.<Function<PackWorld, Object>>getArgument(1).apply(world)));
        return world;
    }

    private static ProceduralObjectSettings oneTree(String name) {
        return TestSettings.of(ProceduralObjectSettings.class, "{\"trees\":[{\"name\":\"" + name + "\"}]}");
    }

    private static ObjectPlacement placement() {
        return TestSettings.of(ObjectPlacement.class, "{\"place\":[\"a\"]}");
    }

    private static ObjectSettings objects(ObjectPlacement placement) {
        return TestSettings.with(new ObjectSettings(), "objects", List.of(placement));
    }
}
