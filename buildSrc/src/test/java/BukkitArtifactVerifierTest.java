import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BukkitArtifactVerifierTest {
    private static final String PLUGIN_DESCRIPTOR = "plugin.yml";
    private static final String SLIMJAR_DEPENDENCIES = "slimjar.dat";
    private static final String SLIMJAR_RESOLUTIONS = "slimjar-resolutions.dat";
    private static final String NMS_BINDING = "art/arcane/iris/core/nms/v26_2_R1/NMSBinding";
    private static final List<String> REQUIRED_ENTRIES = List.of(
            PLUGIN_DESCRIPTOR,
            SLIMJAR_DEPENDENCIES,
            SLIMJAR_RESOLUTIONS,
            NMS_BINDING + ".class"
    );
    private static final int MATTER_SLICES = 1;
    private static final List<String> RUNTIME_DOWNLOADED_PREFIXES = List.of(
            "art/arcane/iris/util/kyori/",
            "art/arcane/iris/util/gson/",
            "art/arcane/iris/util/lru/",
            "art/arcane/iris/util/caffeine/",
            "art/arcane/iris/util/paralithic/"
    );

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsCompleteArtifact() throws Exception {
        File artifact = createArtifact(validEntries());

        BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES, RUNTIME_DOWNLOADED_PREFIXES);
    }

    @Test
    public void rejectsMissingRequiredEntry() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.remove(PLUGIN_DESCRIPTOR);
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES, RUNTIME_DOWNLOADED_PREFIXES));
        assertTrue(failure.getMessage().contains(PLUGIN_DESCRIPTOR));
    }

    @Test
    public void rejectsMissingSlimJarDependencyManifest() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.remove(SLIMJAR_DEPENDENCIES);
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES, RUNTIME_DOWNLOADED_PREFIXES));
        assertTrue(failure.getMessage().contains(SLIMJAR_DEPENDENCIES));
    }

    @Test
    public void rejectsMissingSlimJarResolutionManifest() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.remove(SLIMJAR_RESOLUTIONS);
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES, RUNTIME_DOWNLOADED_PREFIXES));
        assertTrue(failure.getMessage().contains(SLIMJAR_RESOLUTIONS));
    }

    @Test
    public void rejectsExcludedClassThatIsStillReferenced() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.remove("art/arcane/volmlib/util/noise/CNG.class");
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES, RUNTIME_DOWNLOADED_PREFIXES));
        assertTrue(failure.getMessage().contains("art/arcane/volmlib/util/noise/CNG"));
    }

    @Test
    public void acceptsReferenceToRuntimeDownloadedLibrary() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/Consumer.class",
                classReferencing("art/arcane/iris/Consumer", "art/arcane/iris/util/kyori/adventure/text/Component"));
        entries.put("art/arcane/iris/GsonConsumer.class",
                classReferencing("art/arcane/iris/GsonConsumer", "art/arcane/iris/util/gson/Gson"));
        entries.put("art/arcane/iris/LruConsumer.class",
                classReferencing("art/arcane/iris/LruConsumer", "art/arcane/iris/util/lru/ConcurrentLinkedHashMap"));
        entries.put("art/arcane/iris/CaffeineConsumer.class",
                classReferencing("art/arcane/iris/CaffeineConsumer", "art/arcane/iris/util/caffeine/cache/Caffeine"));
        entries.put("art/arcane/iris/ParalithicConsumer.class",
                classReferencing("art/arcane/iris/ParalithicConsumer", "art/arcane/iris/util/paralithic/functions/Function"));
        File artifact = createArtifact(entries);

        BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES, RUNTIME_DOWNLOADED_PREFIXES);
    }

    @Test
    public void rejectsReferenceOutsideRuntimeDownloadedPrefixes() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/PaperConsumer.class",
                classReferencing("art/arcane/iris/PaperConsumer", "art/arcane/iris/util/paper/PaperLib"));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES,
                        RUNTIME_DOWNLOADED_PREFIXES));
        assertTrue(failure.getMessage().contains("art/arcane/iris/util/paper/PaperLib"));
    }

    @Test
    public void rejectsEmbeddedLocale() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("languages/de_DE.json", "{}".getBytes(StandardCharsets.UTF_8));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES, RUNTIME_DOWNLOADED_PREFIXES));
        assertTrue(failure.getMessage().contains("locale files"));
    }

    @Test
    public void rejectsDroppedMatterSlice() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.remove("art/arcane/volmlib/util/matter/slices/BlockMatter.class");
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES, RUNTIME_DOWNLOADED_PREFIXES));
        assertTrue(failure.getMessage().contains("Matter slice types"));
    }

    @Test
    public void ignoresAnnotationOnlyReferences() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/Annotated.class",
                classAnnotatedWith("art/arcane/iris/Annotated", "com/google/errorprone/annotations/CanIgnoreReturnValue"));
        File artifact = createArtifact(entries);

        BukkitArtifactVerifier.verify(artifact, REQUIRED_ENTRIES, MATTER_SLICES, RUNTIME_DOWNLOADED_PREFIXES);
    }

    private Map<String, byte[]> validEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(PLUGIN_DESCRIPTOR, "name: Iris\n".getBytes(StandardCharsets.UTF_8));
        entries.put(SLIMJAR_DEPENDENCIES, new byte[]{1});
        entries.put(SLIMJAR_RESOLUTIONS, new byte[]{1});

        entries.put(NMS_BINDING + ".class",
                classReferencing(NMS_BINDING, "art/arcane/volmlib/util/noise/CNG"));
        entries.put("art/arcane/volmlib/util/noise/CNG.class", emptyClass("art/arcane/volmlib/util/noise/CNG"));
        entries.put("art/arcane/volmlib/util/matter/slices/BlockMatter.class",
                emptyClass("art/arcane/volmlib/util/matter/slices/BlockMatter"));
        return entries;
    }

    private static byte[] emptyClass(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classReferencing(String internalName, String referenced) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", "()V", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, referenced);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classAnnotatedWith(String internalName, String annotation) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        writer.visitAnnotation('L' + annotation + ';', true).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private File createArtifact(Map<String, byte[]> entries) throws Exception {
        File artifact = temporaryFolder.newFile("Iris-bukkit-" + System.nanoTime() + ".jar");
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(artifact))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return artifact;
    }
}
