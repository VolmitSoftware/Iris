package art.arcane.iris.pack.loading;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Answers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommentedPackJsonTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform previousPlatform;
    private IrisSettings previousSettings;
    private IrisData data;

    @Before
    public void bindPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        previousSettings = IrisSettings.settings;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class, Answers.CALLS_REAL_METHODS);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.dataFolder()).thenReturn(temporaryFolder.getRoot());
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, new NoOpPreservationRegistry());
    }

    @After
    public void restorePlatform() {
        if (data != null) {
            data.close();
            data = null;
        }
        IrisServices.remove(PreservationRegistry.class);
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void biomeAndReferencedSnippetWithJsoncCommentsLoad() throws Exception {
        File pack = temporaryFolder.newFolder("commented-pack");
        write(pack.toPath().resolve("biomes/commented.json"), """
                // Biome authored with JSONC comments
                {
                  /* shown in studio */
                  "name": "Commented Biome", // trailing note
                  "generators": ["snippet/generator-layer/commented-flat"]
                }
                """);
        write(pack.toPath().resolve("snippet/generator-layer/commented-flat.json"), """
                {
                  // snippet note
                  "generator": "flat",
                  "min": 2, /* inline */ "max": 5
                }
                """);

        data = IrisData.get(pack);
        IrisBiome biome = data.getBiomeLoader().load("commented");

        assertNotNull("A biome with comments must load", biome);
        assertEquals("Commented Biome", biome.getName());
        IrisBiomeGeneratorLink link = biome.getGenerators().get(0);
        assertEquals("flat", link.getGenerator());
        assertEquals(2, link.getMin());
        assertEquals(5, link.getMax());
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static final class NoOpPreservationRegistry implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }
}
