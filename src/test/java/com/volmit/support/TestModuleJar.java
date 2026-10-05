package com.volmit.support;

import com.volmit.IrisModule;
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
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Builds a real module jar from Java source at test time. Its classes are not on the test
 * classpath, so only a pack class loader can define them. Sources compile against the API's main
 * classes only, like a real module; built-in example modules and host libraries must be added with
 * {@link #compileAgainstHostOf}, so a jar cannot depend on test support classes by accident.
 */
public final class TestModuleJar {
    private static final String SERVICES_FILE = "META-INF/services/" + IrisModule.class.getName();

    private final Map<String, String> sources = new LinkedHashMap<>();
    private final Map<String, byte[]> resources = new LinkedHashMap<>();
    private boolean multiRelease;
    private final List<String> entryPoints = new ArrayList<>();
    private final List<Path> classpath = new ArrayList<>(List.of(classesOf(IrisModule.class)));

    /** An entry class with the given module id whose install body may use {@code graph}. */
    public static TestModuleJar module(String className, String moduleId, String members, String install) {
        return module(className, moduleId, List.of(), members, install);
    }

    /** Like {@link #module(String, String, String, String)}, with {@code info()} declaring {@code optionalPackages}. */
    public static TestModuleJar module(String className, String moduleId, List<String> optionalPackages, String members, String install) {
        var info = moduleInfo(moduleId, optionalPackages);
        return new TestModuleJar().source(className, moduleSource(className, info, members, install)).entryPoint(className);
    }

    /** Compiles one class on its own and returns its class file, for raw entries such as versioned classes. */
    public static byte[] compileClass(Path workDirectory, String className, String code) {
        try {
            var classes = new TestModuleJar().source(className, code).compile(Files.createTempDirectory(workDirectory, "classes"));
            return Files.readAllBytes(classes.resolve(className.replace('.', '/') + ".class"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
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

    /** Adds a raw jar entry, such as a versioned class file, without compiling anything. */
    public TestModuleJar resource(String entryName, byte[] content) {
        resources.put(entryName, content.clone());
        return this;
    }

    /** Marks the jar {@code Multi-Release: true}, so its {@code META-INF/versions} entries count. */
    public TestModuleJar multiRelease() {
        multiRelease = true;
        return this;
    }

    /** Puts a jar or class directory on the compile classpath only; it is not packaged. */
    public TestModuleJar compileAgainst(Path classes) {
        classpath.add(classes);
        return this;
    }

    /** Compiles against wherever {@code type} was loaded from, such as a host library jar. */
    public TestModuleJar compileAgainstHostOf(Class<?> type) {
        return compileAgainst(classesOf(type));
    }

    /** Writes {@code <packRoot>/modules/<jarName>} and returns its path. */
    public Path writeInto(Path packRoot, String jarName) {
        try {
            var jar = Files.createDirectories(packRoot.resolve("modules")).resolve(jarName);
            var classes = compile(Files.createTempDirectory(packRoot, "classes"));
            try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest())) {
                writeClasses(output, classes);
                writeResources(output);
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

    private Manifest manifest() {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (multiRelease) manifest.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        return manifest;
    }

    private void writeResources(JarOutputStream output) throws IOException {
        for (var resource : resources.entrySet()) {
            output.putNextEntry(new JarEntry(resource.getKey()));
            output.write(resource.getValue());
            output.closeEntry();
        }
    }

    private void writeEntryPoints(JarOutputStream output) throws IOException {
        if (entryPoints.isEmpty()) return;
        output.putNextEntry(new JarEntry(SERVICES_FILE));
        output.write(("# entry point\n" + String.join("\n", entryPoints) + "\n").getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static String moduleInfo(String moduleId, List<String> optionalPackages) {
        var info = "ModuleInfo.of(\"%s\", \"%s\", \"1.0.0\")".formatted(moduleId, displayName(moduleId));
        if (optionalPackages.isEmpty()) return info;
        var packages = optionalPackages.stream().map(name -> '"' + name + '"').collect(Collectors.joining(", "));
        return info + ".withOptionalPackages(" + packages + ")";
    }

    private static String moduleSource(String className, String info, String members, String install) {
        int dot = className.lastIndexOf('.');
        return """
                package %s;

                import com.volmit.IrisModule;
                import com.volmit.ModuleInfo;
                import com.volmit.stage.*;

                public final class %s implements IrisModule {
                    %s

                    public ModuleInfo info() { return %s; }

                    public void install(StageGraph graph) { %s }
                }
                """.formatted(className.substring(0, dot), className.substring(dot + 1), members, info, install);
    }

    /** The display name test modules get, so assertions can predict it from the id. */
    public static String displayName(String moduleId) {
        return "Test " + moduleId;
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
