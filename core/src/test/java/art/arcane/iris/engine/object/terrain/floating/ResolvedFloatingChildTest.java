package art.arcane.iris.engine.object.terrain.floating;

import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.builtin.floating.OverrideMode;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResolvedFloatingChildTest {
    @Test
    public void resolveTopObjects_inheritOnly_withInheritTrue_returnsSurfaceObjects() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        KList<IrisObjectPlacement> surface = new KList<>();
        surface.add(placement);

        ResolvedFloatingChild entry = new ResolvedFloatingChild(TestSettings.with(new FloatingChildBiome(), "inheritObjects", true, "topObjectMode", OverrideMode.INHERIT_ONLY));

        KList<IrisObjectPlacement> result = entry.resolveTopObjectsFromSurface(surface);

        assertEquals(surface, result);
    }

    @Test
    public void resolveTopObjects_replace_returnsTopOverridesIgnoringSurface() {
        IrisObjectPlacement surfacePlacement = new IrisObjectPlacement();
        IrisObjectPlacement overridePlacement = new IrisObjectPlacement();
        KList<IrisObjectPlacement> surface = new KList<>();
        surface.add(surfacePlacement);
        KList<IrisObjectPlacement> overrides = new KList<>();
        overrides.add(overridePlacement);

        ResolvedFloatingChild entry = new ResolvedFloatingChild(TestSettings.with(new FloatingChildBiome(), "inheritObjects", true, "topObjectMode", OverrideMode.REPLACE, "topObjectOverrides", overrides));

        KList<IrisObjectPlacement> result = entry.resolveTopObjectsFromSurface(surface);

        assertEquals(1, result.size());
        assertEquals(overridePlacement, result.get(0));
    }

    @Test
    public void resolveTopObjects_merge_returnsCombinedSurfacePlusOverrides() {
        IrisObjectPlacement surfacePlacement = new IrisObjectPlacement();
        IrisObjectPlacement overridePlacement = new IrisObjectPlacement();
        KList<IrisObjectPlacement> surface = new KList<>();
        surface.add(surfacePlacement);
        KList<IrisObjectPlacement> overrides = new KList<>();
        overrides.add(overridePlacement);

        ResolvedFloatingChild entry = new ResolvedFloatingChild(TestSettings.with(new FloatingChildBiome(), "inheritObjects", true, "topObjectMode", OverrideMode.MERGE, "topObjectOverrides", overrides));

        KList<IrisObjectPlacement> result = entry.resolveTopObjectsFromSurface(surface);

        assertEquals(2, result.size());
        assertTrue(result.contains(surfacePlacement));
        assertTrue(result.contains(overridePlacement));
    }

    @Test
    public void resolveTopObjects_inheritOnly_emptySurface_returnsEmpty() {
        ResolvedFloatingChild entry = new ResolvedFloatingChild(TestSettings.with(new FloatingChildBiome(), "topObjectMode", OverrideMode.INHERIT_ONLY));

        KList<IrisObjectPlacement> result = entry.resolveTopObjectsFromSurface(new KList<>());

        assertTrue(result.isEmpty());
    }

    @Test
    public void resolveBottomObjects_inheritOnly_returnsEmptyList() {
        ResolvedFloatingChild entry = new ResolvedFloatingChild(TestSettings.with(new FloatingChildBiome(), "bottomObjectMode", OverrideMode.INHERIT_ONLY));

        KList<IrisObjectPlacement> result = entry.resolveBottomObjects(null);

        assertTrue(result.isEmpty());
    }

    @Test
    public void resolveBottomObjects_replace_returnsBottomOverrides() {
        IrisObjectPlacement overridePlacement = new IrisObjectPlacement();
        KList<IrisObjectPlacement> overrides = new KList<>();
        overrides.add(overridePlacement);

        ResolvedFloatingChild entry = new ResolvedFloatingChild(TestSettings.with(new FloatingChildBiome(), "bottomObjectMode", OverrideMode.REPLACE, "bottomObjectOverrides", overrides));

        KList<IrisObjectPlacement> result = entry.resolveBottomObjects(null);

        assertEquals(1, result.size());
        assertEquals(overridePlacement, result.get(0));
    }

    @Test
    public void resolveTopObjects_inheritTrue_nullTargetProduces_emptySurface() {
        ResolvedFloatingChild entry = new ResolvedFloatingChild(TestSettings.with(new FloatingChildBiome(), "inheritObjects", true, "topObjectMode", OverrideMode.INHERIT_ONLY));

        KList<IrisObjectPlacement> result = entry.resolveTopObjects(null);

        assertTrue(result.isEmpty());
    }
}
