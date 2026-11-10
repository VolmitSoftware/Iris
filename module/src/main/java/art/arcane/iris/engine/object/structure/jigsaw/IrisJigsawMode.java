package art.arcane.iris.engine.object.structure.jigsaw;

import com.volmit.config.Desc;

@Desc("Controls the topology used by an Iris jigsaw structure.")
public enum IrisJigsawMode {
    @Desc("A cell-aligned planar jigsaw intended for villages, paths, and other two-dimensional layouts.")
    PLANAR_JIGSAW,

    @Desc("A freeform spatial connector graph intended for multi-level and three-dimensional structures.")
    SPATIAL_JIGSAW
}
