import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.jvm.tasks.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ApiGeneratorTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void publishesCanonicalCoordinatesAndTracksConfiguredJar() throws Exception {
        Project project = project();
        GenerateApiTask api = project.getTasks().named("irisApi", GenerateApiTask.class).get();
        Jar jar = project.getTasks().named("jar", Jar.class).get();
        jar.getArchiveFileName().set("Iris v4.1.0-26.2 [CraftBukkit] 26.1.2-26.2.jar");

        assertEquals(jar.getArchiveFile().get().getAsFile(), api.getInputFile().get().getAsFile());
        assertEquals(jar.getArchiveFileName().get(), api.getOutputFile().get().getAsFile().getName());
        assertEquals(ApiGenerator.targetDirectory(project), api.getOutputFile().get().getAsFile().getParentFile());

        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        MavenPublication publication = (MavenPublication) publishing.getPublications().getByName("maven");
        assertEquals("art.arcane", publication.getGroupId());
        assertEquals("iris", publication.getArtifactId());
        assertEquals("4.1.0-26.2", publication.getVersion());
        assertEquals(1, publication.getArtifacts().size());
        assertEquals(api.getOutputFile().get().getAsFile(), publication.getArtifacts().iterator().next().getFile());

        GenerateMavenPom pom = project.getTasks().named("generatePomFileForMavenPublication", GenerateMavenPom.class).get();
        File destination = temporary.newFile("iris.pom");
        pom.setDestination(destination);
        pom.doGenerate();
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(destination);
        assertEquals("art.arcane", document.getElementsByTagName("groupId").item(0).getTextContent());
        assertEquals("iris", document.getElementsByTagName("artifactId").item(0).getTextContent());
        assertEquals("4.1.0-26.2", document.getElementsByTagName("version").item(0).getTextContent());
        assertEquals(0, document.getElementsByTagName("dependency").getLength());
    }

    @Test
    public void generatesVerifiableStubsThatCompileConsumersWithSharedSignatures() throws Exception {
        Project project = project();
        Path sources = temporary.newFolder("sources").toPath();
        Path classes = temporary.newFolder("classes").toPath();
        Path apiSource = sources.resolve("example/SampleApi.java");
        Path sharedSource = sources.resolve("example/SharedValue.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(sharedSource, """
                package example;
                public record SharedValue(int value) {}
                """);
        Files.writeString(apiSource, """
                package example;
                import java.io.IOException;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.util.List;
                public abstract class SampleApi<T extends Number> {
                    public static final int CONSTANT = 42;
                    public SampleApi() {}
                    public abstract T value();
                    public native int nativeValue();
                    @Contract
                    public static List<SharedValue> values(@Contract String input) throws IOException {
                        try {
                            if (input.isEmpty()) {
                                throw new IllegalArgumentException("implementation-only");
                            }
                            return List.of(new SharedValue(input.length()));
                        } catch (IllegalArgumentException exception) {
                            throw new IOException(exception);
                        }
                    }
                    public interface Query {
                        int count();
                        default int sample() { return count() + 1; }
                    }
                    public enum Mode { ONE, TWO }
                    @Retention(RetentionPolicy.RUNTIME)
                    public @interface Contract { String value() default "api"; }
                }
                """);
        compile(List.of(apiSource, sharedSource), classes, List.of());

        GenerateApiTask api = project.getTasks().named("irisApi", GenerateApiTask.class).get();
        Path input = project.getLayout().getBuildDirectory().file("plugin.jar").get().getAsFile().toPath();
        Files.createDirectories(input.getParent());
        createJar(classes, input);
        api.getInputFile().set(input.toFile());
        api.getOutputFile().set(project.getLayout().getBuildDirectory().file("api.jar"));
        api.generate();
        Path output = api.getOutputFile().get().getAsFile().toPath();

        try (JarFile jar = new JarFile(output.toFile())) {
            assertNotNull(jar.getEntry("example/SampleApi.class"));
            assertNotNull(jar.getEntry("example/SharedValue.class"));
            assertNull(jar.getEntry("plugin.yml"));
            byte[] bytes = jar.getInputStream(jar.getEntry("example/SampleApi.class")).readAllBytes();
            assertFalse(ClassReferences.read(bytes).contains("java/lang/IllegalArgumentException"));
        }

        try (URLClassLoader loader = new URLClassLoader(new URL[]{output.toUri().toURL()}, null)) {
            Class<?> sample = loader.loadClass("example.SampleApi");
            Method values = sample.getDeclaredMethod("values", String.class);
            assertEquals("input", values.getParameters()[0].getName());
            assertEquals(1, values.getParameterAnnotations()[0].length);
            assertEquals(1, values.getAnnotations().length);
            assertEquals("java.util.List<example.SharedValue>", values.getGenericReturnType().getTypeName());
            assertEquals(IOException.class, values.getExceptionTypes()[0]);
            assertTrue(Modifier.isAbstract(sample.getDeclaredMethod("value").getModifiers()));
            assertTrue(Modifier.isNative(sample.getDeclaredMethod("nativeValue").getModifiers()));
            InvocationTargetException invocation = assertThrows(InvocationTargetException.class,
                    () -> values.invoke(null, "world"));
            assertEquals(IllegalStateException.class, invocation.getCause().getClass());
            assertEquals("Only API", invocation.getCause().getMessage());

            Class<?> shared = loader.loadClass("example.SharedValue");
            assertTrue(shared.isRecord());
            InvocationTargetException constructor = assertThrows(InvocationTargetException.class,
                    () -> shared.getDeclaredConstructor(int.class).newInstance(3));
            assertEquals(IllegalStateException.class, constructor.getCause().getClass());
            assertTrue(loader.loadClass("example.SampleApi$Query").getDeclaredMethod("sample").isDefault());
            loader.loadClass("example.SampleApi$Mode").getDeclaredMethods();
            assertEquals("api", loader.loadClass("example.SampleApi$Contract").getDeclaredMethod("value").getDefaultValue());
        }

        Path consumer = sources.resolve("Consumer.java");
        Files.writeString(consumer, """
                import example.SampleApi;
                import example.SharedValue;
                import java.io.IOException;
                import java.util.List;
                public class Consumer extends SampleApi<Integer> implements SampleApi.Query {
                    public Integer value() { return SampleApi.CONSTANT; }
                    public int count() { return 1; }
                    public List<SharedValue> query() throws IOException { return SampleApi.values("world"); }
                    public int sampleDefault() { return sample(); }
                    public SampleApi.Mode mode() { return SampleApi.Mode.ONE; }
                    public SharedValue point() { return new SharedValue(3); }
                }
                """);
        compile(List.of(consumer), temporary.newFolder("consumer").toPath(), List.of("-classpath", output.toString()));
    }

    private Project project() throws IOException {
        Project project = ProjectBuilder.builder().withName("Iris").withProjectDir(temporary.newFolder()).build();
        project.setGroup("art.arcane");
        project.setVersion("4.1.0-26.2");
        project.getPlugins().apply("java");
        project.getPlugins().apply(ApiGenerator.class);
        return project;
    }

    private static void compile(List<Path> sources, Path output, List<String> extraOptions) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("A JDK is required to verify API consumers", compiler);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = Stream.concat(
                    List.of("--release", "25", "-parameters", "-proc:none", "-d", output.toString()).stream(),
                    extraOptions.stream()).toList();
            boolean success = compiler.getTask(null, files, diagnostics, options, null,
                    files.getJavaFileObjectsFromPaths(sources)).call();
            assertTrue(diagnostics.getDiagnostics().toString(), success);
        }
    }

    private static void createJar(Path classes, Path destination) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(destination));
             Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("plugin.yml"));
            output.write("name: Iris".getBytes());
            output.closeEntry();
        }
    }
}
