import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class PackedJar extends DefaultTask {
    static final String RUNTIME = "art/arcane/iris/platform/bootstrap/PackedRuntime";
    static final String PLUGIN_BASE = "art/arcane/iris/platform/bukkit/plugin/VolmitPlugin";
    static final String PAPER_LOADER = "art/arcane/iris/IrisPluginLoader";
    static final String PAPER_BOOTSTRAP = "art/arcane/iris/IrisBootstrap";
    static final String PAYLOAD = "META-INF/iris/runtime.jar.xz";
    static final String CHECKSUM = "META-INF/iris/runtime.sha256";
    static final String NATIVE_DIRECTORY = "META-INF/iris/native/";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getBootstrapClasses();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getDecoderJars();

    @Internal
    public abstract MapProperty<String, File> getNativeArtifacts();

    @Input
    public List<String> getNativeModules() {
        return getNativeArtifacts().get().keySet().stream().sorted().toList();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public Collection<File> getNativeProviderJars() {
        return getNativeArtifacts().get().values();
    }

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void pack() throws IOException, NoSuchAlgorithmException {
        Map<String, byte[]> bootstrap = new TreeMap<>();
        for (File directory : getBootstrapClasses()) {
            try (Stream<Path> paths = Files.walk(directory.toPath())) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    bootstrap.put(directory.toPath().relativize(path).toString().replace(File.separatorChar, '/'),
                            Files.readAllBytes(path));
                }
            }
        }
        for (File decoder : getDecoderJars()) {
            for (Map.Entry<String, byte[]> entry : readJar(decoder.toPath()).entrySet()) {
                if (entry.getKey().startsWith("org/tukaani/xz/") && entry.getKey().endsWith(".class")) {
                    bootstrap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (!bootstrap.containsKey(RUNTIME + ".class")) {
            throw new IOException("Packed runtime bootstrap class is missing.");
        }
        Path source = getInputJar().get().getAsFile().toPath();
        Path output = getOutputJar().get().getAsFile().toPath();
        PackResult result = writePackedJar(source, output, bootstrap, getNativeArtifacts().get());
        getLogger().lifecycle("Packed {} classes into XZ; retained {} bootstrap classes. {} -> {} bytes",
                result.totalClasses(), result.bootstrapClasses(), Files.size(source), Files.size(output));
    }

    static PackResult writePackedJar(Path source, Path output, Map<String, byte[]> bootstrap,
                                     Map<String, File> nativeArtifacts)
            throws IOException, NoSuchAlgorithmException {
        Map<String, byte[]> contents = readJar(source);
        embedNativeArtifacts(contents, nativeArtifacts);
        int classCount = (int) contents.keySet().stream().filter(name -> name.endsWith(".class")).count();
        for (String name : List.of(PLUGIN_BASE, PAPER_LOADER, PAPER_BOOTSTRAP)) {
            byte[] original = contents.get(name + ".class");
            if (original == null) {
                throw new IOException("Required bootstrap entry is missing: " + name);
            }
            contents.put(name + ".class", instrument(original, name.equals(PLUGIN_BASE)));
        }
        Set<String> retained = bootstrapClosure(contents);
        contents.putAll(bootstrap);
        byte[] payload = writeJar(contents, true);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        LZMA2Options options = new LZMA2Options(9);
        options.setDictSize(Math.min(options.getDictSize(), Math.max(4096, payload.length)));
        try (XZOutputStream xz = new XZOutputStream(compressed, options)) {
            xz.write(payload);
        }
        Map<String, byte[]> outer = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
            if (entry.getKey().startsWith(NATIVE_DIRECTORY)) {
                continue;
            }
            if (entry.getKey().endsWith(".class")) {
                if (!retained.contains(entry.getKey())) {
                    continue;
                }
            }
            outer.put(entry.getKey(), entry.getValue());
        }
        outer.putAll(bootstrap);
        outer.put(PAYLOAD, compressed.toByteArray());
        outer.put(CHECKSUM, (HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)) + "\n")
                .getBytes(StandardCharsets.US_ASCII));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, writeJar(outer, false));
        return new PackResult(classCount, retained.size());
    }

    public static Map<String, File> resolveNativeArtifacts(Map<ComponentIdentifier, File> resolved) {
        Map<String, File> artifacts = new TreeMap<>();
        for (Map.Entry<ComponentIdentifier, File> entry : resolved.entrySet()) {
            String module;
            if (entry.getKey() instanceof ModuleComponentIdentifier component
                    && component.getGroup().equals("com.github.VolmitSoftware.VolmLib")) {
                module = component.getModule();
            } else if (entry.getKey() instanceof ProjectComponentIdentifier component) {
                module = component.getProjectPath().substring(1).replace(':', '-');
            } else {
                throw new GradleException("Unexpected native provider component: " + entry.getKey().getDisplayName());
            }
            if (!module.matches("native-(common|v[0-9_]+R[0-9]+)")) {
                throw new GradleException("Unexpected native provider module: " + module);
            }
            if (artifacts.put(module, entry.getValue()) != null) {
                throw new GradleException("Duplicate native provider module: " + module);
            }
        }
        return artifacts;
    }

    private static void embedNativeArtifacts(Map<String, byte[]> contents, Map<String, File> artifacts)
            throws IOException, NoSuchAlgorithmException {
        byte[] original = contents.get(NativeRuntimeArtifacts.MANIFEST);
        if (original == null) {
            throw new IOException("Missing native runtime dependency manifest");
        }
        Properties manifest = new Properties();
        manifest.load(new ByteArrayInputStream(original));
        Set<String> modules = new TreeSet<>(List.of(manifest.getProperty("modules", "").split(",")));
        if (!modules.equals(artifacts.keySet()) || !modules.contains("native-common")) {
            throw new IOException("Embedded native providers differ: " + artifacts.keySet() + "; expected " + modules);
        }
        List<String> embeddedManifest = new ArrayList<>();
        embeddedManifest.add("modules=" + String.join(",", modules));
        embeddedManifest.add("storage=embedded");
        for (String module : modules) {
            if (!module.matches("native-(common|v[0-9_]+R[0-9]+)")) {
                throw new IOException("Invalid embedded native provider module: " + module);
            }
            byte[] bytes = Files.readAllBytes(artifacts.get(module).toPath());
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            embeddedManifest.add(module + ".coordinate=com.github.VolmitSoftware.VolmLib:" + module + ":embedded-" + digest);
            embeddedManifest.add(module + ".sha256=" + digest);
            contents.put(NATIVE_DIRECTORY + module + ".jar", bytes);
        }
        contents.put(NativeRuntimeArtifacts.MANIFEST,
                (String.join("\n", embeddedManifest) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, byte[]> readJar(Path path) throws IOException {
        Map<String, byte[]> contents = new TreeMap<>();
        try (ZipFile jar = new ZipFile(path.toFile())) {
            for (ZipEntry entry : jar.stream().filter(entry -> !entry.isDirectory()).toList()) {
                try (InputStream input = jar.getInputStream(entry)) {
                    contents.put(entry.getName(), input.readAllBytes());
                }
            }
        }
        return contents;
    }

    static byte[] writeJar(Map<String, byte[]> contents, boolean stored) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            jar.setLevel(9);
            for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
                JarEntry zipEntry = new JarEntry(entry.getKey());
                zipEntry.setTime(0L);
                if (stored || entry.getKey().equals(PAYLOAD)) {
                    CRC32 crc = new CRC32();
                    crc.update(entry.getValue());
                    zipEntry.setMethod(ZipEntry.STORED);
                    zipEntry.setSize(entry.getValue().length);
                    zipEntry.setCompressedSize(entry.getValue().length);
                    zipEntry.setCrc(crc.getValue());
                }
                jar.putNextEntry(zipEntry);
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    static Set<String> bootstrapClosure(Map<String, byte[]> contents) {
        List<String> roots = List.of("art/arcane/iris/Iris", PLUGIN_BASE, PAPER_LOADER,
                PAPER_BOOTSTRAP, "art/arcane/volmlib/integration/ReloadAware");
        ArrayDeque<String> pending = new ArrayDeque<>(roots);
        for (String root : roots) {
            byte[] bytes = contents.get(root + ".class");
            if (bytes != null) {
                pending.addAll(ClassReferences.read(bytes));
            }
        }
        for (String entry : contents.keySet()) {
            if (entry.startsWith("art/arcane/iris/util/slimjar/") && entry.endsWith(".class")) {
                pending.add(entry.substring(0, entry.length() - 6));
            }
        }
        Set<String> retained = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            byte[] bytes = contents.get(name + ".class");
            if (bytes == null || !retained.add(name + ".class")) {
                continue;
            }
            ClassReader reader = new ClassReader(bytes);
            if (reader.getSuperName() != null) {
                pending.add(reader.getSuperName());
            }
            for (String implemented : reader.getInterfaces()) {
                pending.add(implemented);
            }
        }
        return retained;
    }

    static byte[] instrument(byte[] original, boolean rewriteJarAccessor) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            private boolean hasInitializer;

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (reader.getClassName().equals(PAPER_LOADER) && name.equals("classloader")
                        && descriptor.equals("(Lio/papermc/paper/plugin/loader/PluginClasspathBuilder;)V")) {
                    return releaseTemporaryLoader(method);
                }
                if (rewriteJarAccessor && name.equals("getJarFile") && descriptor.equals("()Ljava/io/File;")) {
                    method.visitCode();
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, "runtimeJar", "()Ljava/io/File;", false);
                    method.visitInsn(Opcodes.ARETURN);
                    method.visitMaxs(1, 1);
                    method.visitEnd();
                    return null;
                }
                if (!name.equals("<clinit>")) {
                    return method;
                }
                hasInitializer = true;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, "install", "()V", false);
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (!hasInitializer) {
                    MethodVisitor initializer = super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                    initializer.visitCode();
                    initializer.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, "install", "()V", false);
                    initializer.visitInsn(Opcodes.RETURN);
                    initializer.visitMaxs(0, 0);
                    initializer.visitEnd();
                }
                super.visitEnd();
            }
        }, 0);
        return writer.toByteArray();
    }

    private static MethodVisitor releaseTemporaryLoader(MethodVisitor method) {
        return new MethodVisitor(Opcodes.ASM9, method) {
            private final Label start = new Label();
            private final Label end = new Label();
            private final Label handler = new Label();
            private final Label completed = new Label();

            @Override
            public void visitCode() {
                super.visitCode();
                super.visitLabel(start);
            }

            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.RETURN) {
                    super.visitJumpInsn(Opcodes.GOTO, completed);
                } else {
                    super.visitInsn(opcode);
                }
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                super.visitLabel(end);
                super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
                super.visitLabel(handler);
                super.visitFrame(Opcodes.F_FULL, 0, null, 1, new Object[]{"java/lang/Throwable"});
                super.visitInsn(Opcodes.DUP);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, "releaseTemporary", "(Ljava/lang/Throwable;)V", false);
                super.visitInsn(Opcodes.ATHROW);
                super.visitLabel(completed);
                super.visitFrame(Opcodes.F_FULL, 0, null, 0, null);
                super.visitInsn(Opcodes.ACONST_NULL);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, "releaseTemporary", "(Ljava/lang/Throwable;)V", false);
                super.visitInsn(Opcodes.RETURN);
                super.visitMaxs(Math.max(maxStack, 2), maxLocals);
            }
        };
    }

    record PackResult(int totalClasses, int bootstrapClasses) {
    }
}
