package art.arcane.iris.core.safeguard.task;

import java.util.List;

public class ValueWithDiagnostics<T> {
    private final T value;
    private final List<Diagnostic> diagnostics;

    public ValueWithDiagnostics(T value, List<Diagnostic> diagnostics) {
        this.value = value;
        this.diagnostics = List.copyOf(diagnostics);
    }

    public ValueWithDiagnostics(T value, Diagnostic... diagnostics) {
        this.value = value;
        this.diagnostics = List.of(diagnostics);
    }

    public T getValue() {
        return value;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public void log() {
        log(true, false);
    }

    public void log(boolean withException) {
        log(withException, false);
    }

    public void log(boolean withException, boolean withStackTrace) {
        for (Diagnostic diagnostic : diagnostics) {
            diagnostic.log(withException, withStackTrace);
        }
    }
}
