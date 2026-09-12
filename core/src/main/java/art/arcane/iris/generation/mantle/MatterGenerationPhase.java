package art.arcane.iris.generation.mantle;

public enum MatterGenerationPhase {
    ALL,
    TERRAIN,
    CONTENT;

    public boolean includes(MantleComponent component) {
        return this == ALL || component.getGenerationPhase() == this;
    }
}
