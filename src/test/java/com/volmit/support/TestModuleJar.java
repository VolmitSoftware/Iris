package com.volmit.support;

import com.volmit.IrisModule;
import com.volmit.example.placement.AnchorModule;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Builds a real module jar from Java source at test time. Its classes are not on the test
 * classpath, so only a pack class loader can define them.
 */
public final class TestModuleJar {
    private static final String SERVICES_FILE = "META-INF/services/" + IrisModule.class.getName();

    private final Map<String, String> sources = new LinkedHashMap<>();
    private final List<String> entryPoints = new ArrayList<>();
    private final List<Path> classpath = new ArrayList<>(List.of(classesOf(IrisModule.class), classesOf(AnchorModule.class)));

    /** An entry class with the given module id whose install body may use {@code graph}. */
    public static TestModuleJar module(String className, String moduleId, String members, String install) {
        return new TestModuleJar().source(className, moduleSource(className, moduleId, members, install)).entryPoint(className);
    }

    public TestModuleJar source(String className, String code) {
        sources.put(className, code);
        return this;
    }

    /** Adds one line to the jar's entry point file. */
    public TestModuleJar entryPoint(String className) {
        entryPoints.add(className);
        return this;
    }

    public TestModuleJar compileAgainst(Path jar) {
        classpath.add(jar);
        return this;
    }

    /** Writes {@code <packRoot>/modules/<jarName>} and returns its path. */
    public Path writeInto(Path packRoot, String jarName) {
        try {
            var jar = Files.createDirectories(packRoot.resolve("modules")).resolve(jarName);
            var classes = compile(Files.createTempDirectory(packRoot, "classes"));
            try (var output = new JarOutputStream(Files.newOutputStream(jar), new Manifest())) {
                writeClasses(output, classes);
                writeEntryPoints(output);
            }
            return jar;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Path compile(Path classes) {
        if (sources.isEmpty()) return classes;
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var units = sources.entrySet().stream().map(source -> new StringSource(source.getKey(), source.getValue())).toList();
        var options = List.of("-d", classes.toString(), "-proc:none", "-classpath",
                classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        if (!compiler.getTask(null, null, diagnostics, options, null, units).call()) {
            throw new IllegalStateException("Test module does not compile: " + diagnostics.getDiagnostics());
        }
        return classes;
    }

    private static void writeClasses(JarOutputStream output, Path classes) throws IOException {
        try (var files = Files.walk(classes)) {
            for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
                output.write(Files.readAllBytes(file));
                output.closeEntry();
            }
        }
    }

    private void writeEntryPoints(JarOutputStream output) throws IOException {
        if (entryPoints.isEmpty()) return;
        output.putNextEntry(new JarEntry(SERVICES_FILE));
        output.write(("# entry point\n" + String.join("\n", entryPoints) + "\n").getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static String moduleSource(String className, String moduleId, String members, String install) {
        int dot = className.lastIndexOf('.');
        return """
                package %s;

                import com.volmit.IrisModule;
                import com.volmit.ModuleInfo;
                import com.volmit.stage.*;

                public final class %s implements IrisModule {
                    %s

                    public ModuleInfo info() { return ModuleInfo.of("%s", "1.0.0"); }

                    public void install(StageGraph graph) { %s }
                }
                """.formatted(className.substring(0, dot), className.substring(dot + 1), members, moduleId, install);
    }

    private static Path classesOf(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String code;

        StringSource(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
