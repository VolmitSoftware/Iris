package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls whether river routes may pass through a dimension, region or biome, and at what cost.")
public enum IrisRiverRoutingMode {
    @Description("Rivers never route through here.")
    BLOCK,
    @Description("Rivers route through here only when no cheaper path exists; transit carries a strong cost.")
    AVOID,
    @Description("Rivers route through here at the normal cost.")
    ALLOW,
    @Description("Rivers route through here at a reduced cost, so routes are drawn toward this area.")
    PREFER
}
