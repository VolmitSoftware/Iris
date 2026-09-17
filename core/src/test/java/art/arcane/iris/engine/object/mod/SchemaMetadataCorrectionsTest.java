package art.arcane.iris.engine.object.mod;

import art.arcane.iris.engine.object.mod.IrisMod;
import art.arcane.iris.engine.object.mod.IrisModNoiseStyleReplacer;
import art.arcane.iris.engine.object.mod.IrisModObjectReplacer;
import art.arcane.iris.engine.object.placement.IrisObjectLimit;
import art.arcane.iris.engine.object.placement.IrisSlopeClip;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
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
        assertEquals("Represents a pack modification schema", IrisMod.class.getAnnotation(Desc.class).value());
        assertEquals("Limits object placement by minimum and maximum world height",
                IrisObjectLimit.class.getAnnotation(Desc.class).value());
        assertEquals("Limits placement to a minimum and maximum terrain slope",
                IrisSlopeClip.class.getAnnotation(Desc.class).value());
    }

    @Test
    public void modReplacerMetadataUsesItsActualTypes() throws Exception {
        Field noiseFind = IrisModNoiseStyleReplacer.class.getDeclaredField("find");
        Field objectReplace = IrisModObjectReplacer.class.getDeclaredField("replace");

        assertFalse(noiseFind.isAnnotationPresent(ArrayType.class));
        assertEquals(IrisObject.class, objectReplace.getAnnotation(RegistryListResource.class).value());
    }
}
