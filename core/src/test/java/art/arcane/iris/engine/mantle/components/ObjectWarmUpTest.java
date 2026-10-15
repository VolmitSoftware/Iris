package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectHost.Declaration;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.FeatureAttachment.Target;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Object placing builds its placements' noise and its procedural objects' variants before generation asks. */
public class ObjectWarmUpTest {
    @Test
    public void everyPlacementAndProceduralObjectOfBiomesAndRegionsIsBuilt() {
        ObjectHost host = mock(ObjectHost.class);
        IrisSeed.Source seeds = mock(IrisSeed.Source.class);
        PackWorld world = mock(PackWorld.class, withSettings().extraInterfaces(ObjectHost.Source.class, IrisSeed.Source.class));
        when(((ObjectHost.Source) world).objects()).thenReturn(host);
        when(((IrisSeed.Source) world).seed(IrisSeed.COMPONENT)).thenReturn(1337L);
        ObjectPlacement biomePlacement = placement();
        ObjectPlacement regionPlacement = placement();
        ProceduralObjectSettings biomeProceduralSettings = new ProceduralObjectSettings();
        ProceduralObjectSettings regionProceduralSettings = new ProceduralObjectSettings();
        ProceduralPlacement biomeTree = mock(ProceduralPlacement.class);
        ProceduralPlacement regionTree = mock(ProceduralPlacement.class);
        when(host.procedural(biomeProceduralSettings)).thenReturn(List.of(biomeTree));
        when(host.procedural(regionProceduralSettings)).thenReturn(List.of(regionTree));
        when(host.declarations(ObjectSettings.class)).thenReturn(List.of(
                new Declaration<>(Target.REGION, "region", objects(regionPlacement)),
                new Declaration<>(Target.BIOME, "biome", objects(biomePlacement))));
        when(host.declarations(ProceduralObjectSettings.class)).thenReturn(List.of(
                new Declaration<>(Target.REGION, "region", regionProceduralSettings),
                new Declaration<>(Target.BIOME, "biome", biomeProceduralSettings)));

        new ObjectWarmUp(world).warm();

        verify(host).warm(org.mockito.ArgumentMatchers.eq(biomePlacement), any(RNG.class));
        verify(host).warm(org.mockito.ArgumentMatchers.eq(regionPlacement), any(RNG.class));
        verify(biomeTree).variant(any(RNG.class));
        verify(regionTree).variant(any(RNG.class));
    }

    private static ObjectPlacement placement() {
        return TestSettings.of(ObjectPlacement.class, "{\"place\":[\"a\"]}");
    }

    private static ObjectSettings objects(ObjectPlacement placement) {
        return TestSettings.with(new ObjectSettings(), "objects", List.of(placement));
    }
}
