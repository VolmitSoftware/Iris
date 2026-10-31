package art.arcane.iris.probe;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class PaperRegistryIsolation implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (PaperRegistryScope scope = PaperRegistryScope.open()) {
                    base.evaluate();
                }
            }
        };
    }
}
