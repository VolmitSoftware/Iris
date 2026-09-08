package art.arcane.iris.core.safeguard.task;

import art.arcane.iris.core.safeguard.Mode;

import java.util.function.Supplier;

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

    public static Task advisory(String id, Supplier<CheckResult> action) {
        return new Task(id, Mode.WARNING, null) {
            @Override
            public CheckResult run() {
                return action.get();
            }
        };
    }

    public static Task critical(String id, String throwLockReason, Supplier<CheckResult> action) {
        return new Task(id, Mode.UNSTABLE, throwLockReason) {
            @Override
            public CheckResult run() {
                return action.get();
            }
        };
    }
}
