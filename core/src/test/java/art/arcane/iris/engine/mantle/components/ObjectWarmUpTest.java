package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.iris.engine.procedural.part.ProceduralObject;
import com.volmit.IrisFeature;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.noise.NoiseStyle;
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
    private static final long DENSITY_SEED = 1337L;

    @Test
    public void everyPlacementOfBiomesAndRegionsIsBuiltWithTheProceduralObjectsItPlaces() {
        PackResources resources = mock(PackResources.class, RETURNS_DEEP_STUBS);
        PackObjects objects = mock(PackObjects.class, RETURNS_MOCKS);
        when(resources.objects()).thenReturn(objects);
        when(resources.blocks().find(anyString())).thenReturn(Optional.of(BlockState.parse("minecraft:oak_log")));
        when(resources.idOf(anyString())).thenAnswer(invocation -> new ResourceId("test", invocation.getArgument(0)));
        when(resources.find(eq(ProceduralObjectCatalog.DEFINITIONS), any())).thenReturn(Optional.of(oneLog()));
        PackSettings settings = mock(PackSettings.class);
        PackWorld world = worldWithStates(settings, resources);
        ObjectPlacement biomePlacement = placement("procedural/trees/biome-oak", 2);
        ObjectPlacement regionPlacement = placement("procedural/trees/region-oak", 5);
        declare(settings, ObjectSettings.class, objects(regionPlacement), objects(biomePlacement));

        new ObjectWarmUp(world).warm();

        verify(resources.noise(), atLeastOnce()).sampler(eq(densityStyleOf(biomePlacement)), eq(DENSITY_SEED));
        verify(resources.noise(), atLeastOnce()).sampler(eq(densityStyleOf(regionPlacement)), eq(DENSITY_SEED));
        verify(objects, atLeastOnce()).build(contains("region-oak"), anyInt(), anyInt(), anyInt(), any());
        verify(objects, atLeastOnce()).build(contains("biome-oak"), anyInt(), anyInt(), anyInt(), any());
    }

    private static <F extends IrisFeature> void declare(PackSettings settings, Class<F> type, F inRegion, F inBiome) {
        when(settings.declared(type)).thenReturn(SettingsStack.of(List.of(
                new Entry<>(Level.REGION, REGION, inRegion), new Entry<>(Level.BIOME, BIOME, inBiome))));
    }

    @SuppressWarnings("unchecked")
    private static PackWorld worldWithStates(PackSettings settings, PackResources resources) {
        PackWorld world = mock(PackWorld.class);
        Map<Class<?>, Object> states = new HashMap<>();
        when(world.settings()).thenReturn(settings);
        when(world.resources()).thenReturn(resources);
        when(world.seed(IrisSeedPurposes.OBJECT_DENSITY)).thenReturn(DENSITY_SEED);
        when(world.state(any(), any())).thenAnswer(invocation -> states.computeIfAbsent(invocation.getArgument(0),
                ignored -> invocation.<Function<PackWorld, Object>>getArgument(1).apply(world)));
        return world;
    }

    private static ProceduralObject oneLog() {
        return TestSettings.of(ProceduralObject.class, "{\"variants\":1,\"materials\":[{\"block\":\"minecraft:oak_log\"}],"
                + "\"parts\":[{\"stem\":{\"length\":3}}]}");
    }

    private static ObjectPlacement placement(String objectKey, int densityZoom) {
        return TestSettings.of(ObjectPlacement.class, "{\"place\":[\"" + objectKey + "\"],"
                + "\"densityStyle\":{\"min\":1,\"max\":3,\"style\":{\"style\":\"SIMPLEX\",\"zoom\":" + densityZoom + "}}}");
    }

    private static NoiseStyle densityStyleOf(ObjectPlacement placement) {
        return placement.densityStyle().orElseThrow().style();
    }

    private static ObjectSettings objects(ObjectPlacement placement) {
        return TestSettings.with(new ObjectSettings(), "objects", List.of(placement));
    }
}
