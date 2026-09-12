package art.arcane.iris.structure.nativegen;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls whether an Iris structure placement suppresses its imported native source.")
public enum NativeStructureSuppression {
    @Description("Leaves native generation enabled alongside this Iris structure placement.")
    NONE,

    @Description("Suppresses the structure's vanillaSource when used by a validated dimension-level replacement placement.")
    REPLACE_SOURCE
}
