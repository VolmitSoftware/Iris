package art.arcane.iris.studio.jigsaw;

public enum JigsawStudioBoardState {
    LOADING("Loading"),
    SAVED("Saved"),
    UNSAVED("Unsaved"),
    SAVING("Saving"),
    DISABLED("Disabled"),
    INVALID("Invalid"),
    READ_ONLY("Read-only");

    private final String displayName;

    JigsawStudioBoardState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
