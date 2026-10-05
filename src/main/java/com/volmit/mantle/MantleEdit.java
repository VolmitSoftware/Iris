package com.volmit.mantle;

/** Buffered changes across layers, with the parent's bounds and registrations.
 * Reads and visits include buffered replacements/removals over the unchanged parent.
 * Nested edits are unsupported: edit() throws UnsupportedOperationException.
 * The host excludes competing writers; this is not a concurrent or disk transaction.
 */
public interface MantleEdit extends Mantle, AutoCloseable {
    /** Publishes all changes to the parent or leaves it unchanged on failure.
     * Successful commit ends the edit and invalidates its layer views. On failure the
     * edit can only be closed. Repeated commit throws IllegalStateException.
     * This guarantee applies to generation storage, not crash recovery or world writes.
     */
    void commit();

    /** Discards uncommitted changes and invalidates edit views. Idempotent after commit/close. */
    @Override
    void close();
}
