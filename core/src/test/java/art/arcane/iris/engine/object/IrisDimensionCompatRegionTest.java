package art.arcane.iris.engine.object;

import art.arcane.iris.core.compat.CompatAction;
import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.CompatStatus;
import art.arcane.iris.core.compat.ContentGate;
import art.arcane.iris.core.compat.PackCompatReport;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.Map;

import static art.arcane.iris.engine.object.PackCompatFixtures.MISSING_BLOCK;
import static art.arcane.iris.engine.object.PackCompatFixtures.excludeBlock;
import static art.arcane.iris.engine.object.PackCompatFixtures.find;
import static art.arcane.iris.engine.object.PackCompatFixtures.region;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisDimensionCompatRegionTest {
    @SuppressWarnings("unchecked")
    private static IrisData dataWith(PackCompatReport report, IrisRegion... regions) {
        IrisData data = PackCompatFixtures.data(report);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        for (IrisRegion region : regions) {
            when(regionLoader.load(region.getLoadKey())).thenReturn(region);
        }
        return data;
    }

    private static ContentGate gate(PackCompatReport report) {
        return new ContentGate(null, Map.of(), report);
    }

    @Test
    public void allRegionsDropsExcludedRegionAndRecordsFinding() {
        PackCompatReport report = new PackCompatReport();
        IrisRegion plains = region("overworld");
        IrisRegion sulfur = excludeBlock(region("sulfur-lands"));
        IrisData data = dataWith(report, plains, sulfur);
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("overworld", "sulfur-lands"));
        dimension.setLoadKey("overworld");
        dimension.setLoader(data);

        KList<IrisRegion> pool = dimension.getAllRegions(() -> data);

        assertEquals(1, pool.size());
        assertEquals("overworld", pool.get(0).getLoadKey());
        CompatFinding dropped = find(report, CompatAction.DROPPED, "dimension", "overworld");
        assertNotNull(dropped);
        assertEquals(MISSING_BLOCK, dropped.key());
        assertEquals("regions[1] sulfur-lands", dropped.detail());
    }

    @Test
    public void evaluateCompatExcludesDimensionWithNoRegionsLeft() {
        PackCompatReport report = new PackCompatReport();
        IrisRegion sulfur = excludeBlock(region("sulfur-lands"));
        IrisData data = dataWith(report, sulfur);
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("sulfur-lands"));
        dimension.setLoadKey("sulfurworld");
        dimension.setLoader(data);

        CompatStatus status = dimension.evaluateCompat(gate(report));

        assertTrue(status.excluded());
        CompatFinding cascade = find(report, CompatAction.EXCLUDED, "dimension", "sulfurworld");
        assertNotNull(cascade);
        assertEquals(MISSING_BLOCK, cascade.key());
        assertEquals("no regions remain", cascade.detail());
    }

    @Test
    public void evaluateCompatKeepsDimensionWithSurvivingRegion() {
        PackCompatReport report = new PackCompatReport();
        IrisRegion plains = region("overworld");
        IrisRegion sulfur = excludeBlock(region("sulfur-lands"));
        IrisData data = dataWith(report, plains, sulfur);
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("overworld", "sulfur-lands"));
        dimension.setLoadKey("overworld");
        dimension.setLoader(data);

        assertFalse(dimension.evaluateCompat(gate(report)).excluded());
    }
}
