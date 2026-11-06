package com.volmit.validation;

import java.util.Objects;

/** Something wrong with a pack that a {@link Validator} found: how much it matters, what it concerns and what it is.
 * The message is a sentence for the pack's author; the host shows it with its subject.
 */
public record Problem(Severity severity, Subject subject, String message) {
    public Problem {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) throw new IllegalArgumentException("Blank problem message about " + subject);
    }

    /** A problem that stops the pack from loading. */
    public static Problem error(Subject subject, String message) {
        return new Problem(Severity.ERROR, subject, message);
    }

    /** A problem the host logs while the pack still loads. */
    public static Problem warning(Subject subject, String message) {
        return new Problem(Severity.WARNING, subject, message);
    }
}
