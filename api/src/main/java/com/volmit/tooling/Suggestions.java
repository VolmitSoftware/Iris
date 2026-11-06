package com.volmit.tooling;

import java.util.List;

/** Completions for one {@link Argument}, asked while the sender types, so keep it cheap. */
@FunctionalInterface
public interface Suggestions {
    /** Candidate words; the host keeps those that start with what the sender typed. */
    List<String> suggest(CommandContext context);
}
