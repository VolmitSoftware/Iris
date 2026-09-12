package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;

@Description("The cross-section of the wet channel bed from the centerline out to the waterline.")
public enum IrisRiverBedProfile {
    @Description("A level thalweg over thalwegFraction of the half-width that eases up to a one-block edge; a broad bowl.")
    BOWL,
    @Description("Full depth across the whole width, so the channel edge drops straight to the bed.")
    FLAT,
    @Description("A straight slope from full depth at the centerline to one block at the edge; thalwegFraction is ignored.")
    V,
    @Description("A level thalweg over thalwegFraction of the half-width that stays deep almost to the edge, then rises steeply; a trough with steep sides.")
    U
}
