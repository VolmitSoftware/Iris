package art.arcane.iris.studio.bridge;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileFormTest {
    private enum Shape {ROUND, FLAT}

    private static final class Sample {
        private int height = 8;
        private double weight = 0.5;
        private boolean roots = true;
        private Shape shape = Shape.ROUND;
        private String name = "sample";
    }

    @Test
    public void overridesApplyToACopyAndLeaveTheOriginalAlone() throws Exception {
        Sample original = new Sample();
        Sample copy = ProfileForm.withOverrides(original, Map.of("height", "20", "weight", "1.5", "roots", "false", "shape", "FLAT"));

        assertEquals(8, original.height);
        assertEquals(20, copy.height);
        assertEquals(1.5, copy.weight, 0D);
        assertFalse(copy.roots);
        assertEquals(Shape.FLAT, copy.shape);
        assertEquals("sample", copy.name);
    }

    @Test
    public void aValueThatDoesNotReadAsItsTypeIsIgnored() {
        Sample copy = ProfileForm.withOverrides(new Sample(), Map.of("height", "tall", "shape", "TRIANGLE"));

        assertEquals(8, copy.height);
        assertEquals(Shape.ROUND, copy.shape);
    }

    @Test
    public void theFormListsPlainSettingsWithTheirDefaultsAndWhatChanged() {
        Map<String, String> typed = Map.of("height", "20");
        Sample shown = ProfileForm.withOverrides(new Sample(), typed);

        ProfileForm.Form form = ProfileForm.of("Sample", shown, Set.of("weight"), typed, List.of());

        List<String> names = form.fields().stream().map(ProfileForm.Field::name).toList();
        assertEquals(List.of("height", "roots", "shape"), names);
        ProfileForm.Field height = form.fields().get(0);
        assertEquals(20, height.value());
        assertEquals(8, height.defaultValue());
        assertTrue(height.changed());
        assertFalse(form.fields().get(1).changed());
        assertEquals(List.of("ROUND", "FLAT"), form.fields().get(2).choices());
    }
}
