package art.arcane.iris.core.runtime;

import art.arcane.iris.core.IrisStartupValidation;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.v1X.NMSBinding1X;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.project.agent.Agent;

/**
 * Attaches the Java agent and installs the server-code injection Iris generation needs, once per boot.
 * <p>
 * Both steps are expensive - a self-attach to the running JVM, then three class retransformations - and
 * neither is needed until a world that Iris generates is about to exist. A server that never loads an Iris
 * world never pays for them. Whoever is about to produce such a world calls {@link #installIfDeferred()}
 * first; it is idempotent, and a failure marks the runtime invalid so the same lock that guards a failed
 * boot-time injection guards a failed deferred one.
 */
public final class RuntimeInjection {
    private static final Object LOCK = new Object();

    private static volatile Outcome outcome;

    private RuntimeInjection() {
    }

    public enum Failure {
        NONE,
        NO_BINDING,
        AGENT,
        INJECTION
    }

    public record Outcome(boolean installed, Failure failure, String lockReason) {
    }

    /**
     * Runs the install if it has not run yet and reports what happened. Never throws.
     */
    public static Outcome install() {
        Outcome current = outcome;
        if (current != null) {
            return current;
        }

        synchronized (LOCK) {
            if (outcome != null) {
                return outcome;
            }
            Outcome resolved = attemptInstall();
            outcome = resolved;
            return resolved;
        }
    }

    /**
     * Installs on the deferred path and locks the runtime when that fails, so the caller's own readiness
     * check refuses the world instead of loading it onto uninstrumented server code.
     */
    public static void installIfDeferred() {
        Outcome resolved = install();
        if (resolved.installed()) {
            return;
        }

        IrisLogging.error("Iris runtime injection is unavailable: " + resolved.lockReason());
        IrisStartupValidation.markRuntimeInvalid(resolved.lockReason());
    }

    public static boolean isInstalled() {
        Outcome current = outcome;
        return current != null && current.installed();
    }

    /**
     * Forgets the result so the next boot in this JVM installs against the binding it is running with.
     */
    public static void reset() {
        synchronized (LOCK) {
            outcome = null;
        }
    }

    static String agentLockReason() {
        return "Iris Java agent is unavailable. Add -javaagent:" + Agent.AGENT_JAR.getPath()
                + " to the JVM arguments before -jar and restart the server.";
    }

    private static Outcome attemptInstall() {
        if (!INMS.isBound() || INMS.get() instanceof NMSBinding1X) {
            return new Outcome(false, Failure.NO_BINDING,
                    "Iris cannot use this server's NMS runtime. Resolve the Server Version or NMS Disabled error above and restart the server.");
        }

        if (!Agent.install()) {
            return new Outcome(false, Failure.AGENT, agentLockReason());
        }

        if (!INMS.get().injectBukkit()) {
            return new Outcome(false, Failure.INJECTION,
                    "Iris runtime injection failed. Resolve the startup errors and restart the server.");
        }

        return new Outcome(true, Failure.NONE, null);
    }
}
