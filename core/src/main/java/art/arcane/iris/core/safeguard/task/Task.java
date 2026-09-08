package art.arcane.iris.core.safeguard.task;

import art.arcane.iris.core.safeguard.Mode;

import java.util.function.Supplier;

/**
 * One startup check, plus the severity its own failure carries.
 * <p>
 * A check declares whether it is advisory or critical at the point it is written, so nothing downstream has
 * to recognise a check by its id to decide what a failure means. An advisory check that throws is a Warning
 * and leaves the runtime open; a critical one that throws is Danger and locks the runtime with the reason it
 * carries here.
 */
public abstract class Task {
    private final String id;
    private final Mode failureMode;
    private final String failureLockReason;

    protected Task(String id, Mode failureMode, String failureLockReason) {
        this.id = id;
        this.failureMode = failureMode;
        this.failureLockReason = failureLockReason;
    }

    public String getId() {
        return id;
    }

    public Mode failureMode() {
        return failureMode;
    }

    public String failureLockReason() {
        return failureLockReason;
    }

    public abstract CheckResult run();

    /**
     * A check whose failure degrades Iris but leaves it usable.
     */
    public static Task advisory(String id, Supplier<CheckResult> action) {
        return new Task(id, Mode.WARNING, null) {
            @Override
            public CheckResult run() {
                return action.get();
            }
        };
    }

    /**
     * A check whose failure means Iris cannot safely run. {@code throwLockReason} is what an operator is told
     * when the check itself throws rather than returning a result.
     */
    public static Task critical(String id, String throwLockReason, Supplier<CheckResult> action) {
        return new Task(id, Mode.UNSTABLE, throwLockReason) {
            @Override
            public CheckResult run() {
                return action.get();
            }
        };
    }
}
