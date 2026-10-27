package art.arcane.iris.configuration;

import art.arcane.iris.studio.workspace.StudioEditor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class StudioEditorSettingTest {
    @Test
    public void editorDefaultsToVSCode() {
        assertEquals(StudioEditor.VSCODE, IrisSettings.parseHotloadSnapshot("{}").getStudio().getEditor());
    }

    @Test
    public void editorIsReadByItsLowercaseName() {
        assertEquals(StudioEditor.ZED, IrisSettings.parseHotloadSnapshot("{\"studio\":{\"editor\":\"zed\"}}").getStudio().getEditor());
    }

    @Test
    public void legacyOpenVSCodeFalseMigratesToNoEditorAndIsNotWrittenBack() {
        IrisSettings settings = IrisSettings.parseHotloadSnapshot("{\"studio\":{\"openVSCode\":false}}");

        assertEquals(StudioEditor.NONE, settings.getStudio().getEditor());
        JsonObject studio = new Gson().toJsonTree(settings).getAsJsonObject().getAsJsonObject("studio");
        assertFalse("The legacy key must disappear on save", studio.has("openVSCode"));
        assertEquals("none", studio.get("editor").getAsString());
    }

    @Test
    public void legacyOpenVSCodeTrueKeepsVSCode() {
        assertEquals(StudioEditor.VSCODE, IrisSettings.parseHotloadSnapshot("{\"studio\":{\"openVSCode\":true}}").getStudio().getEditor());
    }
}
