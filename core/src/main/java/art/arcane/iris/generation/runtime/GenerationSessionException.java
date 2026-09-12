package art.arcane.iris.generation.runtime;

public class GenerationSessionException extends WrongEngineBroException {
    private final boolean expectedTeardown;

    public GenerationSessionException(String message) {
        this(message, false);
    }

    public GenerationSessionException(String message, boolean expectedTeardown) {
        super(message);
        this.expectedTeardown = expectedTeardown;
    }

    public boolean isExpectedTeardown() {
        return expectedTeardown;
    }
}
