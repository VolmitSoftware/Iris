package art.arcane.iris.core.tooling;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.modules.jigsaw.JigsawModule;
import art.arcane.iris.core.structure.FakePlatformStructures;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.mantle.components.StructureModule;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import com.volmit.resource.ResourceId;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StructureCommandsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform previousPlatform;
    private File packFolder;
    private IrisData data;
    private ToolingCommands dispatcher;
    private final RecordingSender sender = new RecordingSender();

    @Before
    public void aPackServingTheStructureModule() throws Exception {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenReturn(List.of("minecraft:village", "example:castle"));
        when(hooks.structureSetKeys()).thenReturn(List.of("minecraft:villages"));
        IrisPlatforms.bind(platform);

        packFolder = temporaryFolder.newFolder("overworld");
        data = mock(IrisData.class);
        ModuleResourceFixture.serveModules(data, new JigsawModule(), new StructureModule());
        when(data.getDataFolder()).thenReturn(packFolder);
        IrisStructure emptyCastle = new IrisStructure();
        emptyCastle.setStartPool("ghost-pool");
        ModuleResourceFixture.serve(data).resource(StructureResources.STRUCTURES, "empty-castle", emptyCastle);
        when(data.getPackResources().require(StructureResources.STRUCTURES, new ResourceId("test", "empty-castle")))
                .thenReturn(emptyCastle);

        ServedPack served = new IrisServedPack(data);
        dispatcher = new ToolingCommands(new PackDirectory() {
            @Override
            public Optional<ServedPack> find(String name) {
                return name.equals("overworld") ? Optional.of(served) : Optional.empty();
            }

            @Override
            public List<String> names() {
                return List.of("overworld");
            }
        }, Runnable::run, Optional::empty, () -> Optional.of(new FakePlatformStructures()
                .withStructures("minecraft:village", "example:castle")
                .withStructureSets("minecraft:villages")));
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    private void run(String line) {
        dispatcher.execute(sender, List.of(line.split(" ")));
    }

    @Test
    public void listWritesTheStructureIndexThroughAnEditAndReloadsOnce() throws Exception {
        run("structure list pack=overworld");

        File index = new File(packFolder, ".iris/structure-index.json");
        assertTrue(index.isFile());
        assertTrue(Files.readString(index.toPath()).contains("example:castle"));
        assertEquals(List.of("Wrote the structure index: " + index.getPath()), sender.successes);
        assertTrue(sender.errors.isEmpty());
        verify(data, times(1)).invalidateStructureResources();
    }

    @Test
    public void theAliasesOfTheRootAndTheCommandAnswerToo() {
        run("str ls pack=overworld");

        assertEquals(1, sender.successes.size());
    }

    @Test
    public void infoNamesTheStructureByTheResourcesOfThePack() {
        run("structure info nowhere pack=overworld");

        assertEquals(List.of("No structures resource 'nowhere' in this pack"), sender.errors);
        assertEquals(List.of("Use: structure info <structure>"), sender.infos);
    }

    @Test
    public void infoAssemblesTheStructureAndReportsAnInvalidGraphAsTheCommandsFailure() {
        run("structure info empty-castle pack=overworld");

        assertEquals(1, sender.errors.size());
        assertTrue(sender.errors.get(0), sender.errors.get(0).startsWith("structure info failed: "));
        assertTrue(sender.errors.get(0), sender.errors.get(0).contains("references missing start pool 'ghost-pool'"));
    }

    @Test
    public void verifyNeedsTheWorldOfTheSender() {
        run("structure verify pack=overworld");

        assertEquals(List.of("Stand in a world generated with this pack: verify reads the running world"), sender.errors);
    }

    @Test
    public void theGroupListsTheThreeCommands() {
        run("structure pack=overworld");

        assertEquals(4, sender.infos.size());
        assertTrue(sender.infos.get(1).startsWith("  structure list"));
        assertTrue(sender.infos.get(2).startsWith("  structure info <structure>"));
        assertTrue(sender.infos.get(3).startsWith("  structure verify [radius]"));
    }

    @Test
    public void completionOffersTheStructuresOfThePack() {
        when(data.getPackResources().packId()).thenReturn(new ResourceId("test", "overworld"));

        assertEquals(List.of("empty-castle"), dispatcher.complete(sender, List.of("structure", "info", "pack=overworld", "e")));
    }
}
