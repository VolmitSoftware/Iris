package art.arcane.iris.generation.runtime;

/**
 * A hydrology plan could not be read. Water for the chunk can't be known, so no placement scope may skip this.
 */
public final class HydrologyPlanException extends IllegalStateException {
    public HydrologyPlanException(String message, Throwable cause) {
        super(message, cause);
    }
}
