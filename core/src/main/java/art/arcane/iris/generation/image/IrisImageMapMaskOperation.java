package art.arcane.iris.generation.image;

import art.arcane.volmlib.util.documentation.Description;

@Description("How a referenced mask combines with masks before it")
public enum IrisImageMapMaskOperation {
    MULTIPLY,
    MINIMUM,
    MAXIMUM,
    ADD,
    SUBTRACT
}
