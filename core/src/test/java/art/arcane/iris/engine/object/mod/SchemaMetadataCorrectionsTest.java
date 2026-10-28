package art.arcane.iris.engine.object.mod;

import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.validate.ObjectLimit;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

public class SchemaMetadataCorrectionsTest {
    @Test
    public void objectHeightLimitsAdmitTheirDefaults() throws Exception {
        Field minimum = ObjectLimit.class.getDeclaredField("minimumHeight");
        Field maximum = ObjectLimit.class.getDeclaredField("maximumHeight");

        assertEquals(-2048D, minimum.getAnnotation(com.volmit.config.MinNumber.class).value(), 0D);
        assertEquals(2048D, minimum.getAnnotation(com.volmit.config.MaxNumber.class).value(), 0D);
        assertEquals(-2048D, maximum.getAnnotation(com.volmit.config.MinNumber.class).value(), 0D);
        assertEquals(2048D, maximum.getAnnotation(com.volmit.config.MaxNumber.class).value(), 0D);
    }

    @Test
    public void correctedTypesDescribeTheSchemaTheyExpose() {
        assertEquals("Limits object placement by minimum and maximum world height",
                ObjectLimit.class.getAnnotation(com.volmit.config.Desc.class).value());
        assertEquals("Limits placement to a minimum and maximum terrain slope",
                SlopeClip.class.getAnnotation(com.volmit.config.Desc.class).value());
    }
}
