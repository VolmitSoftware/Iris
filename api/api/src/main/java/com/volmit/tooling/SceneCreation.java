package com.volmit.tooling;

import com.volmit.resource.ResourceKind;

import java.util.Objects;

/**
 * How the studio creates and removes the entries of an editable scene view: each entry is a file of {@code kind}, a new
 * one starts as the JSON text {@code starter}, and other files of the pack name an entry {@code key} as
 * {@code referencePrefix + key}, which is how the studio finds what still uses an entry before it is removed.
 */
public record SceneCreation(ResourceKind<?> kind, String starter, String referencePrefix) {
    public SceneCreation {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(starter, "starter");
        Objects.requireNonNull(referencePrefix, "referencePrefix");
    }
}
