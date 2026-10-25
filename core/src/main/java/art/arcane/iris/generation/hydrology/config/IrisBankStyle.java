package art.arcane.iris.generation.hydrology.config;

import art.arcane.volmlib.util.documentation.Description;

@Description("The shape of the banks between the water's edge and the natural ground, across the tier's bank band.")
public enum IrisBankStyle {
    @Description("An eased curve from the water's edge up to the natural ground, flat at both ends and steepest across the middle of the band.")
    SMOOTH,
    @Description("A straight slope from the water's edge to the natural ground, with a sharp shoulder at the shore and a sharp lip at the top.")
    LINEAR,
    @Description("A hollowed bank that climbs fast beside the water and flattens out toward the natural ground.")
    CONCAVE,
    @Description("The eased curve cut into level steps between the water's edge and the natural ground.")
    TERRACED,
    @Description("A level bench beside the water, then a steep wall up to the natural ground.")
    CLIFF
}
