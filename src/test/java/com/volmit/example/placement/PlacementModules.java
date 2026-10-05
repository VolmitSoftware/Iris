package com.volmit.example.placement;

import com.volmit.IrisModule;
import java.util.List;

public final class PlacementModules {
    private PlacementModules() {}

    /** The ported core lifecycle slice plus one third-party module. */
    public static List<IrisModule> all() {
        return List.of(new PrepareModule(), new AnchorModule(), new BlocksModule(),
                new StiltModule(), new SnowModule(), new MossModule());
    }
}
