package art.arcane.iris.generation.biome;

import art.arcane.iris.pack.value.OverrideMode;
import art.arcane.iris.structure.object.IrisObjectPlacement;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisFloatingChildBiomesResolverTest {
    @Test
    public void resolveTopObjects_inheritOnly_withInheritTrue_returnsSurfaceObjects() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        KList<IrisObjectPlacement> surface = new KList<>();
        surface.add(placement);

        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes();
        entry.setInheritObjects(true);
        entry.setTopObjectMode(OverrideMode.INHERIT_ONLY);

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

        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes();
        entry.setInheritObjects(true);
        entry.setTopObjectMode(OverrideMode.REPLACE);
        entry.setTopObjectOverrides(overrides);

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

        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes();
        entry.setInheritObjects(true);
        entry.setTopObjectMode(OverrideMode.MERGE);
        entry.setTopObjectOverrides(overrides);

        KList<IrisObjectPlacement> result = entry.resolveTopObjectsFromSurface(surface);

        assertEquals(2, result.size());
        assertTrue(result.contains(surfacePlacement));
        assertTrue(result.contains(overridePlacement));
    }

    @Test
    public void resolveTopObjects_inheritOnly_emptySurface_returnsEmpty() {
        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes();
        entry.setTopObjectMode(OverrideMode.INHERIT_ONLY);

        KList<IrisObjectPlacement> result = entry.resolveTopObjectsFromSurface(new KList<>());

        assertTrue(result.isEmpty());
    }

    @Test
    public void resolveBottomObjects_inheritOnly_returnsEmptyList() {
        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes();
        entry.setBottomObjectMode(OverrideMode.INHERIT_ONLY);

        KList<IrisObjectPlacement> result = entry.resolveBottomObjects(null);

        assertTrue(result.isEmpty());
    }

    @Test
    public void resolveBottomObjects_replace_returnsBottomOverrides() {
        IrisObjectPlacement overridePlacement = new IrisObjectPlacement();
        KList<IrisObjectPlacement> overrides = new KList<>();
        overrides.add(overridePlacement);

        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes();
        entry.setBottomObjectMode(OverrideMode.REPLACE);
        entry.setBottomObjectOverrides(overrides);

        KList<IrisObjectPlacement> result = entry.resolveBottomObjects(null);

        assertEquals(1, result.size());
        assertEquals(overridePlacement, result.get(0));
    }

    @Test
    public void resolveTopObjects_inheritTrue_nullTargetProduces_emptySurface() {
        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes();
        entry.setInheritObjects(true);
        entry.setTopObjectMode(OverrideMode.INHERIT_ONLY);

        KList<IrisObjectPlacement> result = entry.resolveTopObjects(null);

        assertTrue(result.isEmpty());
    }
}
