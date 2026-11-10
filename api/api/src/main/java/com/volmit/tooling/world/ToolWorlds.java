package com.volmit.tooling.world;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The tool worlds of one pack: worlds a tool opens to show or edit what it builds, such as a jigsaw studio. Reached
 * through {@link com.volmit.tooling.ToolPack#worlds()}. A platform without tool worlds still answers: {@link #open}
 * then returns a failed future and {@link #find} an empty result.
 *
 * <p>Safe to call from any thread, the server's main thread included.
 */
public interface ToolWorlds {
    /**
     * Opens a world whose chunks come only from {@code spec.painter()}. The future completes, on a host thread, when
     * players can enter the world. It fails with {@link IllegalStateException} when a tool world of that name is open
     * already, with {@link ToolWorldDenied} when the host refuses the pack a world (its message says why), and with the
     * cause when the platform cannot create the world; a failed open leaves no world behind.
     * The host closes every tool world it still has open when it shuts down: it hears {@link ToolWorldListener#closing}
     * of each and waits for it before {@link ToolWorldListener#closed}.
     *
     * <p>A tool world has no natural mob spawning, weather, daylight cycle, portals, vanilla structures or features.
     */
    CompletableFuture<ToolWorld> open(ToolWorldSpec spec);

    /** The open tool world of this pack called {@code name}; empty when none is open or it closed. */
    Optional<ToolWorld> find(String name);
}
