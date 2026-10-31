package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.pack.datapack.DataVersion;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class WorldgenExportTargetsTest {
    @Test
    public void supportedTargetsStartAt26_3() {
        assertEquals(List.of(DataVersion.V26_3), WorldgenExportTargets.SUPPORTED);
    }

    @Test
    public void blankResolvesToLatestSupported() {
        assertSame(DataVersion.V26_3, WorldgenExportTargets.resolve(""));
        assertSame(DataVersion.V26_3, WorldgenExportTargets.resolve("  "));
        assertSame(DataVersion.V26_3, WorldgenExportTargets.resolve(null));
    }

    @Test
    public void patchSpellingResolves() {
        assertSame(DataVersion.V26_3, WorldgenExportTargets.resolve("26.3.0"));
        assertSame(DataVersion.V26_3, WorldgenExportTargets.resolve(" 26.3 "));
    }

    @Test
    public void olderVersionsAreRefused() {
        for (String older : List.of("26.2", "26.2.0", "26.1.2", "1.21.1")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> WorldgenExportTargets.resolve(older));
            assertEquals("Unsupported datapack export version '" + older + "'. Supported: 26.3", error.getMessage());
        }
    }

    @Test
    public void unknownVersionListsSupported() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> WorldgenExportTargets.resolve("27.0"));
        assertEquals("Unsupported datapack export version '27.0'. Supported: 26.3", error.getMessage());
    }

    @Test
    public void blankModeIsOverworld() {
        assertSame(WorldgenExportMode.OVERWORLD, WorldgenExportMode.parse(""));
        assertSame(WorldgenExportMode.OVERWORLD, WorldgenExportMode.parse(null));
    }

    @Test
    public void modeParsingIgnoresCase() {
        assertSame(WorldgenExportMode.PRESET, WorldgenExportMode.parse("preset"));
        assertSame(WorldgenExportMode.DIMENSION, WorldgenExportMode.parse("Dimension"));
        assertSame(WorldgenExportMode.OVERWORLD, WorldgenExportMode.parse(" OVERWORLD "));
    }

    @Test
    public void unknownModeMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> WorldgenExportMode.parse("nether"));
        assertEquals("Unknown export mode 'nether'. Expected: preset, dimension, overworld", error.getMessage());
    }
}
