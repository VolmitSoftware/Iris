package art.arcane.iris.engine.object.structure;

import art.arcane.iris.engine.object.structure.IrisStructureYBand;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IrisStructureYBandTest {
    @Test
    public void authoredBoundsResolveInOrder() {
        IrisStructureYBand band = new IrisStructureYBand().setMin(-120).setMax(-20);

        assertEquals(-120, band.resolvedMin());
        assertEquals(-20, band.resolvedMax());
    }

    @Test
    public void reversedBoundsNormalizeInsteadOfInverting() {
        IrisStructureYBand band = new IrisStructureYBand().setMin(-20).setMax(-120);

        assertEquals(-120, band.resolvedMin());
        assertEquals(-20, band.resolvedMax());
    }

    @Test
    public void collapsedBoundsResolveToASingleY() {
        IrisStructureYBand band = new IrisStructureYBand().setMin(-64).setMax(-64);

        assertEquals(-64, band.resolvedMin());
        assertEquals(-64, band.resolvedMax());
    }

    @Test
    public void numericSchemaBoundsCoverTheWholeBuildRange() throws NoSuchFieldException {
        assertBounds("min");
        assertBounds("max");
    }

    private void assertBounds(String fieldName) throws NoSuchFieldException {
        Field field = IrisStructureYBand.class.getDeclaredField(fieldName);
        MinNumber minimum = field.getAnnotation(MinNumber.class);
        MaxNumber maximum = field.getAnnotation(MaxNumber.class);

        assertNotNull(minimum);
        assertNotNull(maximum);
        assertEquals(-4064D, minimum.value(), 0D);
        assertEquals(4064D, maximum.value(), 0D);
    }
}
