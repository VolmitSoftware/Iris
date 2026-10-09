package com.volmit.service;

import com.volmit.block.BlockState;
import com.volmit.item.ItemDrop;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import java.util.List;
import java.util.Objects;

/**
 * The loot a module gives containers and mobs. The host asks in two steps so that it, and plugins listening to it, can
 * see and change which tables are used: {@link #tables} names the tables a container draws from, {@link #items} turns
 * one table into items. The host asks every provider and puts the answers together; a provider answers {@link #items}
 * only for the tables it knows. A container is filled with items the host creates on the platform, in the order of the
 * tables.
 */
@Service(Providers.ALL)
public interface Loot {
    /**
     * The tables the container at {@code site} draws from, in the order their items are added. Empty when the module
     * gives that container nothing.
     */
    List<Source> tables(PackWorld world, Site site);

    /** The items table {@code key} gives at {@code site}; empty for a table this module does not know. */
    List<ItemDrop> items(PackWorld world, Site site, String key);

    /**
     * Where loot is given.
     *
     * @param position          the container, or the mob's spawn spot: world X and Z, Y the height above the dimension's
     *                          bottom
     * @param container         the container's block when it is a storage chest the module may give the loot of the object
     *                          placed there, null for any other spot
     * @param nativeLootDefined whether the platform already gave the container a loot table of its own
     * @param debug             whether items should say where they came from ({@link ItemDrop#source()})
     */
    record Site(BlockPosition position, BlockState container, boolean nativeLootDefined, boolean debug) {
        public Site {
            Objects.requireNonNull(position, "position");
        }
    }

    /** Where a container's items come from. */
    sealed interface Source permits Table, PlatformTable {
    }

    /** A loot table of the pack, named by its key as packs write it ({@code dungeon/chest}). */
    record Table(String key) implements Source {
        public Table {
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * A loot table of the platform, such as {@code minecraft:chests/simple_dungeon}. The host draws it with {@code seed}
     * so that the same container always gets the same items.
     */
    record PlatformTable(String key, long seed) implements Source {
        public PlatformTable {
            Objects.requireNonNull(key, "key");
        }
    }
}
