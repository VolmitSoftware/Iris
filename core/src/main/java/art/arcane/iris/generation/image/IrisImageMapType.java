package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.documentation.Description;

@Description("How Iris decodes each source pixel")
public enum IrisImageMapType {
    GRAYSCALE_HEIGHT,
    RGB_HEIGHT,
    COLOR_MAP,
    BINARY_MASK,
    GRAYSCALE_MASK,
    ALPHA_MASK
}
