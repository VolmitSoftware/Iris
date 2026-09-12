package art.arcane.iris.structure.jigsaw;

import art.arcane.volmlib.util.documentation.Description;

@Description("Declares the resource compatibility target of an Iris jigsaw structure.")
public enum IrisJigsawCompatibility {
    @Description("Allows Iris-specific connector metadata and placement behavior.")
    IRIS_EXTENDED,

    @Description("Restricts the graph to metadata that can be represented by vanilla jigsaw resources.")
    VANILLA_PORTABLE
}
