package art.arcane.iris.structure.graph;

public final class StructureGraphValidationException extends IllegalArgumentException {
    public StructureGraphValidationException(String message) {
        super(message);
    }

    public StructureGraphValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
