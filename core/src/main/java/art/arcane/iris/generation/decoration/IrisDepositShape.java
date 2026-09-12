package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.util.documentation.Description;

@Description("Selects the geometry used to build each deposit clump.")
public enum IrisDepositShape {
    @Description("Builds the traditional Iris fixed-block-count cube clump.")
    IRIS,
    @Description("Builds a chain of overlapping ellipsoids matching Minecraft's ordinary ore-vein geometry.")
    VANILLA_ELLIPSOID,
    @Description("Builds sparse candidate offsets matching Minecraft's scattered-ore geometry.")
    VANILLA_SCATTERED
}
