import art.arcane.iris.buildtools.GenerationRevisionScope;
import art.arcane.iris.buildtools.GenerationBuildRevision;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class GenerationRevisionScopeTest {
    private static final String ENGINE_ROOT = "core/src/main/java/art/arcane/iris/generation/runtime/";
    private static final String NMS_ROOT = "adapters/bukkit/nms/v26_2_R1/src/main/java/art/arcane/iris/platform/bukkit/nms/v26_2_R1/";
    private static final String COMMAND_ROOT = "adapters/bukkit/plugin/src/main/java/art/arcane/iris/command/";

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void operationalChangesPreserveTheGenerationFingerprint() throws Exception {
        Fixture fixture = fixture();
        String original = GenerationBuildRevision.fingerprint(GenerationBuildRevision.read(fixture.revision()));
        for (String exclusion : fixture.options().exclusions()) {
            if (!exclusion.endsWith(".java")) {
                continue;
            }
            Path operationalSource = fixture.root().resolve(exclusion);
            write(operationalSource, "class Operational { String message = \"updated\"; }");
            GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies());
            assertEquals(exclusion, original, GenerationBuildRevision.fingerprint(
                    GenerationBuildRevision.capture(fixture.options(), fixture.dependencies())));
            Files.delete(operationalSource);
            GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies());
        }
    }

    @Test
    public void generationChangesProduceANewBuildRevision() throws Exception {
        Fixture fixture = fixture();
        for (String relative : generationSources()) {
            Path source = fixture.root().resolve(relative);
            Files.writeString(source, "class Generation { int sample = 2; }");
            GenerationBuildRevision.SourceManifest changed = GenerationBuildRevision.capture(fixture.options(), fixture.dependencies());
            assertNotEquals(relative, GenerationBuildRevision.fingerprint(GenerationBuildRevision.read(fixture.revision())),
                    GenerationBuildRevision.fingerprint(changed));
            GenerationBuildRevision.write(fixture.revision(), changed);
            GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies());
            Files.writeString(source, "class Generation { int sample = 1; }");
            GenerationBuildRevision.write(fixture.revision(), GenerationBuildRevision.capture(fixture.options(), fixture.dependencies()));
        }
    }

    @Test
    public void newSourcesChangeRevisionAcrossPlatforms() throws Exception {
        Fixture fixture = fixture();
        for (String root : fixture.options().roots()) {
            if (!root.endsWith("/java")) {
                continue;
            }
            assertNewSourceChangesRevision(fixture, root + "/NewSource.java");
        }
        assertNewSourceChangesRevision(fixture, ENGINE_ROOT + "NewDiagnostics.java");
        assertNewSourceChangesRevision(fixture, NMS_ROOT + "NmsWorldLifecycleHelper.java");
        assertNewSourceChangesRevision(fixture, COMMAND_ROOT + "NewCommand.java");
        assertNewSourceChangesRevision(fixture, "core/src/main/java/art/arcane/iris/world/safeguard/NewTask.java");
    }

    @Test
    public void onlyExplicitOperationalFilesAreExcluded() throws Exception {
        Fixture fixture = fixture();
        List<String> directoryExclusions = fixture.options().exclusions().stream()
                .filter(path -> !path.endsWith(".java"))
                .toList();
        assertTrue(directoryExclusions.isEmpty());
        GenerationBuildRevision.SourceManifest manifest = GenerationBuildRevision.read(fixture.revision());
        for (String relative : generationSources()) {
            assertTrue(relative, manifest.sources().containsKey(relative));
        }
        assertFalse(manifest.sources().containsKey(ENGINE_ROOT + "EngineDiagnostics.java"));
        assertFalse(manifest.sources().containsKey(NMS_ROOT + "NmsWorldLifecycle.java"));
        assertFalse(manifest.sources().containsKey(COMMAND_ROOT + "CommandIris.java"));
    }

    private static void assertNewSourceChangesRevision(Fixture fixture, String relative) throws IOException {
        Path source = fixture.root().resolve(relative);
        write(source, "class NewSource {}");
        GenerationBuildRevision.SourceManifest changed = GenerationBuildRevision.capture(fixture.options(), fixture.dependencies());
        assertTrue(changed.sources().containsKey(relative));
        assertNotEquals(GenerationBuildRevision.fingerprint(GenerationBuildRevision.read(fixture.revision())),
                GenerationBuildRevision.fingerprint(changed));
        Files.delete(source);
    }

    private Fixture fixture() throws Exception {
        Path root = temporary.newFolder().toPath();
        GenerationBuildRevision.CaptureOptions options = GenerationRevisionScope.current(root);
        for (String scope : options.roots()) {
            Path path = root.resolve(scope);
            if (scope.endsWith("/java")) {
                Files.createDirectories(path);
            } else {
                write(path, "build input");
            }
        }
        for (String relative : generationSources()) {
            write(root.resolve(relative), "class Generation { int sample = 1; }");
        }
        for (String exclusion : options.exclusions()) {
            if (exclusion.endsWith(".java")) {
                write(root.resolve(exclusion), "class Operational {}");
            }
        }
        Path dependency = root.resolve("dependency.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(dependency))) {
            output.putNextEntry(new ZipEntry("generation/Math.class"));
            output.write("generation dependency".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Map<String, Path> dependencies = Map.of("volmlib", dependency);
        Path revision = root.resolve("abi-1.revision");
        GenerationBuildRevision.write(revision, GenerationBuildRevision.capture(options, dependencies));
        return new Fixture(root, revision, options, dependencies);
    }

    private static List<String> generationSources() {
        return List.of(
                ENGINE_ROOT + "IrisEngine.java",
                ENGINE_ROOT + "IrisComplex.java",
                ENGINE_ROOT + "IrisEngineMantle.java",
                ENGINE_ROOT + "EngineRuntimeBuilder.java",
                ENGINE_ROOT + "history/GenerationKernelV1.java",
                ENGINE_ROOT + "object/IrisDimension.java",
                ENGINE_ROOT + "mode/ModeOverworld.java",
                "core/src/main/java/art/arcane/iris/pack/loading/IrisData.java",
                "core/src/main/java/art/arcane/iris/configuration/IrisSettings.java",
                "core/agent/src/main/java/GenerationTransformer.java",
                NMS_ROOT + "NmsGenerationHooks.java",
                NMS_ROOT + "NmsGenerationRegistry.java",
                NMS_ROOT + "NMSBinding.java",
                "spi/src/main/java/art/arcane/iris/spi/PlatformGenerationRegistry.java",
                "adapters/modded-common/src/main/java/art/arcane/iris/modded/IrisModdedChunkGenerator.java"
        );
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private record Fixture(Path root, Path revision, GenerationBuildRevision.CaptureOptions options,
                           Map<String, Path> dependencies) {
    }
}
