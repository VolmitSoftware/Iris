package com.volmit.tooling;

/**
 * What a command does. The host runs it on a worker thread, never on the server's main thread or a region thread, so it
 * may block; to touch the world it asks the platform for the right thread. What it throws reaches the sender as an error
 * and the module's log.
 */
@FunctionalInterface
public interface CommandAction {
    void run(CommandContext context) throws Exception;
}
