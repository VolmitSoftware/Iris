package com.volmit.tooling;

/**
 * What a {@link PlatformStructures} reader throws when the platform cannot answer, such as a server version whose
 * registries it cannot read. The message is written for the sender who ran the tool, so a tool shows it as it is.
 */
public final class StructureAccessException extends RuntimeException {
    private final boolean retryable;

    /**
     * @param message   what went wrong, as the sender should read it
     * @param retryable whether asking again later may succeed, as for a registry that was not ready yet
     */
    public StructureAccessException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public StructureAccessException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** Whether asking again later may succeed. */
    public boolean retryable() {
        return retryable;
    }
}
