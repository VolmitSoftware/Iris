package art.arcane.iris.core.structure.authoring;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisModuleHost;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.core.structure.FakePlatformStructures;
import art.arcane.iris.core.tooling.IrisToolPack;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.mantle.components.StructureModule;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.modules.jigsaw.JigsawModule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructureIndexServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatedIndexLivesOutsideStructureRegistrantDirectory() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        File index = StructureIndexService.indexFile(pack);

        assertEquals(new File(pack, ".iris/structure-index.json"), index);
        assertFalse(index.toPath().startsWith(new File(pack, "structures").toPath()));
    }

    @Test
    public void writeListsPlatformStructuresAndTheStructuresOfThePack() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture.serveModules(data, new StructureModule(), new JigsawModule());
        when(data.getDataFolder()).thenReturn(pack);
        ModuleResourceFixture.serve(data).resource(StructureResources.STRUCTURES, "castle", new IrisStructure());
        FakePlatformStructures platform = new FakePlatformStructures()
                .withStructures("minecraft:village", "example:castle")
                .withStructureSets("minecraft:villages");

        File index = StructureIndexService.write(new IrisToolPack(data, IrisModuleHost.hostLog()), platform);

        assertEquals(StructureIndexService.indexFile(pack), index);
        String written = Files.readString(index.toPath());
        assertTrue(written.contains("minecraft:village"));
        assertTrue(written.contains("example:castle"));
        assertTrue(written.contains("minecraft:villages"));
        assertTrue(written.contains("\"castle\""));
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
