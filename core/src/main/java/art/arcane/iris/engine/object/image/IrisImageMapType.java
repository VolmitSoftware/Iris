package art.arcane.iris.engine.object.image;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("How Iris decodes each source pixel")
public enum IrisImageMapType {
    GRAYSCALE_HEIGHT,
    RGB_HEIGHT,
    COLOR_MAP,
    BINARY_MASK,
    GRAYSCALE_MASK,
    ALPHA_MASK
}
