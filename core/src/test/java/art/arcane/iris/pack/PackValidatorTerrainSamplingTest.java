package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackValidatorTerrainSamplingTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void acceptsOmittedAndSupportedSamplingSteps() throws Exception {
        assertTrue(validate("terrainSamplingStep", null).isLoadable());
        for (String field : new String[]{"terrainSamplingStep", "caveDensitySamplingStep"}) {
            for (String value : new String[]{"1", "2", "4", "8"}) {
                PackValidationResult result = validate(field, value);
                assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
            }
        }
    }

    @Test
    public void rejectsInvalidSamplingStepsBeforeWorldCreation() throws Exception {
        for (String field : new String[]{"terrainSamplingStep", "caveDensitySamplingStep"}) {
            for (String value : new String[]{"0", "-1", "3", "16", "4.5", "\"4\"", "null", "true"}) {
                PackValidationResult result = validate(field, value);
                assertFalse("Accepted " + field + "=" + value, result.isLoadable());
                assertTrue(result.getBlockingErrors().contains(
                        "Dimension 'main' " + field + " must be one of 1, 2, 4, or 8."));
            }
        }
    }

    @Test
    public void acceptsOnlySupportedBiomeBoundsSamplingSteps() throws Exception {
        assertTrue(validate("biomeBoundsSamplingStep", null).isLoadable());
        for (String value : new String[]{"4", "8", "16", "32"}) {
            PackValidationResult result = validate("biomeBoundsSamplingStep", value);
            assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
        }
        for (String value : new String[]{"0", "-1", "1", "2", "3", "5", "64", "8.5", "\"8\"", "null", "true"}) {
            PackValidationResult result = validate("biomeBoundsSamplingStep", value);
            assertFalse("Accepted biomeBoundsSamplingStep=" + value, result.isLoadable());
            assertTrue(result.getBlockingErrors().contains(
                    "Dimension 'main' biomeBoundsSamplingStep must be one of 4, 8, 16, or 32."));
        }
    }

    private PackValidationResult validate(String field, String value) throws Exception {
        Path pack = temporary.newFolder().toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("regions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.writeString(pack.resolve("dimensions/main.json"), "{\"regions\":[\"main\"]"
                + (value == null ? "" : ",\"" + field + "\":" + value) + "}");
        Files.writeString(pack.resolve("regions/main.json"), "{\"landBiomes\":[\"main\"]}");
        Files.writeString(pack.resolve("biomes/main.json"), "{\"name\":\"Main\"}");
        return PackValidator.validate(pack.toFile());
    }
}
