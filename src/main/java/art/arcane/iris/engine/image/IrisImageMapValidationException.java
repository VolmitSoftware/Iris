package art.arcane.iris.engine.image;

import java.util.List;

public final class IrisImageMapValidationException extends IllegalArgumentException {
    private final List<String> diagnostics;

    public IrisImageMapValidationException(String diagnostic) {
        this(List.of(diagnostic));
    }

    public IrisImageMapValidationException(List<String> diagnostics) {
        super(message(diagnostics));
        if (diagnostics == null || diagnostics.isEmpty()) {
            throw new IllegalArgumentException("Image-map validation requires at least one diagnostic");
        }
        this.diagnostics = List.copyOf(diagnostics);
    }

    public IrisImageMapValidationException(String diagnostic, Throwable cause) {
        super(diagnostic, cause);
        this.diagnostics = List.of(diagnostic);
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }

    private static String message(List<String> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "Image-map validation failed";
        }
        return String.join("; ", diagnostics);
    }
}
