package art.arcane.iris.pack.value;

import art.arcane.iris.generation.hydrology.IrisGrottoPoolLevel;
import art.arcane.iris.generation.hydrology.IrisRiverBedProfile;
import art.arcane.iris.generation.hydrology.IrisRiverBlendStyle;
import art.arcane.iris.generation.hydrology.IrisRiverPlacementMode;
import art.arcane.iris.generation.hydrology.IrisRiverRoutingMode;

import art.arcane.volmlib.util.documentation.Description;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class HydrologyEnumDescriptionsTest {
    private static final List<Class<? extends Enum<?>>> HYDROLOGY_ENUMS = List.of(
            IrisRiverPlacementMode.class,
            IrisRiverRoutingMode.class,
            IrisGrottoPoolLevel.class,
            IrisRiverBlendStyle.class,
            IrisRiverBedProfile.class
    );

    @Test
    public void hydrologyEnumsDescribeThemselvesForTheSchema() {
        for (Class<? extends Enum<?>> type : HYDROLOGY_ENUMS) {
            Description description = type.getAnnotation(Description.class);
            assertNotNull(type.getSimpleName() + " needs a @Description", description);
            assertFalse(type.getSimpleName() + " has a blank @Description", description.value().isBlank());
        }
    }

    @Test
    public void everyHydrologyEnumConstantDescribesItselfForTheSchema() throws NoSuchFieldException {
        for (Class<? extends Enum<?>> type : HYDROLOGY_ENUMS) {
            for (Enum<?> constant : type.getEnumConstants()) {
                Field field = type.getField(constant.name());
                Description description = field.getAnnotation(Description.class);
                String label = type.getSimpleName() + "." + constant.name();
                assertNotNull(label + " needs a @Description", description);
                assertFalse(label + " has a blank @Description", description.value().isBlank());
            }
        }
    }
}
