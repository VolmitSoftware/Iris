package art.arcane.iris.core.runtime.jigsaw;

import java.util.Objects;

public enum JigsawStudioToolAction {
    OPEN_MENU("Open Control Menu", false),
    SELECT_WORKCELL("Select Workcell", false),
    TOGGLE_WORKCELL("Toggle Workcell", false),
    LOAD_VARIANT("Load Variant", false),
    CREATE_VARIANT("New Blank Variant", false),
    DUPLICATE_VARIANT("Duplicate This Cell's Variant", false),
    DUPLICATE_FAMILY("Duplicate All Enabled Cells as Family", false),
    PREVIEW_GRAPH("Go to Preview", false),
    FLUSH_AUTOSAVE("Flush Autosave", false),
    TOGGLE_ROTATION("Toggle Rotation", false),
    EXPAND_TO_CELL("Resize Variant to Capacity", false),
    RESIZE_VARIANT("Resize This Variant", false),
    RESIZE_WORKCELL("Resize Workcell Capacity", false),
    RENAME_VARIANT("Rename This Variant", false),
    RENAME_WORKCELL("Rename This Workcell", false),
    ADJUST_VARIANT_WEIGHT("Adjust Variant Weight", false),
    ADJUST_VARIANT_CHANCE("Adjust Variant Chance", false),
    SET_THEME("Set Theme", false),
    SET_PIECE_RULES("Set Piece Rules", false),
    TOGGLE_REQUIRE_CAPS("Toggle Required Caps", false),
    UNLINK_MEMBERSHIP("Unlink Pool Entry", true),
    DELETE_VARIANT("Delete Variant", true),
    DELETE_PROJECT("Delete Project", true);

    private final String displayName;
    private final boolean destructive;

    JigsawStudioToolAction(String displayName, boolean destructive) {
        this.displayName = Objects.requireNonNull(displayName, "Jigsaw Studio tool display name");
        this.destructive = destructive;
    }

    public String displayName() {
        return displayName;
    }

    public boolean destructive() {
        return destructive;
    }
}
