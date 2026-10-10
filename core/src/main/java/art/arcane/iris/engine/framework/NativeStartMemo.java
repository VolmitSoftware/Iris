package art.arcane.iris.engine.framework;

import java.util.Objects;

/**
 * What a module noted about a start it placed, as the host keeps it: two texts in the module's own format, one about
 * the start's terrain and one about its foundation. The host never reads them.
 */
public record NativeStartMemo(String terrain, String foundation) {
    public NativeStartMemo {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(foundation, "foundation");
    }
}
