package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.DataVersion;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ExportCapabilitiesTest {
    @Test
    public void capabilitiesCarryTheTarget() {
        assertSame(DataVersion.V26_3, ExportCapabilities.forTarget(DataVersion.V26_3).target());
    }

    @Test
    public void olderTargetsAreRejected() {
        for (DataVersion older : List.of(DataVersion.V26_2, DataVersion.V26_1_2, DataVersion.V1_21_1)) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ExportCapabilities.forTarget(older));
            assertEquals("Unsupported datapack export version '" + older.getVersion() + "'. Supported: 26.3", error.getMessage());
        }
    }
}
