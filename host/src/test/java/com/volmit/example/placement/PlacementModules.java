package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.host.DiscoveredModule;
import java.util.List;

public final class PlacementModules {
    private PlacementModules() {}

    /** The ported core lifecycle slice plus one third-party module. */
    public static List<IrisModule> all() {
        return List.of(new PrepareModule(), new AnchorModule(), new BlocksModule(),
                new StiltModule(), new SnowModule(), new MossModule());
    }

    /** {@link #all()} as modules shipped with the host. */
    public static List<DiscoveredModule> builtIns() {
        return all().stream().map(DiscoveredModule::builtIn).toList();
    }
}
