package art.arcane.iris.engine.framework;

import java.util.Objects;

/** The memo a module gave a start it placed, as the host keeps it: one text the host never reads, not empty. */
public record NativeStartMemo(String text) {
    public NativeStartMemo {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A start memo's text must not be empty");
        }
    }
}
