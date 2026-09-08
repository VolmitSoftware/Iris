package art.arcane.iris.core.structure;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.history.GenerationPackFingerprint;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StructureIndexServiceTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform previousPlatform;

    @Before
    public void detachPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void generatedIndexLivesOutsideStructureRegistrantDirectory() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        File index = StructureIndexService.indexFile(pack);

        assertEquals(new File(pack, ".iris/structure-index.json"), index);
        assertFalse(index.toPath().startsWith(new File(pack, "structures").toPath()));
    }

    @Test
    public void writeOnceWaitsForBoundHooksAndPreservesGenerationInputs() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        File structure = new File(pack, "structures/castle.json");
        Files.createDirectories(structure.toPath().getParent());
        Files.writeString(structure.toPath(), "{}", StandardCharsets.UTF_8);
        String fingerprint = GenerationPackFingerprint.compute(pack.toPath(), GenerationPackFingerprint.CURRENT_VERSION);
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisStructure> structureLoader = mock(ResourceLoader.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks structureHooks = mock(PlatformStructureHooks.class);
        when(data.getDataFolder()).thenReturn(pack);
        when(data.getStructureLoader()).thenReturn(structureLoader);
        when(structureLoader.getPossibleKeys()).thenReturn(new String[]{"castle"});
        when(structureHooks.structureKeys()).thenReturn(List.of("minecraft:village", "example:castle"));
        when(structureHooks.structureSetKeys()).thenReturn(List.of("minecraft:villages"));

        assertNull(StructureIndexService.writeOnce(data));

        IrisPlatforms.bind(platform);
        assertNull(StructureIndexService.writeOnce(data));

        when(platform.structureHooks()).thenReturn(structureHooks);
        File index = StructureIndexService.writeOnce(data);

        assertEquals(StructureIndexService.indexFile(pack), index);
        assertTrue(index.isFile());
        assertEquals("{}", Files.readString(structure.toPath(), StandardCharsets.UTF_8));
        assertEquals(fingerprint, GenerationPackFingerprint.compute(pack.toPath(), GenerationPackFingerprint.CURRENT_VERSION));
        assertNull(StructureIndexService.writeOnce(data));
        verify(structureHooks, times(1)).structureKeys();
        verify(structureHooks, times(1)).structureSetKeys();
    }

    @Test
    public void sharedServiceDoesNotLinkBukkitRuntimeClasses() throws Exception {
        InputStream stream = StructureIndexService.class.getResourceAsStream("StructureIndexService.class");
        assertNotNull(stream);
        String bytecode;
        try (InputStream input = stream) {
            bytecode = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }

        assertFalse(bytecode.contains("art/arcane/iris/util/project/matter/IrisMatterSupport"));
        assertFalse(bytecode.contains("org/bukkit/"));
    }
}
