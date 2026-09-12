package art.arcane.iris.structure;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StructureCaptureImporterFailureTest {
    @Test
    public void captureFailurePreservesStructureChunkDetailAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("placement broke");

        IllegalStateException error = StructureCaptureImporter.captureFailure(
                "minecraft:monument", 12, -8, "platform placement failed", cause);

        assertSame(cause, error.getCause());
        assertTrue(error.getMessage().contains("minecraft:monument"));
        assertTrue(error.getMessage().contains("12,-8"));
        assertTrue(error.getMessage().contains("platform placement failed"));
    }

    @Test
    public void captureFailureSupportsInfrastructureFailuresWithoutACause() {
        IllegalStateException error = StructureCaptureImporter.captureFailure(
                "minecraft:stronghold", -3, 5, "region task was not accepted", null);

        assertNull(error.getCause());
        assertTrue(error.getMessage().contains("minecraft:stronghold"));
        assertTrue(error.getMessage().contains("-3,5"));
        assertTrue(error.getMessage().contains("region task was not accepted"));
    }
}
