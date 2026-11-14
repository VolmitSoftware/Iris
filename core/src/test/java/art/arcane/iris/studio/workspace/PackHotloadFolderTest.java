package art.arcane.iris.studio.workspace;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.ReactiveFolder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PackHotloadFolderTest {
    private static final long HOTLOAD_DEADLINE_MILLIS = 15_000L;
    private static final long CHECK_INTERVAL_MILLIS = 50L;
    private static final KList<String> WATCHED_EXTENSIONS = new KList<>(".iob", ".json");

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void packFileChangeTriggersHotload() throws Exception {
        File pack = temp.newFolder("pack");
        File dimension = new File(pack, "dimensions/overworld.json");
        write(dimension, "{}");
        AtomicReference<KList<File>> changed = new AtomicReference<>();
        ReactiveFolder folder = PackHotloadFolder.watch(pack, WATCHED_EXTENSIONS, (created, modified, deleted) -> changed.set(modified));
        folder.check();

        write(dimension, "{\"changed\":true}");

        assertNotNull(awaitHotload(folder, changed));
    }

    @Test
    public void editorSettingsAndIrisCacheNeverReachTheHotload() throws Exception {
        File pack = temp.newFolder("pack");
        File dimension = new File(pack, "dimensions/overworld.json");
        File editorSettings = new File(pack, ".zed/settings.json");
        File irisCache = new File(pack, ".iris/cache.json");
        write(dimension, "{}");
        write(editorSettings, "{}");
        write(irisCache, "{}");
        AtomicReference<KList<File>> changed = new AtomicReference<>();
        ReactiveFolder folder = PackHotloadFolder.watch(pack, WATCHED_EXTENSIONS, (created, modified, deleted) -> changed.set(modified));
        folder.check();

        write(editorSettings, "{\"changed\":true}");
        write(irisCache, "{\"changed\":true}");
        write(dimension, "{\"changed\":true}");

        KList<File> reloaded = awaitHotload(folder, changed);
        assertNotNull(reloaded);
        assertEquals(1, reloaded.size());
        assertTrue(reloaded.get(0).getPath().endsWith("overworld.json"));
    }

    private static void write(File file, String content) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), content);
    }

    private static KList<File> awaitHotload(ReactiveFolder folder, AtomicReference<KList<File>> changed) throws InterruptedException {
        long deadline = System.currentTimeMillis() + HOTLOAD_DEADLINE_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (folder.check()) {
                return changed.get();
            }
            Thread.sleep(CHECK_INTERVAL_MILLIS);
        }
        return null;
    }
}
