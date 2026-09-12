package art.arcane.iris.structure.jigsaw;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls the topology used by an Iris jigsaw structure.")
public enum IrisJigsawMode {
    @Description("A cell-aligned planar jigsaw intended for villages, paths, and other two-dimensional layouts.")
    PLANAR_JIGSAW,

    @Description("A freeform spatial connector graph intended for multi-level and three-dimensional structures.")
    SPATIAL_JIGSAW
}
