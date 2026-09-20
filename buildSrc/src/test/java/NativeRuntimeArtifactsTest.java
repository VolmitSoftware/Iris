import org.gradle.api.GradleException;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeRuntimeArtifactsTest {
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
}
