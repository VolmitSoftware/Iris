package art.arcane.iris.core.structure;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.volmlib.util.data.KCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StructureIndexServiceTest {
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
    public void legacyIndexPathRemainsExplicitForCleanup() throws Exception {
        File pack = temporaryFolder.newFolder("pack");

        assertEquals(new File(pack, "structures/structure-index.json"),
                StructureIndexService.legacyIndexFile(pack));
    }

    @Test
    public void legacyIndexIsDeletedAndStructureKeysAreRefreshed() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        File legacyIndex = StructureIndexService.legacyIndexFile(pack);
        Files.createDirectories(legacyIndex.toPath().getParent());
        Files.writeString(legacyIndex.toPath(), "{}", StandardCharsets.UTF_8);
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisStructure> structureLoader = mock(ResourceLoader.class);
        @SuppressWarnings("unchecked")
        KCache<String, IrisStructure> loadCache = mock(KCache.class);
        when(data.getDataFolder()).thenReturn(pack);
        when(data.getStructureLoader()).thenReturn(structureLoader);
        when(structureLoader.getLoadCache()).thenReturn(loadCache);

        StructureIndexService.removeLegacyIndex(data);

        assertFalse(legacyIndex.exists());
        verify(loadCache).invalidate("structure-index");
        verify(structureLoader).clearList();
    }

    @Test
    public void writeOnceWaitsForBoundHooksThenCleansBeforeEnumerating() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        File legacyIndex = StructureIndexService.legacyIndexFile(pack);
        Files.createDirectories(legacyIndex.toPath().getParent());
        Files.writeString(legacyIndex.toPath(), "{}", StandardCharsets.UTF_8);
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisStructure> structureLoader = mock(ResourceLoader.class);
        @SuppressWarnings("unchecked")
        KCache<String, IrisStructure> loadCache = mock(KCache.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks structureHooks = mock(PlatformStructureHooks.class);
        when(data.getDataFolder()).thenReturn(pack);
        when(data.getStructureLoader()).thenReturn(structureLoader);
        when(structureLoader.getLoadCache()).thenReturn(loadCache);
        when(structureLoader.getPossibleKeys()).thenReturn(new String[]{"castle", "structure-index"});
        when(structureHooks.structureKeys()).thenReturn(List.of("minecraft:village", "example:castle"));
        when(structureHooks.structureSetKeys()).thenReturn(List.of("minecraft:villages"));

        assertNull(StructureIndexService.writeOnce(data));
        assertTrue(legacyIndex.exists());

        IrisPlatforms.bind(platform);
        assertNull(StructureIndexService.writeOnce(data));
        assertTrue(legacyIndex.exists());

        when(platform.structureHooks()).thenReturn(structureHooks);
        File index = StructureIndexService.writeOnce(data);

        assertEquals(StructureIndexService.indexFile(pack), index);
        assertTrue(index.isFile());
        assertFalse(legacyIndex.exists());
        InOrder order = inOrder(loadCache, structureLoader, structureHooks);
        order.verify(loadCache).invalidate("structure-index");
        order.verify(structureLoader).clearList();
        order.verify(structureHooks).structureKeys();
        order.verify(structureHooks).structureSetKeys();
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
