package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.documentation.Description;

@Description("How a color absent from a color-map legend is handled")
public enum IrisImageMapUnknownColor {
    ERROR,
    FALLBACK,
    IGNORE
}
