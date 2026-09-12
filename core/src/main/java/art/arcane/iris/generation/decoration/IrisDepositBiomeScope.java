package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.util.documentation.Description;

@Description("Selects which biome identity is checked by a deposit's include and exclude filters.")
public enum IrisDepositBiomeScope {
    @Description("Checks the surface biome selected for the deposit column.")
    SURFACE,
    @Description("Checks the cave biome selected at the deposit origin.")
    CAVE
}
