package art.arcane.iris.core.tooling;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.modules.jigsaw.JigsawModule;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.mantle.components.StructureModule;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.spi.IrisServices;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.PlatformStructures;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StructureIndexOnStartTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    @After
    public void withoutPlatformStructures() {
        IrisServices.remove(PlatformStructures.class);
    }

    @Test
    public void writeOnceWaitsForPlatformStructuresThenWritesOnce() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        IrisData data = mock(IrisData.class);
        PlatformStructures platform = mock(PlatformStructures.class);
        ModuleResourceFixture.serveModules(data, new JigsawModule(), new StructureModule());
        when(data.getDataFolder()).thenReturn(pack);
        ModuleResourceFixture.serve(data).resource(StructureResources.STRUCTURES, "castle", new IrisStructure());
        when(platform.structures()).thenReturn(List.of(ResourceId.parse("minecraft:village"), ResourceId.parse("example:castle")));
        when(platform.structureSets()).thenReturn(List.of(ResourceId.parse("minecraft:villages")));

        assertNull(StructureIndexOnStart.writeOnce(data));

        IrisServices.register(PlatformStructures.class, platform);
        File index = StructureIndexOnStart.writeOnce(data);

        assertEquals(new File(pack, ".iris/structure-index.json"), index);
        assertTrue(index.isFile());
        assertNull(StructureIndexOnStart.writeOnce(data));
        verify(platform, times(1)).structures();
        verify(platform, times(1)).structureSets();
    }
}
