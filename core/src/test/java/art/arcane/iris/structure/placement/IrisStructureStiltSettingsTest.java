package art.arcane.iris.structure.placement;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class IrisStructureStiltSettingsTest {
    @Test
    public void defaultsToCobblestoneFoundation() {
        IrisStructureStiltSettings settings = new IrisStructureStiltSettings();

        assertEquals(64, settings.getMaxDepth());
        assertEquals(1, settings.getSpacing());
        assertFalse(settings.isSupportNonOccluding());
        assertNotNull(settings.getPalette());
        assertEquals(1, settings.getPalette().getPalette().size());
        assertEquals("minecraft:cobblestone", settings.getPalette().getPalette().get(0).getBlock());
    }

    @Test
    public void maxDepthSchemaIsBoundedToTheMaximumWorldHeight() throws NoSuchFieldException {
        Field maxDepth = IrisStructureStiltSettings.class.getDeclaredField("maxDepth");
        MinNumber minimum = maxDepth.getAnnotation(MinNumber.class);
        MaxNumber maximum = maxDepth.getAnnotation(MaxNumber.class);

        assertNotNull(minimum);
        assertNotNull(maximum);
        assertEquals(1.0, minimum.value(), 0.0);
        assertEquals(4064.0, maximum.value(), 0.0);
    }

    @Test
    public void spacingSchemaSupportsSparseFoundations() throws NoSuchFieldException {
        Field spacing = IrisStructureStiltSettings.class.getDeclaredField("spacing");
        MinNumber minimum = spacing.getAnnotation(MinNumber.class);
        MaxNumber maximum = spacing.getAnnotation(MaxNumber.class);

        assertNotNull(minimum);
        assertNotNull(maximum);
        assertEquals(1.0, minimum.value(), 0.0);
        assertEquals(64.0, maximum.value(), 0.0);
    }
}
