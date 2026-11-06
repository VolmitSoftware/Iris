package art.arcane.iris.engine.object.structure.jigsaw;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Declares the resource compatibility target of an Iris jigsaw structure.")
public enum IrisJigsawCompatibility {
    @Desc("Allows Iris-specific connector metadata and placement behavior.")
    IRIS_EXTENDED,

    @Desc("Restricts the graph to metadata that can be represented by vanilla jigsaw resources.")
    VANILLA_PORTABLE
}
