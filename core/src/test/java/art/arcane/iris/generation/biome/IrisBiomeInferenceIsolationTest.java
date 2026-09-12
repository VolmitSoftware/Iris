package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.terrain.InferredType;

import art.arcane.iris.pack.loading.IrisData;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisBiomeInferenceIsolationTest {
    @Test
    public void conflictingRolesUseStableCachedVariants() {
        Gson gson = new Gson();
        IrisData data = mock(IrisData.class);
        when(data.getGson()).thenReturn(gson);
        IrisBiome biome = gson.fromJson("{\"name\":\"Shared\"}", IrisBiome.class);
        biome.setLoader(data);
        biome.setLoadKey("shared");

        IrisBiome land = biome.withInferredType(InferredType.LAND);
        IrisBiome sea = biome.withInferredType(InferredType.SEA);

        assertSame(biome, land);
        assertNotSame(land, sea);
        assertSame(sea, biome.withInferredType(InferredType.SEA));
        assertEquals(InferredType.LAND, land.getInferredType());
        assertEquals(InferredType.SEA, sea.getInferredType());
    }
}
