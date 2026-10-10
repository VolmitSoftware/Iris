package art.arcane.iris.engine.object.world.biome;

import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.object.TestSettings;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
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

    @Test
    public void aVariantKeepsTheModuleSettingsOfItsBiome() {
        Gson gson = new Gson();
        IrisData data = mock(IrisData.class);
        when(data.getGson()).thenReturn(gson);
        IrisBiome biome = gson.fromJson("{\"name\":\"Shared\"}", IrisBiome.class);
        biome.setLoader(data);
        biome.setLoadKey("shared");
        biome.setModuleSettings(BuiltInPack.settings(TestSettings.of(LayersSettings.class, "{\"layers\": []}")));

        biome.withInferredType(InferredType.LAND);
        IrisBiome sea = biome.withInferredType(InferredType.SEA);

        assertSame(biome.getModuleSettings(), sea.getModuleSettings());
        assertTrue(sea.getModuleSettings().find(LayersSettings.class).orElseThrow().layers().isEmpty());
    }
}
