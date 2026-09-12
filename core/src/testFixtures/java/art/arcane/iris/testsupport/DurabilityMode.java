package art.arcane.iris.testsupport;


import art.arcane.iris.world.storage.Durability;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class DurabilityMode implements TestRule {
    private static final String RELAXED = "relaxed";

    private final String mode;

    private DurabilityMode(String mode) {
        this.mode = mode;
    }

    public static DurabilityMode relaxed() {
        return new DurabilityMode(RELAXED);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String previous = System.getProperty(Durability.MODE_PROPERTY);
                System.setProperty(Durability.MODE_PROPERTY, mode);

                try {
                    base.evaluate();
                } finally {
                    if (previous == null) {
                        System.clearProperty(Durability.MODE_PROPERTY);
                    } else {
                        System.setProperty(Durability.MODE_PROPERTY, previous);
                    }
                }
            }
        };
    }
}
