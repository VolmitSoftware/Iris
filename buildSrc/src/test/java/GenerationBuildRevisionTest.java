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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationBuildRevisionTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void verifiesUnchangedSourceAndCanonicalDependencyContents() throws Exception {
        Fixture fixture = fixture();
        GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies());
        String original = GenerationBuildRevision.artifactHash(fixture.dependency());
        artifact(fixture.dependency(), "same code", "another version", 1_700_000_000_000L);
        assertEquals(original, GenerationBuildRevision.artifactHash(fixture.dependency()));
        GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies());
        assertEquals(GenerationBuildRevision.fingerprint(GenerationBuildRevision.read(fixture.revision())),
                GenerationBuildRevision.fingerprint(GenerationBuildRevision.capture(fixture.options(), fixture.dependencies())));
    }

    @Test
    public void dependencyReleaseNamesAndMavenMetadataDoNotChangeGenerationIdentity() throws Exception {
        Fixture fixture = fixture();
        String original = GenerationBuildRevision.fingerprint(GenerationBuildRevision.read(fixture.revision()));
        Path released = fixture.root().resolve("math-2.0.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(released))) {
            for (Map.Entry<String, String> entry : Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nImplementation-Version: 2.0\r\nBuild-Time: 12345\r\n\r\n",
                    "META-INF/maven/example/math/pom.properties", "groupId=example\nartifactId=math\nversion=2.0\n",
                    "META-INF/maven/example/math/pom.xml", "<project><version>2.0</version></project>",
                    "example/Math.class", "same code").entrySet()) {
                ZipEntry archiveEntry = new ZipEntry(entry.getKey());
                archiveEntry.setTime(1_700_000_000_000L);
                output.putNextEntry(archiveEntry);
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        Map<String, Path> dependencies = Map.of("math", released);

        GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), dependencies);
        assertEquals(original, GenerationBuildRevision.fingerprint(
                GenerationBuildRevision.capture(fixture.options(), dependencies)));
    }

    @Test
    public void rejectsChangedAddedAndDeletedGenerationSources() throws Exception {
        Fixture fixture = fixture();
        Path source = fixture.root().resolve("generation/Noise.java");
        Files.writeString(source, "class Noise { int value = 2; }");
        assertTrue(assertThrows(IOException.class,
                () -> GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies()))
                .getMessage().contains("source changed"));
        Files.writeString(source, "class Noise { int value = 1; }");
        Path added = fixture.root().resolve("generation/Added.java");
        Files.writeString(added, "class Added {}");
        assertTrue(assertThrows(IOException.class,
                () -> GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies()))
                .getMessage().contains("source added"));
        Files.delete(added);
        Files.delete(source);
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies()));
    }

    @Test
    public void rejectsChangedAddedAndDeletedDependencies() throws Exception {
        Fixture fixture = fixture();
        String original = GenerationBuildRevision.artifactHash(fixture.dependency());
        artifact(fixture.dependency(), "changed code", "same version", 0L);
        assertNotEquals(original, GenerationBuildRevision.artifactHash(fixture.dependency()));
        assertTrue(assertThrows(IOException.class,
                () -> GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies()))
                .getMessage().contains("dependency changed"));
        artifact(fixture.dependency(), "same code", "same version", 0L);
        assertThrows(IOException.class, () -> GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(),
                Map.of("math", fixture.dependency(), "new-math", fixture.dependency())));
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), Map.of()));
    }

    @Test
    public void rejectsMultiReleaseManifestChanges() throws Exception {
        Fixture fixture = fixture();
        String original = GenerationBuildRevision.artifactHash(fixture.dependency());
        artifact(fixture.dependency(), "same code", "same version\r\nMulti-Release: true", 0L);
        assertNotEquals(original, GenerationBuildRevision.artifactHash(fixture.dependency()));
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies()));
    }

    @Test
    public void rejectsAgentEntrypointAndCapabilityChanges() throws Exception {
        Fixture fixture = fixture();
        String original = GenerationBuildRevision.artifactHash(fixture.dependency());
        for (String attribute : List.of("Agent-Class", "Premain-Class", "Launcher-Agent-Class", "Boot-Class-Path",
                "Can-Redefine-Classes", "Can-Retransform-Classes", "Can-Set-Native-Method-Prefix")) {
            artifact(fixture.dependency(), "same code", "same version\r\n" + attribute + ": changed", 0L);
            assertNotEquals(attribute, original, GenerationBuildRevision.artifactHash(fixture.dependency()));
            assertThrows(IOException.class,
                    () -> GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies()));
        }
    }

    @Test
    public void capturesExplicitAgentBuildInputsAndExecutableSources() throws Exception {
        Fixture fixture = fixture();
        Path agentSource = fixture.root().resolve("agent/src/main/java/Installer.java");
        Path agentBuild = fixture.root().resolve("agent/build.gradle");
        Files.createDirectories(agentSource.getParent());
        Files.writeString(agentSource, "class Installer {}");
        Files.writeString(agentBuild, "plugins { id 'java' }");
        GenerationBuildRevision.CaptureOptions options = new GenerationBuildRevision.CaptureOptions(
                fixture.root(), 1, "example.KernelV1", List.of(new GenerationBuildRevision.AlgorithmVersion(1, 1)),
                List.of("generation", "agent/build.gradle", "agent/src/main/java"), List.of("generation/kernels"));
        Path revision = fixture.root().resolve("agent.revision");
        GenerationBuildRevision.write(revision, GenerationBuildRevision.capture(options, fixture.dependencies()));
        GenerationBuildRevision.verifySnapshot(fixture.root(), revision, fixture.dependencies());
        Files.writeString(agentBuild, "plugins { id 'application' }");
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.verifySnapshot(fixture.root(), revision, fixture.dependencies()));
        Files.writeString(agentBuild, "plugins { id 'java' }");
        Files.writeString(agentSource, "class Installer { boolean defers; }");
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.verifySnapshot(fixture.root(), revision, fixture.dependencies()));
    }

    @Test
    public void checksTheActualPlatformDependencySubset() throws Exception {
        Fixture fixture = fixture();
        GenerationBuildRevision.verifyDependencySubset(fixture.revision(), fixture.dependencies());
        assertThrows(IOException.class, () -> GenerationBuildRevision.verifyDependencySubset(fixture.revision(),
                Map.of("unknown-library", fixture.dependency())));
        artifact(fixture.dependency(), "platform override", "same version", 0L);
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.verifyDependencySubset(fixture.revision(), fixture.dependencies()));
    }

    @Test
    public void catalogRequiresAPackagedAlgorithmTuple() throws Exception {
        Fixture fixture = fixture();
        Path catalog = fixture.root().resolve("catalog.tsv");
        Files.writeString(catalog, "iris-generation-kernel-catalog-v1\ncurrent\t1\t1\t1\nkernel\t1\n");
        Map<Integer, GenerationBuildRevision.SourceManifest> manifests = Map.of(1, GenerationBuildRevision.read(fixture.revision()));
        GenerationBuildRevision.verifyCatalog(catalog, manifests);
        Files.writeString(catalog, "iris-generation-kernel-catalog-v1\ncurrent\t1\t2\t1\nkernel\t1\n");
        assertThrows(IOException.class, () -> GenerationBuildRevision.verifyCatalog(catalog, manifests));
        Files.writeString(catalog, "iris-generation-kernel-catalog-v1\ncurrent\t1\t1\t1\nkernel\t1\nkernel\t2\n");
        assertThrows(IOException.class, () -> GenerationBuildRevision.verifyCatalog(catalog, manifests));
    }

    @Test
    public void recapturesChangedGenerationWithoutChangingAbi() throws Exception {
        Fixture fixture = fixture();
        String original = GenerationBuildRevision.fingerprint(GenerationBuildRevision.read(fixture.revision()));
        Files.writeString(fixture.root().resolve("generation/Noise.java"), "class Noise { int value = 9; }");
        artifact(fixture.dependency(), "new generation code", "new version", 0L);
        GenerationBuildRevision.SourceManifest next = GenerationBuildRevision.capture(fixture.options(), fixture.dependencies());
        GenerationBuildRevision.write(fixture.revision(), next);
        GenerationBuildRevision.verifySnapshot(fixture.root(), fixture.revision(), fixture.dependencies());
        assertEquals(1, next.abi());
        assertNotEquals(original, GenerationBuildRevision.fingerprint(next));
        assertEquals(next, GenerationBuildRevision.read(fixture.revision()));
    }

    private Fixture fixture() throws Exception {
        Path root = temporary.newFolder().toPath();
        Files.createDirectories(root.resolve("generation"));
        Files.writeString(root.resolve("generation/Noise.java"), "class Noise { int value = 1; }");
        Path dependency = root.resolve("math.jar");
        artifact(dependency, "same code", "same version", 0L);
        GenerationBuildRevision.CaptureOptions options = new GenerationBuildRevision.CaptureOptions(
                root, 1, "example.KernelV1", List.of(new GenerationBuildRevision.AlgorithmVersion(1, 1)),
                List.of("generation"), List.of("generation/kernels"));
        Path revision = root.resolve("abi-1.revision");
        Map<String, Path> dependencies = Map.of("math", dependency);
        GenerationBuildRevision.write(revision, GenerationBuildRevision.capture(options, dependencies));
        return new Fixture(root, revision, dependency, dependencies, options);
    }

    private static void artifact(Path path, String content, String version, long timestamp) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            ZipEntry metadata = new ZipEntry("META-INF/MANIFEST.MF");
            metadata.setTime(timestamp);
            output.putNextEntry(metadata);
            output.write(("Manifest-Version: 1.0\r\nImplementation-Version: " + version + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            ZipEntry code = new ZipEntry("example/Math.class");
            code.setTime(timestamp);
            output.putNextEntry(code);
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private record Fixture(Path root, Path revision, Path dependency, Map<String, Path> dependencies,
                           GenerationBuildRevision.CaptureOptions options) {
    }
}
