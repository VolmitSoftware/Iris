package art.arcane.iris.core.structure.export;

import art.arcane.iris.engine.object.IrisDirection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VanillaStructureTemplateOrientationTest {
    @Test
    public void aHorizontalFrontIsRenderedAgainstAnUpwardTop() {
        assertEquals("north_up", VanillaStructureTemplateEncoder.orientation(
                IrisDirection.NORTH_NEGATIVE_Z, IrisDirection.UP_POSITIVE_Y));
        assertEquals("south_up", VanillaStructureTemplateEncoder.orientation(
                IrisDirection.SOUTH_POSITIVE_Z, IrisDirection.UP_POSITIVE_Y));
        assertEquals("east_up", VanillaStructureTemplateEncoder.orientation(
                IrisDirection.EAST_POSITIVE_X, IrisDirection.UP_POSITIVE_Y));
        assertEquals("west_up", VanillaStructureTemplateEncoder.orientation(
                IrisDirection.WEST_NEGATIVE_X, IrisDirection.UP_POSITIVE_Y));
    }

    @Test
    public void averticalFrontIsRenderedAgainstItsHorizontalTop() {
        assertEquals("up_north", VanillaStructureTemplateEncoder.orientation(
                IrisDirection.UP_POSITIVE_Y, IrisDirection.NORTH_NEGATIVE_Z));
        assertEquals("down_east", VanillaStructureTemplateEncoder.orientation(
                IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.EAST_POSITIVE_X));
    }

    @Test
    public void averticalFrontCannotTakeAVerticalTop() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> VanillaStructureTemplateEncoder.orientation(
                        IrisDirection.UP_POSITIVE_Y, IrisDirection.UP_POSITIVE_Y));

        assertTrue(failure.getMessage(), failure.getMessage().contains("horizontal top direction"));
    }

    @Test
    public void aHorizontalFrontCannotTakeANonUpwardTop() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> VanillaStructureTemplateEncoder.orientation(
                        IrisDirection.NORTH_NEGATIVE_Z, IrisDirection.SOUTH_POSITIVE_Z));

        assertTrue(failure.getMessage(), failure.getMessage().contains("upward top direction"));
        assertThrows(IllegalArgumentException.class, () -> VanillaStructureTemplateEncoder.orientation(
                IrisDirection.EAST_POSITIVE_X, IrisDirection.DOWN_NEGATIVE_Y));
    }
}
