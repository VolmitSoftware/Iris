package com.volmit.generation;

/** Marks an exception that says the engine, not one piece of content, failed: a closed session, a stopping runtime. The
 * placement code that skips a failed piece lets it through instead; see {@link GenerationFailures}.
 */
public interface EngineFailure {
}
