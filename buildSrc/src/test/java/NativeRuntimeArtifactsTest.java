import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeRuntimeArtifactsTest {
    @Test
    public void acceptsOnlyRequestedPublishedCoordinates() {
        File common = new File("common.jar");
        File provider = new File("provider.jar");
        Map<ComponentIdentifier, File> resolved = Map.of(
                module("native-common", "revision"), common,
                module("native-v26_2_R1", "revision"), provider);
        assertEquals(Map.of("native-common", common, "native-v26_2_R1", provider),
                NativeRuntimeArtifacts.publishedArtifacts(resolved,
                        List.of("native-common", "native-v26_2_R1"), "revision"));
    }

    @Test
    public void rejectsCompositeProjectArtifacts() {
        ComponentIdentifier project = () -> "project :VolmLib:native-common";
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.publishedArtifacts(
                Map.of(project, new File("native-common-local-SNAPSHOT.jar")), List.of("native-common"), "revision"));
    }

    @Test
    public void rejectsUnexpectedCoordinates() {
        List<ComponentIdentifier> invalid = List.of(
                new PublishedModule("other.group", "native-common", "revision"),
                module("native-common", "other-revision"),
                module("native-v26_3_R1", "revision"));
        for (ComponentIdentifier component : invalid) {
            assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.publishedArtifacts(
                    Map.of(component, new File("provider.jar")), List.of("native-common"), "revision"));
        }
    }

    @Test
    public void rejectsMissingProviders() {
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.publishedArtifacts(
                Map.of(module("native-common", "revision"), new File("common.jar")),
                List.of("native-common", "native-v26_2_R1"), "revision"));
    }

    @Test
    public void recordsExactDependenciesAndRetainsSharedProviderReferences() throws Exception {
        Path directory = Files.createTempDirectory("native-artifacts");
        File provider = directory.resolve("provider.jar").toFile();
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "art/arcane/volmlib/nativelib/v26_2_R1/terrain/Provider", null,
                "art/arcane/volmlib/util/collection/KList", null);
        writer.visitEnd();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(provider.toPath()))) {
            jar.putNextEntry(new JarEntry("art/arcane/volmlib/nativelib/v26_2_R1/terrain/Provider.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
        File rules = directory.resolve("keep.pro").toFile();
        NativeRuntimeArtifacts.generate(Map.of("native-common", provider, "native-v26_2_R1", provider),
                "revision", directory.toFile(), rules);
        Properties manifest = new Properties();
        try (Reader reader = Files.newBufferedReader(directory.resolve(NativeRuntimeArtifacts.MANIFEST))) {
            manifest.load(reader);
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(provider.toPath())));
        assertEquals(digest, manifest.getProperty("native-common.sha256"));
        assertEquals(digest, manifest.getProperty("native-v26_2_R1.sha256"));
        assertTrue(Files.readString(rules.toPath()).contains("-keep class art.arcane.volmlib.util.collection.KList { *; }"));
        File plugin = directory.resolve("plugin.jar").toFile();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(plugin.toPath()))) {
            jar.putNextEntry(new JarEntry(NativeRuntimeArtifacts.MANIFEST));
            Files.copy(directory.resolve(NativeRuntimeArtifacts.MANIFEST), jar);
            jar.closeEntry();
        }
        NativeRuntimeArtifacts.verify(plugin, List.of("v26_2_R1"), "revision");
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.verify(plugin, List.of("v26_3_R1"), "revision"));
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.verify(plugin, List.of("v26_2_R1"), "other"));
    }

    private static ModuleComponentIdentifier module(String module, String version) {
        return new PublishedModule("com.github.VolmitSoftware.VolmLib", module, version);
    }

    private record PublishedModule(String group, String module, String version)
            implements ModuleComponentIdentifier, ModuleIdentifier {
        @Override
        public String getGroup() {
            return group;
        }

        @Override
        public String getModule() {
            return module;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public ModuleIdentifier getModuleIdentifier() {
            return this;
        }

        @Override
        public String getName() {
            return module;
        }

        @Override
        public String getDisplayName() {
            return group + ":" + module + ":" + version;
        }
    }
}
