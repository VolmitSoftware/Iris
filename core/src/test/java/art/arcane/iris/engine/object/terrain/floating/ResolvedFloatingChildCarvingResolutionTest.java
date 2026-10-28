package art.arcane.iris.engine.object.terrain.floating;

import art.arcane.iris.modules.carve.CarvingEntry;
import art.arcane.iris.modules.floating.FloatingChildBiome;
import art.arcane.iris.engine.object.TestSettings;
import com.google.gson.Gson;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackBiome;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResolvedFloatingChildCarvingResolutionTest {
    private static final String NAMESPACE = "ns";

    @Test
    public void carvingBiome_loadsBiomeKeyWhenEntryIdMissing() {
        ResourceId biome = ResourceId.parse("ns:carving/mushroom");
        FloatingWorld world = world(List.of(), Map.of("carving/mushroom", biome));

        assertEquals(Optional.of(biome), child("carving/mushroom", world).carvingBiome());
    }

    @Test
    public void carvingBiome_prefersDimensionCarvingEntryId() {
        ResourceId entryBiome = ResourceId.parse("ns:carving/standard-deepdark");
        ResourceId sameKeyBiome = ResourceId.parse("ns:global-deepdark-band");
        CarvingEntry entry = new Gson().fromJson("{\"id\": \"global-deepdark-band\", \"biome\": \"carving/standard-deepdark\"}", CarvingEntry.class);
        FloatingWorld world = world(List.of(entry), Map.of("carving/standard-deepdark", entryBiome, "global-deepdark-band", sameKeyBiome));

        assertEquals(Optional.of(entryBiome), child("global-deepdark-band", world).carvingBiome());
    }

    private static ResolvedFloatingChild child(String carving, FloatingWorld world) {
        return new ResolvedFloatingChild(TestSettings.with(new FloatingChildBiome(), "carving", carving), world);
    }

    private static FloatingWorld world(List<CarvingEntry> dimensionCarving, Map<String, ResourceId> biomes) {
        PackResources resources = mock(PackResources.class);
        when(resources.idOf(anyString())).thenAnswer(invocation -> ResourceId.ofPackKey(NAMESPACE, invocation.getArgument(0, String.class)));
        when(resources.find(eq(PackResources.BIOMES), any())).thenAnswer(invocation -> {
            ResourceId id = invocation.getArgument(1);
            return biomes.containsValue(id) ? Optional.of(new PackBiome(id, id.path(), id, 1, List.of())) : Optional.empty();
        });
        return new FloatingWorld(resources, null, 0L, 0L, 0, dimensionCarving);
    }
}
