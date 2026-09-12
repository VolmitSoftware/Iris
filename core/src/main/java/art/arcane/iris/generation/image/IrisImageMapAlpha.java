package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.documentation.Description;

@Description("How alpha participates in decoded data")
public enum IrisImageMapAlpha {
    IGNORE,
    MASK,
    TRANSPARENT_IS_FALLBACK,
    ERROR
}
