package art.arcane.iris.pack.value;

import art.arcane.iris.generation.terrain.IrisSlopeClip;
import art.arcane.iris.pack.mod.IrisMod;
import art.arcane.iris.pack.mod.IrisModNoiseStyleReplacer;
import art.arcane.iris.pack.mod.IrisModObjectReplacer;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectLimit;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SchemaMetadataCorrectionsTest {
    @Test
    public void objectHeightLimitsAdmitTheirDefaults() throws Exception {
        Field minimum = IrisObjectLimit.class.getDeclaredField("minimumHeight");
        Field maximum = IrisObjectLimit.class.getDeclaredField("maximumHeight");

        assertEquals(-2048D, minimum.getAnnotation(MinNumber.class).value(), 0D);
        assertEquals(2048D, minimum.getAnnotation(MaxNumber.class).value(), 0D);
        assertEquals(-2048D, maximum.getAnnotation(MinNumber.class).value(), 0D);
        assertEquals(2048D, maximum.getAnnotation(MaxNumber.class).value(), 0D);
    }

    @Test
    public void correctedTypesDescribeTheSchemaTheyExpose() {
        assertEquals("Represents a pack modification schema", IrisMod.class.getAnnotation(Description.class).value());
        assertEquals("Limits object placement by minimum and maximum world height",
                IrisObjectLimit.class.getAnnotation(Description.class).value());
        assertEquals("Limits placement to a minimum and maximum terrain slope",
                IrisSlopeClip.class.getAnnotation(Description.class).value());
    }

    @Test
    public void modReplacerMetadataUsesItsActualTypes() throws Exception {
        Field noiseFind = IrisModNoiseStyleReplacer.class.getDeclaredField("find");
        Field objectReplace = IrisModObjectReplacer.class.getDeclaredField("replace");

        assertFalse(noiseFind.isAnnotationPresent(ArrayType.class));
        assertEquals(IrisObject.class, objectReplace.getAnnotation(RegistryListResource.class).value());
    }
}
