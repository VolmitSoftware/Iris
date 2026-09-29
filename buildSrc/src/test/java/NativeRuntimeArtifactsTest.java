import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeRuntimeArtifactsTest {
    private static final List<String> MODULES = List.of("native-common", "native-v26_2_R1");

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void describesTheEmbeddedProvidersAndRetainsTheirSharedReferences() throws Exception {
        Map<String, File> natives = natives();
        File output = temporary.newFolder("generated");
        File rules = new File(output, "keep.pro");
        NativeRuntimeArtifacts.generate(natives, MODULES, output, rules);
        Properties manifest = new Properties();
        try (Reader reader = Files.newBufferedReader(output.toPath().resolve(NativeRuntimeArtifacts.MANIFEST))) {
            manifest.load(reader);
        }
        assertEquals("native-common,native-v26_2_R1", manifest.getProperty("modules"));
        assertEquals("embedded", manifest.getProperty("storage"));
        assertFalse(manifest.containsKey("repository"));
        for (String module : MODULES) {
            String digest = digest(Files.readAllBytes(natives.get(module).toPath()));
            assertEquals(digest, manifest.getProperty(module + ".sha256"));
            assertEquals("com.github.VolmitSoftware.VolmLib:" + module + ":embedded-" + digest,
                    manifest.getProperty(module + ".coordinate"));
        }
        assertTrue(Files.readString(rules.toPath()).contains("-keep class art.arcane.volmlib.util.collection.KList { *; }"));
        assertEquals(List.of("art/arcane/volmlib/util/collection/KList"),
                Files.readAllLines(output.toPath().resolve("META-INF/volmit/runtime-roots.list")));
    }

    @Test
    public void rejectsAProviderSetThatDiffersFromTheRuntimeModules() throws Exception {
        Map<String, File> natives = natives();
        natives.remove("native-v26_2_R1");
        File output = temporary.newFolder("generated");
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.generate(natives, MODULES, output,
                new File(output, "keep.pro")));
    }

    @Test
    public void verifiesThePluginManifestAgainstTheProvidersBeingEmbedded() throws Exception {
        Map<String, File> natives = natives();
        File plugin = plugin(natives, Map.of());
        NativeRuntimeArtifacts.verify(plugin, MODULES, natives);
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.verify(plugin,
                List.of("native-common", "native-v26_3_R1"), natives));
        Map<String, File> rebuilt = new TreeMap<>(natives);
        rebuilt.put("native-v26_2_R1", provider("rebuilt.jar", "art/arcane/volmlib/util/collection/KMap"));
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.verify(plugin, MODULES, rebuilt));
    }

    @Test
    public void rejectsProviderImplementationsBundledOutsideTheEmbeddedJars() throws Exception {
        Map<String, File> natives = natives();
        File plugin = plugin(natives, Map.of("art/arcane/volmlib/nativelib/v26_2_R1/terrain/Provider.class", new byte[0]));
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.verify(plugin, MODULES, natives));
    }

    @Test
    public void verifiesTheEmbeddedProviderBytesInsideThePackedRuntime() throws Exception {
        Map<String, File> natives = natives();
        Map<String, byte[]> runtime = runtime(natives, natives);
        NativeRuntimeArtifacts.verifyPacked(packed(runtime), MODULES, natives);
        NativeRuntimeArtifacts.verifyPacked(jar("ordinary.jar", runtime), MODULES, natives);
        Map<String, File> rebuilt = new TreeMap<>(natives);
        rebuilt.put("native-v26_2_R1", provider("rebuilt.jar", "art/arcane/volmlib/util/collection/KMap"));
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.verifyPacked(packed(runtime(natives, rebuilt)),
                MODULES, natives));
        Map<String, byte[]> missing = new TreeMap<>(runtime);
        missing.remove(NativeRuntimeArtifacts.RESOURCES + "native-v26_2_R1.jar");
        assertThrows(GradleException.class, () -> NativeRuntimeArtifacts.verifyPacked(packed(missing), MODULES, natives));
    }

    private Map<String, File> natives() throws IOException {
        Map<String, File> natives = new TreeMap<>();
        natives.put("native-common", provider("common.jar", "java/lang/Object"));
        natives.put("native-v26_2_R1", provider("v26_2_R1.jar", "art/arcane/volmlib/util/collection/KList"));
        return natives;
    }

    private File provider(String name, String superclass) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "art/arcane/volmlib/nativelib/v26_2_R1/terrain/Provider", null,
                superclass, null);
        writer.visitEnd();
        return jar(name, Map.of("art/arcane/volmlib/nativelib/v26_2_R1/terrain/Provider.class", writer.toByteArray()));
    }

    private File plugin(Map<String, File> natives, Map<String, byte[]> extra) throws IOException {
        File generated = temporary.newFolder();
        NativeRuntimeArtifacts.generate(natives, MODULES, generated, new File(generated, "keep.pro"));
        Map<String, byte[]> contents = new TreeMap<>(extra);
        contents.put(NativeRuntimeArtifacts.MANIFEST,
                Files.readAllBytes(generated.toPath().resolve(NativeRuntimeArtifacts.MANIFEST)));
        return jar("plugin.jar", contents);
    }

    private Map<String, byte[]> runtime(Map<String, File> described, Map<String, File> embedded) throws IOException {
        File generated = temporary.newFolder();
        NativeRuntimeArtifacts.generate(described, MODULES, generated, new File(generated, "keep.pro"));
        Map<String, byte[]> contents = new TreeMap<>();
        contents.put(NativeRuntimeArtifacts.MANIFEST,
                Files.readAllBytes(generated.toPath().resolve(NativeRuntimeArtifacts.MANIFEST)));
        for (Map.Entry<String, File> provider : embedded.entrySet()) {
            contents.put(NativeRuntimeArtifacts.RESOURCES + provider.getKey() + ".jar",
                    Files.readAllBytes(provider.getValue().toPath()));
        }
        return contents;
    }

    private File packed(Map<String, byte[]> runtime) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (XZOutputStream xz = new XZOutputStream(compressed, new LZMA2Options(1))) {
            xz.write(Files.readAllBytes(jar("payload.jar", runtime).toPath()));
        }
        Map<String, byte[]> outer = new TreeMap<>();
        outer.put(NativeRuntimeArtifacts.MANIFEST, runtime.get(NativeRuntimeArtifacts.MANIFEST));
        outer.put("META-INF/volmit/runtime.jar.xz", compressed.toByteArray());
        return jar("packed.jar", outer);
    }

    private File jar(String name, Map<String, byte[]> contents) throws IOException {
        Path file = temporary.newFolder().toPath().resolve(name);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return file.toFile();
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
