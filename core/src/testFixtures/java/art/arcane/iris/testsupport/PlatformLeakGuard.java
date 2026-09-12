package art.arcane.iris.testsupport;


import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.List;

public final class PlatformLeakGuard implements TestRule {
    private PlatformLeakGuard() {
    }

    public static PlatformLeakGuard clean() {
        return new PlatformLeakGuard();
    }

    public static void requireClean(String scope, String phase) {
        boolean bound = IrisRuntimeState.platformBound();
        List<String> services = IrisRuntimeState.registeredServices();

        if (!bound && services.isEmpty()) {
            return;
        }

        IrisRuntimeState.reset();
        throw new AssertionError(describe(scope, phase, bound, services));
    }

    @Override
    public Statement apply(Statement base, Description description) {
        String scope = description.getDisplayName();

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                requireClean(scope, "start");

                try {
                    base.evaluate();
                } finally {
                    requireClean(scope, "end");
                }
            }
        };
    }

    private static String describe(String scope, String phase, boolean bound, List<String> services) {
        StringBuilder message = new StringBuilder("Iris runtime state is dirty at ")
                .append(phase)
                .append(" of ")
                .append(scope)
                .append(':');

        if (bound) {
            message.append(" platform still bound;");
        }

        if (!services.isEmpty()) {
            message.append(" services still registered ").append(services).append(';');
        }

        return message.toString();
    }
}
