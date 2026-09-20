package art.arcane.iris.platform.bootstrap;

import io.github.slimjar.exceptions.DownloaderException;
import io.github.slimjar.resolver.data.Dependency;
import io.github.slimjar.resolver.data.DependencyData;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NativeRuntimeLibrariesTest {
    @Test
    public void selectsOnlyCommonAndMatchingProvider() throws Exception {
        Properties manifest = manifest();
        Dependency original = new Dependency("example", "library", "1", null, List.of());
        DependencyData data = new DependencyData(List.of(), List.of(), List.of(original), List.of());
        List<String> selected = NativeRuntimeLibraries.from(manifest, "26.3.0").merge(data).dependencies()
                .stream().map(Dependency::artifactId).sorted().toList();
        assertEquals(List.of("library", "native-common", "native-v26_3_R1"), selected);
        assertThrows(UnsupportedOperationException.class, () -> NativeRuntimeLibraries.from(manifest, "1.21.11"));
    }

    @Test
    public void rejectsProviderBytesFromDifferentBuild() throws Exception {
        Path jar = Files.createTempFile("native-runtime", ".jar");
        try {
            Files.write(jar, new byte[0]);
            NativeRuntimeLibraries.verify(jar.toFile(), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
            Files.writeString(jar, "different build");
            assertThrows(DownloaderException.class, () -> NativeRuntimeLibraries.verify(jar.toFile(),
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    private static Properties manifest() {
        Properties properties = new Properties();
        List<String> modules = List.of("native-common", "native-v26_2_R1", "native-v26_3_R1");
        properties.setProperty("modules", String.join(",", modules));
        properties.setProperty("repository", "https://jitpack.io/");
        for (String module : modules) {
            properties.setProperty(module + ".coordinate", "com.github.VolmitSoftware.VolmLib:" + module + ":revision");
            properties.setProperty(module + ".sha256", "0".repeat(64));
        }
        return properties;
    }
}
