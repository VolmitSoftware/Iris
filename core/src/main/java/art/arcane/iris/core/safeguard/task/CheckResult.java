package art.arcane.iris.core.safeguard.task;

import art.arcane.iris.core.safeguard.Mode;

import java.util.List;

public record CheckResult(Mode mode, String lockReason, List<Diagnostic> diagnostics) {
    public CheckResult {
        if (mode == null) {
            throw new IllegalArgumentException("A startup check result needs a mode");
        }
        if (mode == Mode.UNSTABLE && (lockReason == null || lockReason.isBlank())) {
            throw new IllegalArgumentException("A Danger startup check result needs a lock reason");
        }
        diagnostics = List.copyOf(diagnostics);
    }

    public static CheckResult stable(Diagnostic... diagnostics) {
        return new CheckResult(Mode.STABLE, null, List.of(diagnostics));
    }

    public static CheckResult warning(Diagnostic... diagnostics) {
        return new CheckResult(Mode.WARNING, null, List.of(diagnostics));
    }

    public static CheckResult warning(List<Diagnostic> diagnostics) {
        return new CheckResult(Mode.WARNING, null, diagnostics);
    }

    public static CheckResult danger(String lockReason, Diagnostic... diagnostics) {
        return new CheckResult(Mode.UNSTABLE, lockReason, List.of(diagnostics));
    }

    public void log(boolean withException, boolean withStackTrace) {
        for (Diagnostic diagnostic : diagnostics) {
            diagnostic.log(withException, withStackTrace);
        }
    }
}
