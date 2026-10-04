package art.arcane.iris.engine.object.image;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("How alpha participates in decoded data")
public enum IrisImageMapAlpha {
    IGNORE,
    MASK,
    TRANSPARENT_IS_FALLBACK,
    ERROR
}
