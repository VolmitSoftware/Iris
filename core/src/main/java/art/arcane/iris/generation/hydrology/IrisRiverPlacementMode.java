package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls whether river sources may start inside a dimension, region or biome, and how strongly they are drawn there.")
public enum IrisRiverPlacementMode {
    @Description("No river source may start here. Rivers may still pass through when routing allows it.")
    DISABLED,
    @Description("Rivers may pass through, but no source starts here.")
    TRANSIT_ONLY,
    @Description("Sources start here on their own merits, ranked like everywhere else.")
    NATURAL,
    @Description("Eligible source sites here rank ahead of sites elsewhere in the tile.")
    PREFERRED_HEADWATER,
    @Description("At least one source starts here whenever a site can reach a legal outlet; sources.minimumPerTile can raise that quota.")
    REQUIRED_HEADWATER
}
