package art.arcane.iris.util.common.director.specialhandlers;

import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ObjectScaleHandlerTest {
    @Test
    public void distinguishesInheritedScaleFromExplicitOne() throws Exception {
        ObjectScaleHandler handler = new ObjectScaleHandler();
        assertNull(handler.parse("dimension", true));
        assertNull(handler.parse(" DIMENSION ", false));
        assertEquals(1D, handler.parse("1.0", false), 0D);
        assertEquals(0.5D, handler.parse("0.5", false), 0D);
        assertEquals(2D, handler.parse("2", false), 0D);
        assertEquals("dimension", handler.toString(null));
    }

    @Test
    public void rejectsInvalidManualFactors() {
        ObjectScaleHandler handler = new ObjectScaleHandler();
        for (String invalid : new String[]{"NaN", "Infinity", "-Infinity", "0", "-1", "0.009", "50.001"}) {
            assertThrows(invalid, DirectorParsingException.class, () -> handler.parse(invalid, false));
        }
    }
}
