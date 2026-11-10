package art.arcane.iris.engine.framework;

import com.volmit.generation.EngineFailure;

public class GenerationSessionException extends WrongEngineBroException implements EngineFailure {
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
