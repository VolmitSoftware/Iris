package art.arcane.iris.world.safeguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The framed console block Iris prints when it refuses to generate a world. Every platform prints the same shape
 * so a refusal is found the same way in any server log.
 */
public final class GenerationRefusalNotice {
    private static final String RULE = "=".repeat(78);

    private GenerationRefusalNotice() {
    }

    public static List<String> compose(String headline, List<String> causes, List<String> consequences) {
        List<String> lines = new ArrayList<>(causes.size() + consequences.size() + 3);
        lines.add(RULE);
        lines.add(Objects.requireNonNull(headline, "headline"));
        for (int index = 0; index < causes.size(); index++) {
            lines.add((index == 0 ? "Cause: " : "Caused by: ") + causes.get(index));
        }
        lines.addAll(consequences);
        lines.add(RULE);
        return List.copyOf(lines);
    }

    /**
     * Every distinct message in the cause chain, outermost first. A wrapper whose message only repeats its cause
     * (CompletionException, a rethrow with the same text) adds nothing and is skipped.
     */
    public static List<String> causes(Throwable failure) {
        List<String> causes = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && seen.add(current)) {
            Throwable cause = current.getCause();
            String message = current.getMessage();
            boolean repeatsCause = cause != null && cause.toString().equals(message);
            if (!repeatsCause) {
                String text = message == null || message.isBlank() ? current.getClass().getSimpleName() : message.trim();
                if (causes.isEmpty() || !causes.getLast().equals(text)) {
                    causes.add(text);
                }
            }
            current = cause;
        }
        return List.copyOf(causes);
    }

    public static String summary(Throwable failure) {
        return String.join("; ", causes(failure));
    }
}
