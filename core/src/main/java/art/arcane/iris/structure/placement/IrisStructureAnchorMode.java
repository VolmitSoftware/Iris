package art.arcane.iris.structure.placement;

import art.arcane.volmlib.util.documentation.Description;

@Description("Selects the vertical environment used to anchor an Iris structure placement.")
public enum IrisStructureAnchorMode {
    @Description("Preserve the historical underground boolean behavior.")
    LEGACY,

    @Description("Anchor to the terrain surface and use minHeight/maxHeight as a surface gate.")
    SURFACE,

    @Description("Choose a deterministic Y inside the minHeight/maxHeight band.")
    HEIGHT_BAND,

    @Description("Anchor in carved space with solid support below.")
    CAVE_FLOOR,

    @Description("Anchor in carved space with solid support above.")
    CAVE_CEILING,

    @Description("Anchor in carved space without nearby floor or ceiling support.")
    CAVE_CENTER,

    @Description("Anchor at any carved-space position.")
    CAVE_ANY;

    public boolean isCave() {
        return switch (this) {
            case CAVE_FLOOR, CAVE_CEILING, CAVE_CENTER, CAVE_ANY -> true;
            default -> false;
        };
    }
}
