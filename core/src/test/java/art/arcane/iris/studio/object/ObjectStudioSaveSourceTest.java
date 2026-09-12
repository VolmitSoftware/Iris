package art.arcane.iris.studio.object;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectStudioSaveSourceTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void fallbackLayoutSavesToTheEditableSource() throws Exception {
        Path source = temporary.newFolder("source").toPath();
        Path epoch = temporary.newFolder("epoch").toPath();
        Files.createDirectories(epoch.resolve("objects"));
        Files.writeString(epoch.resolve("objects/tree.iob"), "retained");
        Engine engine = mock(Engine.class);
        IrisData retained = mock(IrisData.class);
        when(engine.getData()).thenReturn(retained);
        when(engine.getPackSource()).thenReturn(source);
        when(retained.getDataFolder()).thenReturn(epoch.toFile());
        File destination = ObjectStudioSaveService.resolveObjectsDir(retained, engine);
        assertEquals(source.resolve("objects").toFile(), destination);
        Files.writeString(destination.toPath().resolve("tree.iob"), "edited");
        assertEquals("retained", Files.readString(epoch.resolve("objects/tree.iob")));
        assertEquals("edited", Files.readString(source.resolve("objects/tree.iob")));
    }

    @Test
    public void registeredMultiPackSourcesKeepTheirOwnDestination() throws Exception {
        File source = temporary.newFolder("additional-pack");
        File epoch = temporary.newFolder("epoch");
        Engine engine = mock(Engine.class);
        IrisData registered = mock(IrisData.class);
        IrisData retained = mock(IrisData.class);
        when(engine.getData()).thenReturn(retained);
        when(retained.getDataFolder()).thenReturn(epoch);
        when(registered.getDataFolder()).thenReturn(source);
        assertEquals(new File(source, "objects"), ObjectStudioSaveService.resolveObjectsDir(registered, engine));
    }
}
