package art.arcane.iris.modded.service;

import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedStudioHotloadServiceTest {
    @Test
    public void detectsDatapackImportsAcrossLoadedDimensions() {
        IrisDimension empty = new IrisDimension();
        IrisDimension imported = new IrisDimension();
        imported.setDatapackImports(new KList<String>().qadd("https://modrinth.com/datapack/example"));

        assertTrue(ModdedStudioHotloadService.hasDatapackImports(List.of(empty, imported)));
    }

    @Test
    public void rejectsMissingOrEmptyDatapackImports() {
        IrisDimension empty = new IrisDimension();

        assertFalse(ModdedStudioHotloadService.hasDatapackImports(null));
        assertFalse(ModdedStudioHotloadService.hasDatapackImports(List.of(empty)));
    }
}
