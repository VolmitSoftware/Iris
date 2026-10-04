package art.arcane.iris.engine.object.terrain.deposit;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Selects the geometry used to build each deposit clump.")
public enum IrisDepositShape {
    @Desc("Builds the traditional Iris fixed-block-count cube clump.")
    IRIS,
    @Desc("Builds a chain of overlapping ellipsoids matching Minecraft's ordinary ore-vein geometry.")
    VANILLA_ELLIPSOID,
    @Desc("Builds sparse candidate offsets matching Minecraft's scattered-ore geometry.")
    VANILLA_SCATTERED
}
