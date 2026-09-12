package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.documentation.Description;

@Description("How coordinates outside an image are resolved")
public enum IrisImageMapOutOfBounds {
    FALLBACK,
    CLAMP,
    REPEAT,
    MIRROR,
    ERROR
}
