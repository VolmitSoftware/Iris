package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.RequireSurfaceSupportSettings;
import art.arcane.iris.builtin.objects.SurfaceSupportBufferSettings;
import art.arcane.iris.builtin.objects.UpperObjectsSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Iris reads the settings of iris:objects as its own placements, once, and answers for a level that declares none. */
public class ObjectPlacementsTest {
    @Test
    public void aBiomeThatDeclaresNoObjectsHasNone() {
        IrisBiome biome = new IrisBiome();

        assertTrue(ObjectPlacements.all(biome).isEmpty());
    }

    @Test
    public void placementsAreReadFromTheJsonTheyWereWrittenAsAndOnlyOnce() {
        IrisData data = mock(IrisData.class);
        when(data.getGson()).thenReturn(new Gson());
        IrisBiome biome = new IrisBiome();
        biome.setLoader(data);
        biome.setModuleSettings(BuiltInPack.settings(TestSettings.with(new ObjectSettings(), "objects", List.of(
                TestSettings.of(ObjectPlacement.class, "{\"place\":[\"hut\"],\"density\":2}"),
                TestSettings.of(ObjectPlacement.class, "{\"place\":[\"cave/crystal\"],\"carvingSupport\":\"CARVING_ONLY\"}"),
                TestSettings.of(ObjectPlacement.class, "{\"place\":[\"rift\"],\"carvingSupport\":\"ANYWHERE\"}")))));

        assertEquals(3, ObjectPlacements.all(biome).size());
        assertEquals(2, ObjectPlacements.all(biome).get(0).density());
        assertEquals(List.of("hut", "rift"), ObjectPlacements.surface(biome).stream().map(p -> p.place().get(0)).toList());
        assertEquals(List.of("cave/crystal", "rift"), ObjectPlacements.carving(biome).stream().map(p -> p.place().get(0)).toList());
        assertSame(ObjectPlacements.all(biome).get(0), ObjectPlacements.all(biome).get(0));
    }

    @Test
    public void aDimensionThatDeclaresNothingKeepsIrisDefaults() {
        IrisDimension dimension = new IrisDimension();

        assertFalse(ObjectPlacements.upperObjects(dimension));
        assertFalse(ObjectPlacements.upperObjectsForced(dimension));
        assertTrue(ObjectPlacements.requiresSurfaceSupport(dimension));
        assertEquals(2, ObjectPlacements.surfaceSupportBuffer(dimension));
    }

    @Test
    public void aDimensionTellsWhatItDeclares() {
        IrisDimension dimension = new IrisDimension();
        dimension.setModuleSettings(BuiltInPack.settings(
                TestSettings.of(UpperObjectsSettings.class, "{\"enabled\":true}"),
                TestSettings.of(RequireSurfaceSupportSettings.class, "{\"require\":false}"),
                TestSettings.of(SurfaceSupportBufferSettings.class, "{\"buffer\":6}")));

        assertTrue(ObjectPlacements.upperObjects(dimension));
        assertFalse(ObjectPlacements.requiresSurfaceSupport(dimension));
        assertEquals(6, ObjectPlacements.surfaceSupportBuffer(dimension));
    }
}
