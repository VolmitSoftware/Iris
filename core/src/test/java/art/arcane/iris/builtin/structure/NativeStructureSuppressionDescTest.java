package art.arcane.iris.builtin.structure;

import com.volmit.config.Desc;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NativeStructureSuppressionDescTest {
    @Test
    public void suppressionTypeAndValuesKeepTheirStudioDescriptions() throws NoSuchFieldException {
        assertEquals("Controls whether an Iris structure placement suppresses its imported native source.",
                NativeStructureSuppression.class.getAnnotation(Desc.class).value());
        assertEquals("Leaves native generation enabled alongside this Iris structure placement.",
                NativeStructureSuppression.class.getField("NONE").getAnnotation(Desc.class).value());
        assertEquals("Suppresses the structure's vanillaSource when used by a validated dimension-level replacement placement.",
                NativeStructureSuppression.class.getField("REPLACE_SOURCE").getAnnotation(Desc.class).value());
    }
}
