import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PackedJarTest {
    @Rule
    public TemporaryFolder directory = new TemporaryFolder();

    @Test
    public void preservesAllRuntimeEntriesAndPublishesTheirChecksum() throws Exception {
        Path original = directory.newFile("original.jar").toPath();
        Path packed = directory.getRoot().toPath().resolve("packed.jar");
        Map<String, byte[]> contents = new LinkedHashMap<>();
        contents.put(PackedJar.PLUGIN_BASE + ".class", pluginBase());
        contents.put(PackedJar.PAPER_LOADER + ".class", simpleClass(PackedJar.PAPER_LOADER, "java/lang/Object"));
        contents.put(PackedJar.PAPER_BOOTSTRAP + ".class", simpleClass(PackedJar.PAPER_BOOTSTRAP, "java/lang/Object"));
        contents.put("art/arcane/iris/command/Example.class", simpleClass("art/arcane/iris/command/Example", "java/lang/Object"));
        contents.put("plugin.yml", "main: art.arcane.iris.Iris\n".getBytes(StandardCharsets.UTF_8));
        List<String> modules = List.of("native-common", "native-v26_2_R1", "native-v26_3_R1");
        contents.put(NativeRuntimeArtifacts.MANIFEST,
                ("modules=" + String.join(",", modules) + "\nrepository=http://127.0.0.1:8765/\n")
                        .getBytes(StandardCharsets.UTF_8));
        Map<String, File> nativeArtifacts = new LinkedHashMap<>();
        for (String module : modules) {
            Path artifact = directory.newFile(module + ".jar").toPath();
            Files.write(artifact, PackedJar.writeJar(Map.of("provider.txt", module.getBytes(StandardCharsets.UTF_8)), false));
            nativeArtifacts.put(module, artifact.toFile());
        }
        Files.write(original, PackedJar.writeJar(contents, false));

        Map<String, byte[]> bootstrap = Map.of(
                PackedJar.RUNTIME + ".class", simpleClass(PackedJar.RUNTIME, "java/lang/Object"),
                PackedJar.RUNTIME + "$RuntimeArchive.class", simpleClass(PackedJar.RUNTIME + "$RuntimeArchive", "java/lang/Object"),
                "org/tukaani/xz/XZInputStream.class", simpleClass("org/tukaani/xz/XZInputStream", "java/lang/Object"));
        PackedJar.PackResult result = PackedJar.writePackedJar(original, packed, bootstrap, nativeArtifacts);
        assertEquals(4, result.totalClasses());
        Map<String, byte[]> outer = PackedJar.readJar(packed);
        assertFalse(outer.containsKey("art/arcane/iris/command/Example.class"));
        assertArrayEquals(contents.get("plugin.yml"), outer.get("plugin.yml"));
        byte[] payload;
        try (XZInputStream xz = new XZInputStream(new ByteArrayInputStream(outer.get(PackedJar.PAYLOAD)))) {
            payload = xz.readAllBytes();
        }
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)),
                new String(outer.get(PackedJar.CHECKSUM), StandardCharsets.US_ASCII).trim());
        Map<String, byte[]> extracted = new LinkedHashMap<>();
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(payload))) {
            for (JarEntry entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                assertEquals(ZipEntry.STORED, entry.getMethod());
                extracted.put(entry.getName(), jar.readAllBytes());
            }
        }
        Map<String, byte[]> expected = new LinkedHashMap<>(contents);
        expected.putAll(bootstrap);
        for (String module : modules) {
            expected.put(PackedJar.NATIVE_DIRECTORY + module + ".jar", Files.readAllBytes(nativeArtifacts.get(module).toPath()));
        }
        assertEquals(expected.keySet(), extracted.keySet());
        Properties nativeManifest = new Properties();
        nativeManifest.load(new ByteArrayInputStream(extracted.get(NativeRuntimeArtifacts.MANIFEST)));
        assertEquals(String.join(",", modules), nativeManifest.getProperty("modules"));
        assertEquals("embedded", nativeManifest.getProperty("storage"));
        assertNull(nativeManifest.getProperty("repository"));
        assertFalse(new String(extracted.get(NativeRuntimeArtifacts.MANIFEST), StandardCharsets.UTF_8).contains("127.0.0.1"));
        assertArrayEquals(extracted.get(NativeRuntimeArtifacts.MANIFEST), outer.get(NativeRuntimeArtifacts.MANIFEST));
        for (String module : modules) {
            String path = PackedJar.NATIVE_DIRECTORY + module + ".jar";
            byte[] artifact = Files.readAllBytes(nativeArtifacts.get(module).toPath());
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
            assertArrayEquals(artifact, extracted.get(path));
            assertFalse(outer.containsKey(path));
            assertEquals(digest, nativeManifest.getProperty(module + ".sha256"));
            assertEquals("com.github.VolmitSoftware.VolmLib:" + module + ":embedded-" + digest,
                    nativeManifest.getProperty(module + ".coordinate"));
        }
        for (String name : bootstrap.keySet()) {
            assertArrayEquals(bootstrap.get(name), extracted.get(name));
            assertArrayEquals(bootstrap.get(name), outer.get(name));
        }
        for (String name : contents.keySet()) {
            if (!name.equals(NativeRuntimeArtifacts.MANIFEST)
                    && !List.of(PackedJar.PLUGIN_BASE, PackedJar.PAPER_LOADER, PackedJar.PAPER_BOOTSTRAP).contains(
                    name.replace(".class", ""))) {
                assertArrayEquals(contents.get(name), extracted.get(name));
            }
        }
        for (String name : List.of(PackedJar.PLUGIN_BASE, PackedJar.PAPER_LOADER, PackedJar.PAPER_BOOTSTRAP)) {
            assertArrayEquals(extracted.get(name + ".class"), outer.get(name + ".class"));
            assertEquals(1, installationCalls(extracted.get(name + ".class")));
        }
    }

    @Test
    public void rejectsMissingOrUnexpectedEmbeddedNativeProviders() throws Exception {
        Path original = directory.newFile("original.jar").toPath();
        Path packed = directory.getRoot().toPath().resolve("packed.jar");
        byte[] manifest = "modules=native-common,native-v26_2_R1,native-v26_3_R1\n".getBytes(StandardCharsets.UTF_8);
        Files.write(original, PackedJar.writeJar(Map.of(NativeRuntimeArtifacts.MANIFEST, manifest), false));
        File common = directory.newFile("common.jar");
        assertThrows(IOException.class, () -> PackedJar.writePackedJar(original, packed, Map.of(),
                Map.of("native-common", common, "native-v26_2_R1", common)));
        assertThrows(IOException.class, () -> PackedJar.writePackedJar(original, packed, Map.of(),
                Map.of("native-common", common, "native-v26_2_R1", common, "native-v26_3_R1", common, "native-other", common)));
        assertFalse(Files.exists(packed));
    }

    @Test
    public void resolvesCompositeNativeModulesWithoutParsingArtifactFileNames() {
        File common = new File("arbitrary-common.jar");
        File provider = new File("arbitrary-provider.jar");
        assertEquals(Map.of("native-common", common, "native-v26_2_R1", provider),
                PackedJar.resolveNativeArtifacts(Map.of(project(":native-common"), common,
                        project(":native:v26_2_R1"), provider)));
    }

    private static ProjectComponentIdentifier project(String path) {
        return (ProjectComponentIdentifier) Proxy.newProxyInstance(PackedJarTest.class.getClassLoader(),
                new Class<?>[]{ProjectComponentIdentifier.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getProjectPath", "getDisplayName", "toString" -> path;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    public void installsBeforeExistingInitializationAndRedirectsTheScannerJar() {
        List<String> instructions = new ArrayList<>();
        new ClassReader(PackedJar.instrument(pluginBase(), true)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor,
                                                boolean isInterface) {
                        instructions.add(name + ":" + owner + "." + methodName);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        instructions.add(name + ":" + value);
                    }
                };
            }
        }, 0);
        assertEquals(List.of("<clinit>:" + PackedJar.RUNTIME + ".install", "<clinit>:existing",
                "getJarFile:" + PackedJar.RUNTIME + ".runtimeJar"), instructions);
    }

    @Test
    public void retainsEntrypointReferencesAndHierarchyWithoutRecursiveMemberReferences() {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        ClassWriter iris = new ClassWriter(0);
        iris.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "art/arcane/iris/Iris", null, "example/Base", null);
        iris.visitField(Opcodes.ACC_PUBLIC, "field", "Lexample/Signature;", null, null).visitEnd();
        MethodVisitor method = iris.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "later", "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "example/Body", "later", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        iris.visitEnd();
        contents.put("art/arcane/iris/Iris.class", iris.toByteArray());
        for (String name : List.of("example/Base", "example/Signature", "example/Body")) {
            contents.put(name + ".class", simpleClass(name, "java/lang/Object"));
        }
        ClassWriter signature = new ClassWriter(0);
        signature.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "example/Signature", null, "example/Ancestor", null);
        signature.visitField(Opcodes.ACC_PUBLIC, "field", "Lexample/Chained;", null, null).visitEnd();
        signature.visitEnd();
        contents.put("example/Signature.class", signature.toByteArray());
        contents.put("example/Ancestor.class", simpleClass("example/Ancestor", "java/lang/Object"));
        contents.put("example/Chained.class", simpleClass("example/Chained", "java/lang/Object"));
        Set<String> retained = PackedJar.bootstrapClosure(contents);
        assertTrue(retained.contains("example/Base.class"));
        assertTrue(retained.contains("example/Signature.class"));
        assertTrue(retained.contains("example/Body.class"));
        assertTrue(retained.contains("example/Ancestor.class"));
        assertFalse(retained.contains("example/Chained.class"));
    }

    @Test
    public void releasesTemporaryLoaderAfterSuccessAndPreservesThrownFailure() throws Exception {
        assertLoaderCleanup(false);
    }

    @Test
    public void releasesTemporaryLoaderWithDiscardedParameterFrameLocals() throws Exception {
        assertLoaderCleanup(true);
    }

    private static void assertLoaderCleanup(boolean discardFrameLocals) throws Exception {
        String builderName = "io/papermc/paper/plugin/loader/PluginClasspathBuilder";
        byte[] original = loaderFixture(builderName);
        if (discardFrameLocals) {
            original = discardFrameLocals(original);
        }
        FixtureLoader fixture = new FixtureLoader(Map.of(
                builderName, simpleClass(builderName, "java/lang/Object"),
                PackedJar.RUNTIME, runtimeFixture(),
                PackedJar.PAPER_LOADER, PackedJar.instrument(original, false)));
        Class<?> runtime = fixture.loadClass(PackedJar.RUNTIME.replace('/', '.'));
        Class<?> loader = fixture.loadClass(PackedJar.PAPER_LOADER.replace('/', '.'));
        Object instance = loader.getConstructor().newInstance();
        Method invoke = loader.getMethod("classloader", fixture.loadClass(builderName.replace('/', '.')));
        invoke.invoke(instance, new Object[]{null});
        assertEquals(1, runtime.getField("releases").getInt(null));
        assertNull(runtime.getField("released").get(null));
        Throwable failure = new IllegalStateException("load failed");
        runtime.getField("failure").set(null, failure);
        try {
            invoke.invoke(instance, new Object[]{null});
            fail("Expected the original loader failure");
        } catch (InvocationTargetException caught) {
            assertSame(failure, caught.getCause());
        }
        assertEquals(2, runtime.getField("releases").getInt(null));
        assertSame(failure, runtime.getField("released").get(null));
    }

    private static byte[] discardFrameLocals(byte[] original) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("classloader")) {
                    return method;
                }
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                        super.visitFrame(Opcodes.F_FULL, 0, null, numStack, stack);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    private static int installationCalls(byte[] bytes) {
        List<String> calls = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor,
                                                boolean isInterface) {
                        if (name.equals("<clinit>") && owner.equals(PackedJar.RUNTIME) && methodName.equals("install")) {
                            calls.add(methodName);
                        }
                    }
                };
            }
        }, 0);
        return calls.size();
    }

    private static byte[] runtimeFixture() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, PackedJar.RUNTIME, null, "java/lang/Object", null);
        for (String field : List.of("failure", "released")) {
            writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, field, "Ljava/lang/Throwable;", null, null).visitEnd();
        }
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "releases", "I", null, null).visitEnd();
        MethodVisitor install = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "install", "()V", null, null);
        install.visitCode();
        install.visitInsn(Opcodes.RETURN);
        install.visitMaxs(0, 0);
        install.visitEnd();
        MethodVisitor release = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "releaseTemporary",
                "(Ljava/lang/Throwable;)V", null, null);
        release.visitCode();
        release.visitVarInsn(Opcodes.ALOAD, 0);
        release.visitFieldInsn(Opcodes.PUTSTATIC, PackedJar.RUNTIME, "released", "Ljava/lang/Throwable;");
        release.visitFieldInsn(Opcodes.GETSTATIC, PackedJar.RUNTIME, "releases", "I");
        release.visitInsn(Opcodes.ICONST_1);
        release.visitInsn(Opcodes.IADD);
        release.visitFieldInsn(Opcodes.PUTSTATIC, PackedJar.RUNTIME, "releases", "I");
        release.visitInsn(Opcodes.RETURN);
        release.visitMaxs(0, 0);
        release.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] loaderFixture(String builderName) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, PackedJar.PAPER_LOADER, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "classloader", "(L" + builderName + ";)V", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, PackedJar.RUNTIME, "failure", "Ljava/lang/Throwable;");
        method.visitInsn(Opcodes.DUP);
        Label success = new Label();
        method.visitJumpInsn(Opcodes.IFNULL, success);
        method.visitInsn(Opcodes.ATHROW);
        method.visitLabel(success);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] simpleClass(String name, String parent) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, name, null, parent, null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] pluginBase() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, PackedJar.PLUGIN_BASE, null, "java/lang/Object", null);
        MethodVisitor initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn("existing");
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(1, 0);
        initializer.visitEnd();
        MethodVisitor accessor = writer.visitMethod(Opcodes.ACC_PUBLIC, "getJarFile", "()Ljava/io/File;", null, null);
        accessor.visitCode();
        accessor.visitInsn(Opcodes.ACONST_NULL);
        accessor.visitInsn(Opcodes.ARETURN);
        accessor.visitMaxs(1, 1);
        accessor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class FixtureLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        private FixtureLoader(Map<String, byte[]> classes) {
            super(PackedJarTest.class.getClassLoader());
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name.replace('.', '/'));
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
