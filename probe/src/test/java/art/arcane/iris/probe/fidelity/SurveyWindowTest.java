package art.arcane.iris.probe.fidelity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SurveyWindowTest {
    @Test
    public void coordinatesSitAtStrideCellCentres() {
        SurveyWindow window = new SurveyWindow(0, 0, 65536, 256);

        assertEquals(256, window.perAxis());
        assertEquals(65536L, window.samples());
        assertEquals(-32768 + 128, window.coordinate(0, 0));
        assertEquals(32768 - 128, window.coordinate(0, 255));
    }

    @Test
    public void windowMustBeAPositiveMultipleOfTheStride() {
        assertThrows(IllegalArgumentException.class, () -> new SurveyWindow(0, 0, 1000, 256));
        assertThrows(IllegalArgumentException.class, () -> new SurveyWindow(0, 0, 128, 256));
        assertThrows(IllegalArgumentException.class, () -> new SurveyWindow(0, 0, 256, 0));
    }
}
