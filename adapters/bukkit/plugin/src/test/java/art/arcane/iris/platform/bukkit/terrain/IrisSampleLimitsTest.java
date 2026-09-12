package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisColumnField;
import art.arcane.iris.api.terrain.IrisColumnQuery;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisSampleLimitsTest {
    @Test
    public void chunkCapIsAShareOfTheNoiseCacheWithAFloor() {
        assertEquals(256, IrisSampleLimits.maxChunks(1_024));
        assertEquals(IrisSampleLimits.MINIMUM_CHUNKS, IrisSampleLimits.maxChunks(0));
        assertEquals(IrisSampleLimits.MINIMUM_CHUNKS, IrisSampleLimits.maxChunks(16));
    }

    @Test
    public void columnCapIsDerivedFromTheChunkCapAndDoesNotOverflow() {
        assertEquals(65_536, IrisSampleLimits.maxColumns(1_024));
        assertTrue(IrisSampleLimits.maxColumns(Integer.MAX_VALUE) > 0);
    }

    @Test
    public void strideCannotSmuggleAQueryPastTheChunkCap() {
        IrisColumnQuery smuggled = IrisColumnQuery.rect(
                0, 0, 6399, 6399, 64, EnumSet.of(IrisColumnField.SURFACE_KIND));

        int maxColumns = IrisSampleLimits.maxColumns(1_024);
        int maxChunks = IrisSampleLimits.maxChunks(1_024);

        assertTrue("this query must pass a column-only cap", smuggled.columnCount() <= maxColumns);
        assertFalse("but it must be refused on chunk span",
                IrisSampleLimits.withinLimits(smuggled, maxColumns, maxChunks));
    }

    @Test
    public void aQueryInsideBothCapsIsAccepted() {
        IrisColumnQuery accepted = IrisColumnQuery.rect(
                0, 0, 255, 255, 4, EnumSet.of(IrisColumnField.SURFACE_KIND));

        assertTrue(IrisSampleLimits.withinLimits(
                accepted, IrisSampleLimits.maxColumns(1_024), IrisSampleLimits.maxChunks(1_024)));
    }

    @Test
    public void aDenseQueryOverManyColumnsIsRefused() {
        IrisColumnQuery dense = IrisColumnQuery.rect(
                0, 0, 1023, 1023, 1, EnumSet.of(IrisColumnField.SURFACE_HEIGHT));

        assertTrue(dense.columnCount() > IrisSampleLimits.maxColumns(1_024));
        assertFalse(IrisSampleLimits.withinLimits(
                dense, IrisSampleLimits.maxColumns(1_024), IrisSampleLimits.maxChunks(1_024)));
    }
}
