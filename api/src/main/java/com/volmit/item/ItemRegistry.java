package com.volmit.item;

import com.volmit.resource.ContentKey;
import java.util.OptionalInt;

/** What a module may ask the platform about items, so that its decisions follow the item they concern. Safe for
 * concurrent use.
 */
public interface ItemRegistry {
    /**
     * The most damage an item of this kind takes before it breaks, 0 for an item that cannot be damaged; empty when no
     * item is known by {@code item}.
     */
    OptionalInt maxDurability(ContentKey item);
}
