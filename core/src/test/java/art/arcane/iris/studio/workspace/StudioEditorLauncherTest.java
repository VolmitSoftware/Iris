package art.arcane.iris.studio.workspace;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StudioEditorLauncherTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void autoPicksZedWhenItIsTheOnlyEditorOnThePath() throws IOException {
        File bin = binWith("zeditor");

        assertEquals(StudioEditor.ZED, StudioEditorLauncher.detectInstalledEditor(bin.getPath()));
    }

    @Test
    public void autoPrefersVSCodeWhenBothEditorsAreOnThePath() throws IOException {
        File zedBin = binWith("zed");
        File codeBin = binWith("code");

        assertEquals(StudioEditor.VSCODE, StudioEditorLauncher.detectInstalledEditor(zedBin.getPath() + File.pathSeparator + codeBin.getPath()));
    }

    @Test
    public void autoFallsBackToVSCodeWhenNoEditorIsOnThePath() throws IOException {
        assertEquals(StudioEditor.VSCODE, StudioEditorLauncher.detectInstalledEditor(temporaryFolder.newFolder().getPath()));
        assertEquals(StudioEditor.VSCODE, StudioEditorLauncher.detectInstalledEditor(null));
    }

    private File binWith(String command) throws IOException {
        File bin = temporaryFolder.newFolder();
        File executable = new File(bin, command);
        assertTrue(executable.createNewFile());
        assertTrue(executable.setExecutable(true));
        return bin;
    }
}
