package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls which vertical part of the world may contain a deposit origin.")
public enum IrisDepositPlacementScope {
    @Description("Places in solid terrain below the generated surface while preserving the configured surface clearance.")
    TERRAIN,
    @Description("Places only in existing solid hosts above the generated terrain surface.")
    ABOVE_TERRAIN,
    @Description("Places in existing solid hosts anywhere within the dimension build height.")
    FULL_HEIGHT
}
