package art.arcane.iris.engine.object.terrain.deposit;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Controls which vertical part of the world may contain a deposit origin.")
public enum IrisDepositPlacementScope {
    @Desc("Places in solid terrain below the generated surface while preserving the configured surface clearance.")
    TERRAIN,
    @Desc("Places only in existing solid hosts above the generated terrain surface.")
    ABOVE_TERRAIN,
    @Desc("Places in existing solid hosts anywhere within the dimension build height.")
    FULL_HEIGHT
}
