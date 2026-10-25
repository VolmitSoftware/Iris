package art.arcane.iris.generation.hydrology.config;

import art.arcane.volmlib.util.documentation.Description;

@Description("Whether a kind of water feature may form in an area.")
public enum IrisHydrologyPermission {
    @Description("The feature may form in this area.")
    ALLOW,
    @Description("The feature never forms in this area.")
    BLOCK
}
