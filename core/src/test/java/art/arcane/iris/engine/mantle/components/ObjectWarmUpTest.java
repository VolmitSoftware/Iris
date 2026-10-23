package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.IrisSeeds;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectHost.Declaration;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.Level;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Object placing builds its placements' noise and its procedural objects' variants before generation asks. */
public class ObjectWarmUpTest {
    @Test
    public void everyPlacementAndProceduralObjectOfBiomesAndRegionsIsBuilt() {
        ObjectHost host = mock(ObjectHost.class);
        IrisSeeds seeds = seed -> seed == IrisSeed.COMPONENT ? 1337L : 0L;
        PackWorld world = mock(PackWorld.class);
        when(world.extension(ObjectHost.class)).thenReturn(Optional.of(host));
        when(world.extension(IrisSeeds.class)).thenReturn(Optional.of(seeds));
        ObjectPlacement biomePlacement = placement();
        ObjectPlacement regionPlacement = placement();
        ProceduralObjectSettings biomeProceduralSettings = new ProceduralObjectSettings();
        ProceduralObjectSettings regionProceduralSettings = new ProceduralObjectSettings();
        ProceduralPlacement biomeTree = mock(ProceduralPlacement.class);
        ProceduralPlacement regionTree = mock(ProceduralPlacement.class);
        when(host.procedural(eq(biomeProceduralSettings), any())).thenReturn(List.of(biomeTree));
        when(host.procedural(eq(regionProceduralSettings), any())).thenReturn(List.of(regionTree));
        when(host.declarations(ObjectSettings.class)).thenReturn(List.of(
                new Declaration<>(Level.REGION, "region", objects(regionPlacement)),
                new Declaration<>(Level.BIOME, "biome", objects(biomePlacement))));
        when(host.declarations(ProceduralObjectSettings.class)).thenReturn(List.of(
                new Declaration<>(Level.REGION, "region", regionProceduralSettings),
                new Declaration<>(Level.BIOME, "biome", biomeProceduralSettings)));

        new ObjectWarmUp(world).warm();

        verify(host).warm(eq(biomePlacement), any(RNG.class));
        verify(host).warm(eq(regionPlacement), any(RNG.class));
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
