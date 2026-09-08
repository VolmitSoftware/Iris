import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedArtifactVerifierTest {
    private static final String METADATA = "fabric.mod.json";
    private static final String CODEC_CLASS = "art/arcane/volmlib/util/mantle/io/Lz4IOWorkerCodecSupport.class";
    private static final String HARDWARE_CLASS = "art/arcane/iris/util/common/misc/getHardware.class";
    private static final String MIXIN_CONFIG = "irisworldgen.entity.mixins.json";
    private static final String CLIENT_MIXIN_CONFIG = "irisworldgen.client.mixins.json";
    private static final String NEOFORGE_METADATA = "META-INF/neoforge.mods.toml";
    private static final List<String> REQUIRED_ENTRIES = List.of(METADATA, CODEC_CLASS, HARDWARE_CLASS);
    private static final byte[] INTERNAL_CODEC = (
            "net/jpountz/lz4/LZ4BlockInputStream net/jpountz/lz4/LZ4BlockOutputStream"
    ).getBytes(StandardCharsets.ISO_8859_1);

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsMinecraftRuntimeReferences() throws Exception {
        Map<String, byte[]> entries = validEntries();
        File artifact = createArtifact(entries);

        ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES);
    }

    @Test
    public void rejectsRelocatedRuntimeReference() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/Test.class",
                "art/arcane/iris/shadow/jpountz/lz4/LZ4BlockInputStream"
                        .getBytes(StandardCharsets.ISO_8859_1));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("Iris-private runtime references"));
    }

    @Test
    public void rejectsBundledMinecraftRuntimeLibrary() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("net/jpountz/lz4/LZ4BlockInputStream.class", new byte[]{0});
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("duplicates Minecraft runtime libraries"));
    }

    @Test
    public void rejectsNestedMinecraftRuntimeLibrary() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("META-INF/jars/lz4-java.jar", new byte[]{0});
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("duplicates Minecraft runtime libraries"));
    }

    @Test
    public void rejectsRenamedJarJarRuntimeLibrary() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("META-INF/jarjar/runtime-support.jar", createNestedArtifact(Map.of(
                "com/sun/jna/Native.class", new byte[]{0}
        )));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("duplicates Minecraft runtime libraries"));
    }

    @Test
    public void rejectsArtifactWithoutPlatformMetadata() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(CODEC_CLASS, INTERNAL_CODEC);
        entries.put(HARDWARE_CLASS, "oshi/SystemInfo".getBytes(StandardCharsets.ISO_8859_1));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("is missing " + METADATA));
    }

    @Test
    public void rejectsUnrelocatedAsm() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("org/objectweb/asm/ClassReader.class", new byte[]{0});
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("ships unrelocated ASM"));
    }

    @Test
    public void rejectsNestedJarMissingFromMetadata() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("META-INF/jars/fabric-rendering-v1.jar", createNestedArtifact(Map.of(
                "net/fabricmc/Placeholder.class", new byte[]{0}
        )));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("does not declare"));
    }

    @Test
    public void rejectsDeclaredNestedJarThatIsNotShipped() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put(METADATA, fabricMetadata(
                "[ { \"file\": \"META-INF/jars/fabric-api-base.jar\" } ]",
                "[ \"" + MIXIN_CONFIG + "\" ]"));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("does not ship"));
    }

    @Test
    public void acceptsNestedJarDeclaredInMetadata() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put(METADATA, fabricMetadata(
                "[ { \"file\": \"META-INF/jars/fabric-api-base.jar\" } ]",
                "[ \"" + MIXIN_CONFIG + "\" ]"));
        entries.put("META-INF/jars/fabric-api-base.jar", createNestedArtifact(Map.of(
                "net/fabricmc/Placeholder.class", new byte[]{0}
        )));
        File artifact = createArtifact(entries);

        ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES);
    }

    @Test
    public void rejectsMixinCompatibilityLevelAboveJava21() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put(MIXIN_CONFIG, mixinConfig("JAVA_25"));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("compatibilityLevel JAVA_25"));
    }

    @Test
    public void rejectsMixinCompatibilityLevelAboveJava21RegisteredByManifest() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/mods.toml", "modId = \"irisworldgen\"".getBytes(StandardCharsets.UTF_8));
        entries.put(CODEC_CLASS, INTERNAL_CODEC);
        entries.put(HARDWARE_CLASS, "oshi/SystemInfo".getBytes(StandardCharsets.ISO_8859_1));
        entries.put(CLIENT_MIXIN_CONFIG, mixinConfig("JAVA_25"));
        File artifact = createArtifact(entries, manifestWithMixinConfigs(CLIENT_MIXIN_CONFIG));

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, List.of(CODEC_CLASS, HARDWARE_CLASS)));
        assertTrue(failure.getMessage().contains("compatibilityLevel JAVA_25"));
    }

    @Test
    public void rejectsMixinCompatibilityLevelAboveJava21RegisteredByNeoforgeToml() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(NEOFORGE_METADATA, ("[[mixins]]\nconfig = \"" + CLIENT_MIXIN_CONFIG + "\"\n")
                .getBytes(StandardCharsets.UTF_8));
        entries.put(CODEC_CLASS, INTERNAL_CODEC);
        entries.put(HARDWARE_CLASS, "oshi/SystemInfo".getBytes(StandardCharsets.ISO_8859_1));
        entries.put(CLIENT_MIXIN_CONFIG, mixinConfig("JAVA_25"));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, List.of(CODEC_CLASS, HARDWARE_CLASS)));
        assertTrue(failure.getMessage().contains("compatibilityLevel JAVA_25"));
    }

    @Test
    public void rejectsRegisteredMixinConfigThatIsNotShipped() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.remove(MIXIN_CONFIG);
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("which is not in the jar"));
    }

    @Test
    public void rejectsArtifactWithoutMixinRegistration() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put(METADATA, fabricMetadata("[ ]", "[ ]"));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("registers no mixin configs"));
    }

    @Test
    public void rejectsBukkitSupertypeOutsidePlatformPackage() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/engine/platform/BukkitChunkGenerator.class",
                classExtending("art/arcane/iris/engine/platform/BukkitChunkGenerator",
                        "org/bukkit/generator/ChunkGenerator"));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("org.bukkit supertype"));
    }

    @Test
    public void rejectsBukkitInterfaceOutsidePlatformPackage() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/Listener.class",
                classImplementing("art/arcane/iris/Listener", "org/bukkit/event/Listener"));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES));
        assertTrue(failure.getMessage().contains("org.bukkit supertype"));
    }

    @Test
    public void acceptsBukkitSupertypeInsidePlatformPackage() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/platform/bukkit/BukkitWorld.class",
                classImplementing("art/arcane/iris/platform/bukkit/BukkitWorld", "org/bukkit/event/Listener"));
        File artifact = createArtifact(entries);

        ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES);
    }

    @Test
    public void acceptsBukkitSupertypeDeclaredInThePurityAllowlist() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/engine/platform/BukkitChunkGenerator.class",
                classExtending("art/arcane/iris/engine/platform/BukkitChunkGenerator",
                        "org/bukkit/generator/ChunkGenerator"));
        File artifact = createArtifact(entries);

        ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES,
                Set.of("art/arcane/iris/engine/platform/BukkitChunkGenerator"));
    }

    @Test
    public void acceptsBukkitSupertypeOnNestedClassOfAllowlistedSource() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/engine/object/IrisEntity$BukkitOps$1.class",
                classImplementing("art/arcane/iris/engine/object/IrisEntity$BukkitOps$1",
                        "org/bukkit/event/Listener"));
        File artifact = createArtifact(entries);

        ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES,
                Set.of("art/arcane/iris/engine/object/IrisEntity"));
    }

    @Test
    public void acceptsBukkitSupertypeFromSharedLibrary() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/volmlib/util/bukkit/Events.class",
                classImplementing("art/arcane/volmlib/util/bukkit/Events", "org/bukkit/event/Listener"));
        File artifact = createArtifact(entries);

        ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES);
    }

    @Test
    public void rejectsBukkitSupertypeMissingFromThePurityAllowlist() throws Exception {
        Map<String, byte[]> entries = validEntries();
        entries.put("art/arcane/iris/core/link/data/OraxenDataProvider.class",
                classImplementing("art/arcane/iris/core/link/data/OraxenDataProvider",
                        "org/bukkit/event/Listener"));
        File artifact = createArtifact(entries);

        GradleException failure = assertThrows(GradleException.class,
                () -> ModdedArtifactVerifier.verify(artifact, REQUIRED_ENTRIES,
                        Set.of("art/arcane/iris/core/link/data/ItemAdderDataProvider")));
        assertTrue(failure.getMessage().contains("core/purity-allowlist.txt"));
    }

    private Map<String, byte[]> validEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(METADATA, fabricMetadata("[ ]", "[ \"" + MIXIN_CONFIG + "\" ]"));
        entries.put(MIXIN_CONFIG, mixinConfig("JAVA_21"));
        entries.put(CODEC_CLASS, INTERNAL_CODEC);
        entries.put(HARDWARE_CLASS, "oshi/SystemInfo".getBytes(StandardCharsets.ISO_8859_1));
        return entries;
    }

    private byte[] fabricMetadata(String jarsArray, String mixinsArray) {
        return ("{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"id\": \"irisworldgen\",\n"
                + "  \"mixins\": " + mixinsArray + ",\n"
                + "  \"jars\": " + jarsArray + "\n"
                + "}\n").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] mixinConfig(String compatibilityLevel) {
        return ("{\n"
                + "  \"required\": true,\n"
                + "  \"minVersion\": \"0.8\",\n"
                + "  \"package\": \"art.arcane.iris.modded.mixin\",\n"
                + "  \"compatibilityLevel\": \"" + compatibilityLevel + "\"\n"
                + "}\n").getBytes(StandardCharsets.UTF_8);
    }

    private Manifest manifestWithMixinConfigs(String configs) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("MixinConfigs", configs);
        return manifest;
    }

    private byte[] classExtending(String internalName, String superName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, superName, null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private byte[] classImplementing(String internalName, String interfaceName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object",
                new String[]{interfaceName});
        writer.visitEnd();
        return writer.toByteArray();
    }

    private File createArtifact(Map<String, byte[]> entries) throws Exception {
        return createArtifact(entries, null);
    }

    private File createArtifact(Map<String, byte[]> entries, Manifest manifest) throws Exception {
        File artifact = temporaryFolder.newFile("artifact-" + System.nanoTime() + ".jar");
        try (FileOutputStream file = new FileOutputStream(artifact);
             JarOutputStream output = manifest == null
                     ? new JarOutputStream(file)
                     : new JarOutputStream(file, manifest)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return artifact;
    }

    private byte[] createNestedArtifact(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream output = new JarOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
