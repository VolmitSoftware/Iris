package art.arcane.iris;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class IrisDisabledStaticsTest {
    @Test
    public void serviceLookupAfterDisableStatesThatIrisIsDisabled() {
        assertNull(Iris.instance);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> Iris.service(Runnable.class));

        assertEquals("Iris is disabled; Runnable is unavailable", failure.getMessage());
    }

    @Test
    public void tempFolderAfterDisableStatesThatIrisIsDisabled() {
        assertNull(Iris.instance);

        IllegalStateException failure = assertThrows(IllegalStateException.class, Iris::getTemp);

        assertEquals("Iris is disabled; the temp folder is unavailable", failure.getMessage());
    }
}
