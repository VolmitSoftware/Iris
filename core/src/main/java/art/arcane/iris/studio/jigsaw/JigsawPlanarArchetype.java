package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;

import java.util.Objects;

public enum JigsawPlanarArchetype {
    BLANK("workcell/blank", JigsawPlanarTopology.BLANK),
    END("workcell/end", JigsawPlanarTopology.NORTH_END),
    STRAIGHT("workcell/straight", JigsawPlanarTopology.NORTH_SOUTH_STRAIGHT),
    CORNER("workcell/corner", JigsawPlanarTopology.NORTH_EAST_CORNER),
    TEE("workcell/tee", JigsawPlanarTopology.NORTH_EAST_WEST_TEE),
    CROSS("workcell/cross", JigsawPlanarTopology.CROSS);

    private final String stableId;
    private final JigsawPlanarTopology canonicalTopology;

    JigsawPlanarArchetype(String stableId, JigsawPlanarTopology canonicalTopology) {
        this.stableId = stableId;
        this.canonicalTopology = canonicalTopology;
    }

    public static JigsawPlanarArchetype fromTopology(JigsawPlanarTopology topology) {
        JigsawPlanarTopology source = Objects.requireNonNull(topology, "Planar topology");
        return switch (source.kind()) {
            case BLANK -> BLANK;
            case END -> END;
            case STRAIGHT -> STRAIGHT;
            case CORNER -> CORNER;
            case TEE -> TEE;
            case CROSS -> CROSS;
        };
    }

    public static JigsawPlanarArchetype fromModel(IrisJigsawWorkcellArchetype archetype) {
        return valueOf(Objects.requireNonNull(archetype, "Planar workcell archetype").name());
    }

    public String stableId() {
        return stableId;
    }

    public JigsawPlanarTopology canonicalTopology() {
        return canonicalTopology;
    }

    public IrisJigsawWorkcellArchetype modelArchetype() {
        return IrisJigsawWorkcellArchetype.valueOf(name());
    }

    public String displayName() {
        return switch (this) {
            case BLANK -> "Blank";
            case END -> "End Cap";
            case STRAIGHT -> "Hallway";
            case CORNER -> "L Junction";
            case TEE -> "T Junction";
            case CROSS -> "Cross Junction";
        };
    }

    public int sourceToCanonicalQuarterTurns(JigsawPlanarTopology sourceTopology) {
        JigsawPlanarTopology source = Objects.requireNonNull(sourceTopology, "Source planar topology");
        if (fromTopology(source) != this) {
            throw new IllegalArgumentException("Topology " + source + " does not belong to archetype " + this);
        }
        for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
            if (source.rotateClockwise(quarterTurns) == canonicalTopology) {
                return quarterTurns;
            }
        }
        throw new IllegalStateException("No canonical rotation exists for topology " + source);
    }

    public int canonicalToSourceQuarterTurns(JigsawPlanarTopology sourceTopology) {
        return Math.floorMod(-sourceToCanonicalQuarterTurns(sourceTopology), 4);
    }
}
