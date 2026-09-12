package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.lifecycle.VanishedWorldStorage;
import com.google.gson.Gson;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisEngineDataPersistenceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void atomicWriteRefusesToRebuildADeletedWorldFolder() throws Exception {
        File worldFolder = temporaryFolder.newFolder("deleted-world");
        File output = new File(worldFolder, "iris/engine-data/dimension.json");
        assertTrue(worldFolder.delete());

        assertThrows(IOException.class,
                () -> EngineDataStore.writeEngineDataAtomically(output, new IrisEngineData()));

        assertFalse("a save must never resurrect a world tree", worldFolder.exists());
    }

    @Test
    public void vanishedWorldStorageIsDetectedAndReported() throws Exception {
        VanishedWorldStorage.reset();
        File worldFolder = temporaryFolder.newFolder("vanishing-world");

        assertFalse(VanishedWorldStorage.vanished(worldFolder));
        assertTrue(worldFolder.delete());
        assertTrue(VanishedWorldStorage.vanished(worldFolder));
        VanishedWorldStorage.reset();
    }

    /**
     * A save-all after a hot delete writes the level's own data/*.dat files back, which recreates the world
     * folder. That folder is not the world returning, and letting Iris resume writing into it rebuilds an
     * iris/ directory that makes the next boot classify the husk as unusable on some runs and empty on
     * others.
     */
    @Test
    public void aWorldFolderTheServerRecreatesNeverReEnablesPersistence() throws Exception {
        VanishedWorldStorage.reset();
        File worldFolder = temporaryFolder.newFolder("recreated-world");

        assertFalse(VanishedWorldStorage.vanished(worldFolder));
        assertTrue(worldFolder.delete());
        assertTrue(VanishedWorldStorage.vanished(worldFolder));
        assertTrue(worldFolder.mkdirs());

        assertTrue("a deleted world stays deleted for this JVM", VanishedWorldStorage.vanished(worldFolder));
        VanishedWorldStorage.reset();
    }

    /**
     * The world folder can be back before Iris ever looks at it, so the latch alone is not enough: a tree
     * Iris has already written that is now missing is the same delete.
     */
    @Test
    public void anEstablishedIrisTreeThatIsGoneCountsAsVanishedUnderARecreatedFolder() throws Exception {
        VanishedWorldStorage.reset();
        File worldFolder = temporaryFolder.newFolder("hot-deleted-world");
        File engineData = new File(worldFolder, "iris/engine-data");
        assertTrue(engineData.mkdirs());

        assertFalse(VanishedWorldStorage.vanished(worldFolder, engineData));
        assertTrue(engineData.delete());
        assertTrue(new File(worldFolder, "iris").delete());

        assertTrue(VanishedWorldStorage.vanished(worldFolder, engineData));
        assertTrue("the verdict holds without the established tree too",
                VanishedWorldStorage.vanished(worldFolder));
        VanishedWorldStorage.reset();
    }

    @Test
    public void atomicWriteCreatesAndReplacesEngineData() throws Exception {
        File worldFolder = temporaryFolder.newFolder("live-world");
        File folder = new File(worldFolder, "iris/engine-data");
        File output = new File(folder, "dimension.json");
        IrisEngineData first = new IrisEngineData();
        first.getStatistics().setVersion(10);

        EngineDataStore.writeEngineDataAtomically(output, first);

        IrisEngineData firstRead = new Gson().fromJson(Files.readString(output.toPath()), IrisEngineData.class);
        assertEquals(10, firstRead.getStatistics().getVersion());

        IrisEngineData replacement = new IrisEngineData();
        replacement.getStatistics().setVersion(20);
        EngineDataStore.writeEngineDataAtomically(output, replacement);

        IrisEngineData replacementRead = new Gson().fromJson(Files.readString(output.toPath()), IrisEngineData.class);
        assertEquals(20, replacementRead.getStatistics().getVersion());
        File[] temporaryFiles = folder.listFiles((ignored, name) -> name.endsWith(".tmp"));
        assertFalse(temporaryFiles != null && temporaryFiles.length > 0);
    }

    @Test(expected = IOException.class)
    public void atomicWriteRejectsParentlessPath() throws Exception {
        EngineDataStore.writeEngineDataAtomically(new File("parentless-engine-data.json"), new IrisEngineData());
    }
}
