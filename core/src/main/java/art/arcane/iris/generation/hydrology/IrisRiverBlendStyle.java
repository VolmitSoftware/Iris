package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;

@Description("The shape of the eroded valley side between the shore band and natural terrain.")
public enum IrisRiverBlendStyle {
    @Description("An eased S-curve from the bank top out to natural terrain, flat at both ends and steepest across the middle of the band; blendCurve skews it.")
    SMOOTH,
    @Description("A straight slope from the bank top to natural terrain with a sharp shoulder at the shore and a sharp lip at the top; blendCurve bends it.")
    LINEAR,
    @Description("A hollowed valley side that climbs fast beside the shore and flattens out toward natural terrain; blendCurve deepens the hollow.")
    CONCAVE,
    @Description("The eased curve cut into level steps, terraceSteps of them between the bank top and natural terrain.")
    TERRACED,
    @Description("A level bench at the bank top for cliffFraction of the band, then a vertical wall up to natural terrain.")
    CLIFF
}
