package art.arcane.iris.engine.framework.structure;

public enum StructureAssemblyStatus {
    COMPLETE,
    INTENTIONAL_EMPTY,
    FAILED_UNCAPPED,
    FAILED_RULES,
    HARD_CAP;

    public boolean isComplete() {
        return this == COMPLETE || this == INTENTIONAL_EMPTY;
    }

    public boolean isFailure() {
        return !isComplete();
    }
}
