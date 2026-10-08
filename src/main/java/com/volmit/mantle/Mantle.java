package com.volmit.mantle;

/**
 * Invocation-scoped access to typed mantle data. X and Z are world block coordinates; Y is
 * the height above the dimension's bottom (0 = bottom), so it is never negative. The host translates
 * them to storage coordinates.
 *
 * <p>The host owns chunk acquisition, release, persistence and the permitted access area.
 * Out-of-area access throws IndexOutOfBoundsException. Access after the invocation ends
 * throws IllegalStateException. Do not retain this interface or use it from detached tasks.
 * Concurrent use of one instance is not supported unless the host explicitly permits it.
 *
 * <p>Only registered layers are supported. Unknown IDs or mismatched registered types throw
 * IllegalArgumentException. Null arguments are rejected. Layer views share this view's lifetime.
 *
 * <p>Writes affect generation storage, not necessarily the current chunk Hunk or live world.
 * Direct writes have no rollback guarantee. Use edit() to buffer multi-layer changes.
 * The host must serialize overlapping writes; read-modify-write is not independently atomic.
 */
public interface Mantle {
    BlockBox bounds();

    <T> MantleLayer<T> layer(MantleKey<T> key);

    /** Only one edit may be active per view. Parent reads remain allowed, but parent
     * mutations are prohibited until the edit ends (IllegalStateException).
     */
    MantleEdit edit();
}
