package art.arcane.iris.core.project;

import com.google.gson.annotations.SerializedName;

/** The external editor {@code studio.editor} opens packs and pack files in. */
public enum StudioEditor {
    @SerializedName("vscode")
    VSCODE("VSCode"),
    @SerializedName("zed")
    ZED("Zed"),
    @SerializedName("none")
    NONE("no editor");

    private final String displayName;

    StudioEditor(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
