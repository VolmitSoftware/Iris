package art.arcane.iris.engine.object.structure.vanilla;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Controls whether an Iris structure placement suppresses its imported native source.")
public enum NativeStructureSuppression {
    @Desc("Leaves native generation enabled alongside this Iris structure placement.")
    NONE,

    @Desc("Suppresses the structure's vanillaSource when used by a validated dimension-level replacement placement.")
    REPLACE_SOURCE
}
