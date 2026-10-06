package art.arcane.iris.engine.object.image;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("How a referenced mask combines with masks before it")
public enum IrisImageMapMaskOperation {
    MULTIPLY,
    MINIMUM,
    MAXIMUM,
    ADD,
    SUBTRACT
}
