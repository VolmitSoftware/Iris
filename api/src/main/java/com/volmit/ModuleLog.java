package com.volmit;

/** The log of one module. The host labels every line with the module's id, such as {@code [iris:decorant]}, and writes it
 * to its own log, so a module never needs a logging library of its own and its lines are easy to find. Each module has
 * its own: reach it as {@code context.log()} in stage bodies and generators
 * ({@link com.volmit.generation.InvocationContext#log}), as {@link com.volmit.world.PackWorld#log} in services and
 * lifecycle hooks, and as {@link ModuleSetup#log} during the module's setup.
 *
 * <p>Safe to use from any thread. A message is plain text, not a format string.
 */
public interface ModuleLog {
    /** Detail for people debugging the module. The host writes it only when its debug setting is on, so building the
     * message costs time even when nobody reads it: guard a message that is expensive to build with {@link #debugging()},
     * or leave it out.
     */
    void debug(String message);

    /** Whether {@link #debug} lines are written now, so a module can skip building them, or skip work done only to trace. */
    boolean debugging();

    /** Something worth telling the server owner once in a while, such as what the module did at startup. */
    void info(String message);

    /** Something is wrong or unusual, but the module carries on. */
    void warn(String message);

    /** Warns the first time {@code key} is used on this log and writes {@code message} at {@link #debug} level every
     * time after, for a condition worth stating once that is met per block, per sample or per chunk. Keys belong to the
     * module: two modules can use the same key and each warns once. The key space must be bounded, so never put a
     * coordinate in it; the host stops claiming new keys beyond a few hundred and then only debugs.
     */
    void warnOnce(String key, String message);

    /** Logs {@code message} as an error and reports {@code cause} the way the host reports its own failures, such as
     * to its error tracker. Use it for a failure the module caught but cannot recover from. A null {@code cause}
     * only logs the message.
     */
    void error(String message, Throwable cause);
}
