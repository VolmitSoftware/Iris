package art.arcane.iris.core.modules;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/** Compiles a pack module's sources against the Iris API on the test classpath into {@code <pack>/modules/<jar>}. */
public final class PackModuleJar {
    private static final String SERVICE_ENTRY = "META-INF/services/com.volmit.IrisModule";

    private PackModuleJar() {
    }

    /** @param sources Java sources by binary class name; {@code entryClass} is the module's entry point */
    public static void write(Path pack, String jarName, String entryClass, Map<String, String> sources) throws IOException {
        Path work = Files.createTempDirectory("iris-pack-module-src");
        Path classes = Files.createDirectories(work.resolve("classes"));
        compile(work, classes, sources);
        Path jar = Files.createDirectories(pack.resolve("modules")).resolve(jarName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addClasses(output, classes);
            output.putNextEntry(new JarEntry(SERVICE_ENTRY));
            output.write((entryClass + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static void compile(Path work, Path classes, Map<String, String> sources) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = work.resolve("src").resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
            files.add(file);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<String> options = List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString());
            boolean compiled = compiler.getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjectsFromPaths(files)).call();
            if (!compiled) {
                throw new IllegalStateException("Module sources do not compile: " + diagnostics.getDiagnostics());
            }
        }
    }

    private static void addClasses(JarOutputStream output, Path classes) throws IOException {
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                output.write(Files.readAllBytes(file));
                output.closeEntry();
            }
        }
    }
}
