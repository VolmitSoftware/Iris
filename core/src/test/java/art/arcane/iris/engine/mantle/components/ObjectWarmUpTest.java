package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.LegacyStreams;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.modules.objects.ProceduralObjectSettings;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisFeature;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.object.PackObjects;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Object placing builds its placements' noise and its procedural objects' variants before generation asks. */
public class ObjectWarmUpTest {
    private static final ResourceId REGION = new ResourceId("test", "region/region");
    private static final ResourceId BIOME = new ResourceId("test", "biome/biome");

    @Test
    public void everyPlacementAndProceduralObjectOfBiomesAndRegionsIsBuilt() {
        LegacyStreams streams = mock(LegacyStreams.class);
        PackResources resources = mock(PackResources.class, RETURNS_DEEP_STUBS);
        PackObjects objects = mock(PackObjects.class, RETURNS_MOCKS);
        when(resources.objects()).thenReturn(objects);
        when(resources.blocks().find(anyString())).thenReturn(Optional.of(BlockState.parse("minecraft:oak_log")));
        PackSettings settings = mock(PackSettings.class);
        PackWorld world = worldWithStates(settings, resources, streams);
        ObjectPlacement biomePlacement = placement();
        ObjectPlacement regionPlacement = placement();
        declare(settings, ObjectSettings.class, objects(regionPlacement), objects(biomePlacement));
        declare(settings, ProceduralObjectSettings.class, oneTree("region-oak"), oneTree("biome-oak"));

        new ObjectWarmUp(world).warm();

        verify(streams).density(eq(biomePlacement), any(RNG.class), anyDouble(), anyDouble());
        verify(streams).density(eq(regionPlacement), any(RNG.class), anyDouble(), anyDouble());
        verify(objects, atLeastOnce()).build(contains("region-oak"), anyInt(), anyInt(), anyInt(), any());
        verify(objects, atLeastOnce()).build(contains("biome-oak"), anyInt(), anyInt(), anyInt(), any());
    }

    private static <F extends IrisFeature> void declare(PackSettings settings, Class<F> type, F inRegion, F inBiome) {
        when(settings.declared(type)).thenReturn(SettingsStack.of(List.of(
                new Entry<>(Level.REGION, REGION, inRegion), new Entry<>(Level.BIOME, BIOME, inBiome))));
    }

    @SuppressWarnings("unchecked")
    private static PackWorld worldWithStates(PackSettings settings, PackResources resources, LegacyStreams streams) {
        PackWorld world = mock(PackWorld.class);
        Map<Class<?>, Object> states = new HashMap<>();
        when(world.settings()).thenReturn(settings);
        when(world.resources()).thenReturn(resources);
        when(world.extension(LegacyStreams.class)).thenReturn(Optional.of(streams));
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
