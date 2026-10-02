package art.arcane.iris.testsupport;

import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExpectedToFailUntilFixedRuleTest {
    private static final String KNOWN_BUG = "known bug";
    private static final String BUG_STILL_PRESENT = "bug still present";
    private static final String REMOVAL_REMINDER = "remove @ExpectedToFailUntilFixed: " + KNOWN_BUG;
    private static final Statement PASSING = new Statement() {
        @Override
        public void evaluate() {
        }
    };

    private final ExpectedToFailUntilFixedRule rule = new ExpectedToFailUntilFixedRule();
    private final AssertionError bugStillPresent = new AssertionError(BUG_STILL_PRESENT);
    private final IllegalStateException crash = new IllegalStateException(BUG_STILL_PRESENT);

    @Test
    public void doesAMarkedTestThatStillFailsGetReportedAsSkippedWithItsFailure() {
        assertSame(bugStillPresent, assertThrows(AssumptionViolatedException.class, runMarked(failingWith(bugStillPresent))).getCause());
    }

    @Test
    public void doesAMarkedTestThatPassesFailWithARemovalReminder() {
        assertTrue(messageOfAssertionFailure(runMarked(PASSING)).contains(REMOVAL_REMINDER));
    }

    @Test
    public void doesACrashInAMarkedTestStillFail() {
        assertSame(crash, assertThrows(IllegalStateException.class, runMarked(failingWith(crash))));
    }

    @Test
    public void doesAnUnmarkedFailingTestStillFail() {
        assertSame(bugStillPresent, assertThrows(AssertionError.class, runUnmarked(failingWith(bugStillPresent))));
    }

    @ExpectedToFailUntilFixed(KNOWN_BUG)
    private void markedTestTemplate() {
    }

    private ThrowingRunnable runMarked(Statement test) {
        return () -> rule.apply(test, describe(markerOfTemplate())).evaluate();
    }

    private ThrowingRunnable runUnmarked(Statement test) {
        return () -> rule.apply(test, describe()).evaluate();
    }

    private ExpectedToFailUntilFixed markerOfTemplate() throws NoSuchMethodException {
        return getClass().getDeclaredMethod("markedTestTemplate").getAnnotation(ExpectedToFailUntilFixed.class);
    }

    private static String messageOfAssertionFailure(ThrowingRunnable test) {
        return assertThrows(AssertionError.class, test).getMessage();
    }

    private static Description describe(Annotation... annotations) {
        return Description.createTestDescription(ExpectedToFailUntilFixedRuleTest.class, "example", annotations);
    }

    private static Statement failingWith(Throwable failure) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                throw failure;
            }
        };
    }
}
