package art.arcane.iris.engine.object.structure;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Selects the vertical environment used to anchor an Iris structure placement.")
public enum IrisStructureAnchorMode {
    @Desc("Preserve the historical underground boolean behavior.")
    LEGACY,

    @Desc("Anchor to the terrain surface and use minHeight/maxHeight as a surface gate.")
    SURFACE,

    @Desc("Choose a deterministic Y inside the minHeight/maxHeight band.")
    HEIGHT_BAND,

    @Desc("Anchor in carved space with solid support below.")
    CAVE_FLOOR,

    @Desc("Anchor in carved space with solid support above.")
    CAVE_CEILING,

    @Desc("Anchor in carved space without nearby floor or ceiling support.")
    CAVE_CENTER,

    @Desc("Anchor at any carved-space position.")
    CAVE_ANY;

    public boolean isCave() {
        return switch (this) {
            case CAVE_FLOOR, CAVE_CEILING, CAVE_CENTER, CAVE_ANY -> true;
            default -> false;
        };
    }
}
