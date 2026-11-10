package com.volmit.tooling;

import com.volmit.tooling.world.ToolPlayer;
import java.util.Optional;

/**
 * Who sent a command. Safe from any thread: the host delivers messages on the thread the platform needs. Messages are
 * plain text, and the host styles them; there is no colour and no translation.
 */
public interface Sender {
    /** The sender's name as the platform shows it, such as a player's name or "Server". */
    String name();

    /** A plain line; the host styles it as information. */
    void info(String message);

    /** A plain line; the host styles it as a success. */
    void success(String message);

    /** A plain line; the host styles it as an error. */
    void error(String message);

    /** The platform's own sender as {@code type}, such as a Bukkit {@code Player}; empty when it is not one. For platform parts. */
    <T> Optional<T> extension(Class<T> type);

    /** The sender as a player of the platform; empty for the console, a command block, or a platform without tool worlds. */
    Optional<ToolPlayer> player();
}
