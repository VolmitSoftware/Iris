package com.volmit.entity;

import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;
import java.util.List;
import java.util.Objects;

/**
 * An entity a module decided to spawn, with every random choice made: the host creates it on the platform and applies
 * what is set. Names are as authored in the pack, with {@code &} colour codes.
 *
 * @param type                 the entity type's key, such as {@code minecraft:zombie}; ignored when {@code specialType} is set
 * @param specialType          a mob of another plugin, written {@code PluginName:MobName}, or empty
 * @param reason               the platform's spawn reason by name, or null for a natural spawn
 * @param customName           the name shown above the entity, empty for none
 * @param flags                the settings that every entity kind understands
 * @param equipment            what the entity wears and holds
 * @param passengers           entities that ride this one
 * @param attributes           attribute modifiers to apply
 * @param lootTables           keys of loot tables the entity drops, instead of the platform's drops, when it dies
 * @param lootShuffleSeed      seeds how the drops of {@code lootTables} are shuffled
 * @param leashHolder          the entity that holds this one on a leash, or null
 * @param spawnEffect          an effect to show when the entity appears, or null
 * @param riseOutOfGround      whether the entity slowly climbs out of the ground when players are near
 * @param pandaMainGene        for pandas the main gene by name, or null
 * @param pandaHiddenGene      for pandas the hidden gene by name, or null
 * @param applySettingsToSpecial whether the settings also apply to a mob of another plugin
 * @param commands             console commands to run where the entity appears
 */
public record EntitySpec(String type, String specialType, String reason, String customName, Flags flags, Equipment equipment,
                         List<EntitySpec> passengers, List<ItemAttribute> attributes, List<String> lootTables,
                         long lootShuffleSeed, EntitySpec leashHolder, SpawnEffect spawnEffect, boolean riseOutOfGround,
                         String pandaMainGene, String pandaHiddenGene, boolean applySettingsToSpecial,
                         List<Command> commands) {
    public EntitySpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(specialType, "specialType");
        Objects.requireNonNull(customName, "customName");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(equipment, "equipment");
        passengers = List.copyOf(passengers);
        attributes = List.copyOf(attributes);
        lootTables = List.copyOf(lootTables);
        commands = List.copyOf(commands);
    }

    /** Whether the entity is a mob of another plugin. */
    public boolean isSpecial() {
        return !specialType.isEmpty();
    }

    /** What an entity can be set to be, whatever its kind; the host applies the ones its kind has. */
    public record Flags(boolean customNameVisible, boolean aware, boolean ai, boolean glowing, boolean gravity,
                        boolean invulnerable, boolean silent, boolean pickupItems, boolean removable,
                        boolean keepEntity, boolean baby) {
    }

    /** The items an entity wears and holds; null for an empty slot. */
    public record Equipment(ItemDrop helmet, ItemDrop chestplate, ItemDrop leggings, ItemDrop boots, ItemDrop mainHand,
                            ItemDrop offHand) {
        public static final Equipment NONE = new Equipment(null, null, null, null, null, null);
    }

    /**
     * Sound and particles shown around an entity that appears. Offsets and the randomness of the particle motion are
     * as in an effect of the pack; the host makes the random choices.
     */
    public record SpawnEffect(String sound, int soundDistance, double minPitch, double maxPitch, double volume,
                              String particle, int particleOffset, int particleCount, double altX, double altY,
                              double altZ, boolean randomAltX, boolean randomAltY, boolean randomAltZ, double extra) {
    }

    /**
     * Console commands run when an entity appears. {@code {x}}, {@code {y}} and {@code {z}} stand for its block position.
     *
     * @param commands    the commands
     * @param delay       server ticks to wait before running them
     * @param repeat      whether to run them again and again until the server stops
     * @param repeatDelay server ticks between repeats
     */
    public record Command(List<String> commands, long delay, boolean repeat, long repeatDelay) {
        public Command {
            commands = List.copyOf(commands);
        }
    }
}
