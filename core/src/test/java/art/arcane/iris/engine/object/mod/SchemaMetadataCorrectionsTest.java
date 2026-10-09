package art.arcane.iris.engine.object.mod;

import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.validate.ObjectLimit;
import art.arcane.iris.engine.object.mod.IrisMod;
import art.arcane.iris.engine.object.mod.IrisModNoiseStyleReplacer;
import art.arcane.iris.engine.object.mod.IrisModObjectReplacer;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
        assertEquals("Represents a pack modification schema", IrisMod.class.getAnnotation(Desc.class).value());
        assertEquals("Limits object placement by minimum and maximum world height",
                ObjectLimit.class.getAnnotation(com.volmit.config.Desc.class).value());
        assertEquals("Limits placement to a minimum and maximum terrain slope",
                SlopeClip.class.getAnnotation(com.volmit.config.Desc.class).value());
    }

    @Test
    public void modReplacerMetadataUsesItsActualTypes() throws Exception {
        Field noiseFind = IrisModNoiseStyleReplacer.class.getDeclaredField("find");
        Field objectReplace = IrisModObjectReplacer.class.getDeclaredField("replace");

        assertFalse(noiseFind.isAnnotationPresent(ArrayType.class));
        assertEquals(IrisObject.class, objectReplace.getAnnotation(RegistryListResource.class).value());
    }
}
