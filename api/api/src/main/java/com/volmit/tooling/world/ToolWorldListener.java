package com.volmit.tooling.world;

import com.volmit.geometry.BlockPosition;
import java.util.Optional;

/**
 * What a tool hears about its world. Every method has a default that does nothing, so a tool implements only the ones
 * it needs.
 *
 * <p>Threads. A call runs on the thread that owns the player or the position it is about, and the host waits for it:
 * return quickly, and hand slow work to another thread. The host calls only for the tool's own world. A listener that
 * throws is logged by the host and counts as having answered with the method's default.
 *
 * <p>The methods that return a boolean decide whether the host lets the thing happen: {@code false} cancels it.
 */
public interface ToolWorldListener {
    /** A player came into the world: joined the server in it, teleported or changed world into it, or respawned in it. Once for each stay. */
    default void entered(ToolPlayer player) {
    }

    /** A player left the world: quit, teleported or changed world out of it. Once for each stay. */
    default void left(ToolPlayer player) {
    }

    /** A player moved to another block of the world; not called for moves inside one block. */
    default void moved(ToolPlayer player, BlockPosition to) {
    }

    /** A player started or stopped sneaking. */
    default void sneakToggled(ToolPlayer player, boolean sneaking) {
    }

    /** A player used their hand. Return {@code false} to cancel what the platform would do with it, such as opening a container or placing a block. */
    default boolean used(ToolPlayer player, ToolUse use) {
        return true;
    }

    /** Something is about to change blocks. Return {@code false} to cancel it. The default freezes the world. */
    default boolean blockChanging(BlockChange change) {
        return false;
    }

    /**
     * A command is about to run: {@code line} is what was typed without the leading slash. {@code sender} is empty for the
     * console, which is asked on behalf of every tool world that is open. Return {@code false} to cancel it.
     */
    default boolean command(Optional<ToolPlayer> sender, String line) {
        return true;
    }

    /** A chunk of the world finished loading, painted or read back. */
    default void chunkLoaded(int chunkX, int chunkZ) {
    }

    /** The world was unloaded and is gone; nothing can be done with it any more. */
    default void closed() {
    }
}
