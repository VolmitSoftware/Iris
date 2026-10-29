package art.arcane.iris;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;

public class IrisPluginLoaderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exposesOnlyRelocatedRuntimeLibrariesInStableOrder() throws Exception {
        Path root = temporaryFolder.newFolder("libraries").toPath();
        Path original = Files.createDirectories(root.resolve("gson/2.14.0"))
                .resolve("gson.jar");
        Path relocatedGson = Files.createDirectories(root.resolve("gson/2.14.0/relocated/Iris"))
                .resolve("gson.jar");
        Path relocatedCaffeine = Files.createDirectories(root.resolve("caffeine/3.2.4/relocated/Iris"))
                .resolve("caffeine.jar");
        Files.createFile(original);
        Files.createFile(relocatedGson);
        Files.createFile(relocatedCaffeine);

        List<Path> libraries = IrisPluginLoader.relocatedLibraries(root);

        assertEquals(List.of(relocatedCaffeine, relocatedGson), libraries);
    }

    @Test
    public void readsMavenLibrariesFromTheSpigotDescriptorInThePluginJar() throws Exception {
        Path jar = temporaryFolder.getRoot().toPath().resolve("Iris.jar");
        try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write("name: Iris\nlibraries: [ 'org.lwjgl:lwjgl:3.4.3', 'org.lwjgl:lwjgl:jar:natives-linux:3.4.3' ]\n"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        List<String> libraries = IrisPluginLoader.mavenLibraries(jar);

        assertEquals(List.of("org.lwjgl:lwjgl:3.4.3", "org.lwjgl:lwjgl:jar:natives-linux:3.4.3"), libraries);
    }
}
