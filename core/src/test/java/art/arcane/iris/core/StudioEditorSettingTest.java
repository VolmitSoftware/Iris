package art.arcane.iris.core;

import art.arcane.iris.core.project.StudioEditor;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StudioEditorSettingTest {
    @Test
    public void missingEditorDefaultsToVSCode() {
        assertEquals(StudioEditor.VSCODE, IrisSettings.parseHotloadSnapshot("{\"studio\":{}}").getStudio().getEditor());
    }

    @Test
    public void editorIsReadFromItsLowercaseName() {
        assertEquals(StudioEditor.ZED, IrisSettings.parseHotloadSnapshot("{\"studio\":{\"editor\":\"zed\"}}").getStudio().getEditor());
    }

    @Test
    public void unknownEditorFallsBackToVSCode() {
        assertEquals(StudioEditor.VSCODE, IrisSettings.parseHotloadSnapshot("{\"studio\":{\"editor\":\"emacs\"}}").getStudio().getEditor());
    }

    @Test
    public void editorIsWrittenByItsLowercaseName() {
        IrisSettings settings = new IrisSettings();
        settings.getStudio().setEditor(StudioEditor.NONE);

        assertTrue(new Gson().toJson(settings).contains("\"editor\":\"none\""));
    }
}
