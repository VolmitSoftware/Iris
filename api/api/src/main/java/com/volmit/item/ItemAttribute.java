package com.volmit.item;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** An attribute modifier of an {@link ItemDrop} or an entity, with the amount the module decided. The host creates a
 * modifier under an id it makes unique from {@code name}, for every equipment slot.
 *
 * @param attribute the attribute, such as {@code minecraft:max_health}
 * @param name      identifies the modifier among those of one item or entity; not shown to players
 */
public record ItemAttribute(ResourceId attribute, String name, Operation operation, double amount) {
    public ItemAttribute {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(operation, "operation");
    }

    /** How the amount changes the attribute's value, as the platform's attribute modifiers do. */
    public enum Operation {
        /** Adds the amount to the base value. */
        ADD_VALUE,
        /** Adds the base value times the amount. */
        ADD_MULTIPLIED_BASE,
        /** Multiplies the value, after the other modifiers, by one plus the amount. */
        ADD_MULTIPLIED_TOTAL
    }
}
