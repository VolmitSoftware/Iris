package com.volmit.vanilla;

import java.util.Objects;

/** What a module noted about a start it placed, for itself: two texts in the module's own format, one about how the
 * start meets the terrain and one about its foundation. The host keeps the memo with the start for as long as the world
 * does and hands it back whenever it asks the module about the start again ({@link StructureFit.Start#memo()},
 * {@link com.volmit.service.VanillaStructures#reach}), so a start that spans many chunks is finished the way it was
 * begun, even after the pack changed. The host never reads the texts.
 */
public record StartMemo(String terrain, String foundation) {
    public StartMemo {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(foundation, "foundation");
    }
}
