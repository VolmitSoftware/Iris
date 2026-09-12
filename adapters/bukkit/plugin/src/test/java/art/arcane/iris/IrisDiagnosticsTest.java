package art.arcane.iris;

import art.arcane.iris.diagnostics.splash.IrisSplashPackScanner;
import art.arcane.iris.command.CommandSVC;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisDiagnosticsTest {
    @Test
    public void serviceInitializationSkipsPackageHelperClasses() throws Exception {
        Class<?> registrar = Class.forName("art.arcane.iris.command.PaperCommandRegistrar");

        assertFalse(Iris.isConcreteImplementation(registrar, IrisService.class));
        assertTrue(Iris.isConcreteImplementation(CommandSVC.class, IrisService.class));
        // The service package is eagerly Class.forName-scanned by JarScanner at enable; a class
        // referencing Paper-only types there prints an NCDFE stack trace on plain Spigot.
        assertFalse(
                "Paper-only command registrar must stay out of the JarScanner-scanned services package",
                "art.arcane.iris.generation.runtime".equals(registrar.getPackageName()));
    }

    @Test
    public void reportErrorWithContextPrintsFullStacktrace() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            Iris.reportError("Runtime world creation failed.", new IllegalStateException("outer", new IllegalArgumentException("inner")));
        } finally {
            System.setErr(originalErr);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Runtime world creation failed."));
        assertTrue(text.contains("IllegalStateException"));
        assertTrue(text.contains("IllegalArgumentException"));
        assertTrue(text.contains("inner"));
    }

    @Test
    public void splashPackScanSkipsInternalAndInvalidFolders() throws Exception {
        Path root = Files.createTempDirectory("iris-splash");
        try {
            Path validPack = root.resolve("overworld");
            Files.createDirectories(validPack.resolve("dimensions"));
            Files.writeString(validPack.resolve("dimensions").resolve("overworld.json"), "{\"version\":\"4000\"}");

            Files.createDirectories(root.resolve("datapack-imports"));

            Path brokenPack = root.resolve("broken");
            Files.createDirectories(brokenPack.resolve("dimensions"));
            Files.writeString(brokenPack.resolve("dimensions").resolve("broken.json"), "{");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream originalErr = System.err;
            System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
            List<IrisSplashPackScanner.SplashPackMetadata> packs;
            try {
                packs = IrisSplashPackScanner.collect(root.toFile(), Iris::reportError);
            } finally {
                System.setErr(originalErr);
            }

            assertEquals(1, packs.size());
            assertEquals("overworld", packs.get(0).name());
            assertEquals("4000", packs.get(0).version());

            String text = output.toString(StandardCharsets.UTF_8);
            assertTrue(text.contains("Failed to read splash metadata for dimension pack \"broken\"."));
            assertTrue(text.contains("Json"));
        } finally {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
