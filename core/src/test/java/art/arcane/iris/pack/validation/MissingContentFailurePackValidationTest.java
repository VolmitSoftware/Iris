package art.arcane.iris.pack.validation;

import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MissingContentFailurePackValidationTest {
    private static final String PACK = "sulfur-pack";

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void bindRegistriesWithoutSulfur() {
        CompatFixtures.bind(CompatFixtures.registries("minecraft:sulfur"));
    }

    @After
    public void unbind() {
        CompatFixtures.unbind();
    }

    private File packWithSulfurBiome(boolean requireAllContent) throws Exception {
        File pack = temporaryFolder.newFolder(PACK);
        write(pack, "dimensions/main.json", "{\"requireAllContent\":" + requireAllContent + ",\"regions\":[\"main\"]}");
        write(pack, "regions/main.json", "{\"landBiomes\":[\"plain\",\"sulfur\"]}");
        write(pack, "biomes/plain.json", "{\"name\":\"Plain\"}");
        write(pack, "biomes/sulfur.json",
                "{\"name\":\"Sulfur\",\"layers\":[{\"palette\":[{\"block\":\"minecraft:sulfur\"}]}]}");
        return pack;
    }

    private static boolean hasMissingContentError(PackValidationResult result) {
        return result.getBlockingErrors().stream().anyMatch(error -> error.contains("uses requireAllContent"));
    }

    @Test
    public void lenientDimensionKeepsGatedContentOutOfTheBlockingErrors() throws Exception {
        PackValidationResult result = PackValidator.validate(packWithSulfurBiome(false));

        assertFalse(result.getCompatFindings().isEmpty());
        assertFalse(result.getBlockingErrors().toString(), hasMissingContentError(result));
    }

    @Test
    public void requiringDimensionFailsValidationNamingTheRemovedContent() throws Exception {
        PackValidationResult result = PackValidator.validate(packWithSulfurBiome(true));

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().toString(), result.getBlockingErrors().contains(
                "Dimension 'main' in pack '" + PACK + "' uses requireAllContent and content is unavailable on Minecraft"
                        + " 26.1.2: minecraft:sulfur (block): excluded biome sulfur at layers[0].palette[0];"
                        + " dropped region main at landBiomes[1] sulfur"));
    }

    @Test
    public void requiringDimensionIgnoresContentItNeverReaches() throws Exception {
        File pack = temporaryFolder.newFolder(PACK);
        write(pack, "dimensions/main.json", "{\"requireAllContent\":true,\"regions\":[\"main\"]}");
        write(pack, "regions/main.json", "{\"landBiomes\":[\"plain\"]}");
        write(pack, "biomes/plain.json", "{\"name\":\"Plain\"}");
        write(pack, "biomes/stray.json",
                "{\"name\":\"Stray\",\"layers\":[{\"palette\":[{\"block\":\"minecraft:sulfur\"}]}]}");

        PackValidationResult result = PackValidator.validate(pack);

        assertFalse(result.getCompatFindings().isEmpty());
        assertFalse(result.getBlockingErrors().toString(), hasMissingContentError(result));
    }

    private static void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
