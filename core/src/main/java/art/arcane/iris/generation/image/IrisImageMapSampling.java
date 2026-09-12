package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.documentation.Description;

@Description("The filter used between source pixels")
public enum IrisImageMapSampling {
    NEAREST,
    BILINEAR,
    BICUBIC
}
