package art.arcane.iris.testsupport;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class ExpectedToFailUntilFixedRule implements TestRule {
    @Override
    public Statement apply(Statement test, Description description) {
        ExpectedToFailUntilFixed marker = markerOf(description);
        return marker == null ? test : new ExpectedFailure(test, marker.value());
    }

    private static ExpectedToFailUntilFixed markerOf(Description description) {
        return description.getAnnotation(ExpectedToFailUntilFixed.class);
    }

    private static final class ExpectedFailure extends Statement {
        private final Statement test;
        private final String reason;

        private ExpectedFailure(Statement test, String reason) {
            this.test = test;
            this.reason = reason;
        }

        @Override
        public void evaluate() throws Throwable {
            try {
                test.evaluate();
            } catch (AssertionError stillFailing) {
                throw reportAsSkipped(stillFailing);
            }
            throw new AssertionError("Passes now, remove @ExpectedToFailUntilFixed: " + reason);
        }

        private AssumptionViolatedException reportAsSkipped(AssertionError stillFailing) {
            return new AssumptionViolatedException("Still failing until fixed: " + reason, stillFailing);
        }
    }
}
