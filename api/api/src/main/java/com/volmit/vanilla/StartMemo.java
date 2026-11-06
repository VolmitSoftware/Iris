package com.volmit.vanilla;

import java.util.Objects;

/** What a module noted about a start it placed, for itself: one text in the module's own format. The host keeps the memo
 * with the start for as long as the world does and hands it back whenever it asks the module about the start again
 * ({@link StructureFit.Start#memo()}, {@link com.volmit.service.VanillaStructures#reach}), so a start that spans many
 * chunks is finished the way it was begun, even after the pack changed. The host never reads the text, which is not
 * empty: a start the module noted nothing about has no memo.
 */
public record StartMemo(String text) {
    public StartMemo {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A start memo's text must not be empty; give the start no memo instead");
        }
    }
}
