package com.volmit.item;

import java.util.Objects;

/** An attribute modifier of an {@link ItemDrop} or an entity, with the amount the module decided. The host creates a
 * modifier under an id it makes unique from {@code name}, for every equipment slot.
 *
 * @param attribute the platform attribute's key, such as {@code max_health} or {@code minecraft:armor}
 * @param name      identifies the modifier among those of one item or entity; not shown to players
 * @param operation {@code ADD_NUMBER}, {@code ADD_SCALAR} or {@code MULTIPLY_SCALAR_1}
 */
public record ItemAttribute(String attribute, String name, String operation, double amount) {
    public ItemAttribute {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(operation, "operation");
    }
}
