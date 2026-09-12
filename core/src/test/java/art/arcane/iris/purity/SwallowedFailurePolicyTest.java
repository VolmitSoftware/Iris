package art.arcane.iris.purity;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * A catch whose body is empty says nothing about whether the failure was expected. On world lifecycle,
 * world replacement, runtime control, safeguard, service enable and disable, and generation paths that is
 * the difference between a diagnosable server and a silent one, so these packages carry none: an optional
 * API that is genuinely absent goes through {@code CapabilityProbe}, and everything else reports.
 */
public class SwallowedFailurePolicyTest {
    private static final List<String> PROTECTED_ROOTS = List.of(
            "src/main/java/art/arcane/iris/world/runtime",
            "src/main/java/art/arcane/iris/generation/validation",
            "src/main/java/art/arcane/iris/studio/object",
            "src/main/java/art/arcane/iris/studio/jigsaw",
            "src/main/java/art/arcane/iris/world/lifecycle",
            "src/main/java/art/arcane/iris/world/safeguard",
            "src/main/java/art/arcane/iris/integration",
            "src/main/java/art/arcane/iris/world/tree",
            "src/main/java/art/arcane/iris/world/entity/EntityRiseSVC.java",
            "src/main/java/art/arcane/iris/studio/StudioSVC.java",
            "src/main/java/art/arcane/iris/studio/PackDownloadProgressReporter.java",
            "src/main/java/art/arcane/iris/generation/runtime/EngineMaintenance.java",
            "src/main/java/art/arcane/iris/generation/runtime/GlobalCacheSVC.java",
            "src/main/java/art/arcane/iris/generation/runtime/PreservationSVC.java",
            "src/main/java/art/arcane/iris/diagnostics/LogFilterSVC.java",
            "src/main/java/art/arcane/iris/platform/bukkit/BoardSVC.java",
            "src/main/java/art/arcane/iris/localization",
            "src/main/java/art/arcane/iris/structure/authoring",
            "src/main/java/art/arcane/iris/configuration/IrisSettings.java",
            "src/main/java/art/arcane/iris/generation/decoration/DecoratorCore.java",
            "src/main/java/art/arcane/iris/generation/decoration/DecoratorPlatformHooks.java",
            "src/main/java/art/arcane/iris/generation/decoration/FloatingDecorator.java",
            "src/main/java/art/arcane/iris/generation/decoration/IrisCeilingDecorator.java",
            "src/main/java/art/arcane/iris/generation/decoration/IrisEngineDecorator.java",
            "src/main/java/art/arcane/iris/generation/decoration/IrisSeaFloorDecorator.java",
            "src/main/java/art/arcane/iris/generation/decoration/IrisSeaSurfaceDecorator.java",
            "src/main/java/art/arcane/iris/generation/decoration/IrisShoreLineDecorator.java",
            "src/main/java/art/arcane/iris/generation/decoration/IrisSpeleothems.java",
            "src/main/java/art/arcane/iris/generation/decoration/IrisSugarCane.java",
            "src/main/java/art/arcane/iris/generation/decoration/IrisSurfaceDecorator.java",
            "src/main/java/art/arcane/iris/world/history",
            "../spi/src/main/java",
            "../adapters/bukkit/plugin/src/main/java/art/arcane/iris/Iris.java",
            "../adapters/bukkit/plugin/src/main/java/art/arcane/iris/world/PendingWorldReplacementManager.java"
    );
    private static final Pattern IGNORED_CATCH = Pattern.compile("^\\s*}?\\s*catch \\([^)]*\\)\\s*\\{\\s*$");

    @Test
    public void noProtectedPathEndsAFailureInAnEmptyCatch() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : protectedSources()) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size() - 1; i++) {
                Matcher matcher = IGNORED_CATCH.matcher(lines.get(i));
                if (matcher.matches() && "}".equals(lines.get(i + 1).trim())) {
                    offenders.add(file + ":" + (i + 1) + " " + lines.get(i).trim());
                }
            }
        }

        assertTrue("Empty catch blocks on protected paths: " + String.join("\n", offenders), offenders.isEmpty());
    }

    /**
     * The reporting front door is what makes a failure visible, so a protected path must not print a
     * caught throwable's message on its own and call that handled.
     */
    @Test
    public void theProbeHelperIsTheOnlyQuietFailurePath() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : protectedSources()) {
            String source = Files.readString(file);
            if (source.contains("catch (Throwable ignored)") || source.contains("catch (Exception ignored)")) {
                offenders.add(file.toString());
            }
        }

        assertTrue("Protected paths still name a caught failure \"ignored\": " + String.join("\n", offenders),
                offenders.isEmpty());
    }

    private static List<Path> protectedSources() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String root : PROTECTED_ROOTS) {
            Path path = Path.of(root);
            assertTrue("Protected root is missing: " + root, Files.exists(path));
            if (Files.isRegularFile(path)) {
                files.add(path);
                continue;
            }
            try (Stream<Path> walk = Files.walk(path)) {
                walk.filter(candidate -> candidate.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files;
    }
}
