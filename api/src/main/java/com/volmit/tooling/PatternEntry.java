package com.volmit.tooling;

import java.util.List;
import java.util.Objects;

/**
 * One entry of a {@link PatternView}'s catalogue.
 *
 * @param key    what the view names the entry by in {@link PatternView#pattern}
 * @param name   what the studio lists it as, such as "Layer 1"
 * @param group  what the studio lists it under, such as its biome's name; blank for none
 * @param depths how many blocks deep the entry can reach, at least 1
 * @param facts  short lines the studio shows with the entry, such as "2 to 4 blocks thick"
 */
public record PatternEntry(String key, String name, String group, int depths, List<String> facts) {
    public PatternEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(group, "group");
        facts = List.copyOf(facts);
        if (key.isBlank()) {
            throw new IllegalArgumentException("A pattern entry needs a key");
        }
        if (depths < 1) {
            throw new IllegalArgumentException("Pattern entry " + key + " reaches " + depths + " blocks deep; an entry reaches at least 1");
        }
    }
}
