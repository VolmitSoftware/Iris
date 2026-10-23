package com.volmit.entity;

import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;
import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An entity a module decided to spawn, with every random choice made: the host creates it on the platform and applies
 * what is set. Names are as authored in the pack, with {@code &} colour codes.
 *
 * @param type                 the entity type's key, such as {@code minecraft:zombie}; ignored when {@code specialType} is set
 * @param specialType          a mob of another plugin, written {@code PluginName:MobName}; empty for an entity of the platform
 * @param reason               the platform's spawn reason by name; empty for a natural spawn
 * @param customName           the name shown above the entity; empty for none
 * @param flags                the settings that every entity kind understands
 * @param equipment            what the entity wears and holds
 * @param passengers           entities that ride this one
 * @param attributes           attribute modifiers to apply
 * @param lootTables           the pack's loot tables the entity drops, instead of the platform's drops, when it dies
 * @param lootShuffleSeed      seeds how the drops of {@code lootTables} are shuffled
 * @param leashHolder          the entity that holds this one on a leash; empty for none
 * @param spawnEffect          an effect to show when the entity appears; empty for none
 * @param riseOutOfGround      whether the entity slowly climbs out of the ground when players are near
 * @param pandaMainGene        for pandas the main gene by name; empty to leave it to the platform
 * @param pandaHiddenGene      for pandas the hidden gene by name; empty to leave it to the platform
 * @param applySettingsToSpecial whether the settings also apply to a mob of another plugin
 * @param commands             console commands to run where the entity appears
 */
public record EntitySpec(Optional<String> type, Optional<String> specialType, Optional<String> reason, Optional<String> customName,
                         Flags flags, Equipment equipment, List<EntitySpec> passengers, List<ItemAttribute> attributes,
                         List<ResourceId> lootTables, long lootShuffleSeed, Optional<EntitySpec> leashHolder,
                         Optional<SpawnEffect> spawnEffect, boolean riseOutOfGround, Optional<String> pandaMainGene,
                         Optional<String> pandaHiddenGene, boolean applySettingsToSpecial, List<Command> commands) {
    public EntitySpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(specialType, "specialType");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(customName, "customName");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(equipment, "equipment");
        passengers = List.copyOf(passengers);
        attributes = List.copyOf(attributes);
        lootTables = List.copyOf(lootTables);
        Objects.requireNonNull(leashHolder, "leashHolder");
        Objects.requireNonNull(spawnEffect, "spawnEffect");
        Objects.requireNonNull(pandaMainGene, "pandaMainGene");
        Objects.requireNonNull(pandaHiddenGene, "pandaHiddenGene");
        commands = List.copyOf(commands);
    }

    /** Whether the entity is a mob of another plugin. */
    public boolean isSpecial() {
        return specialType.isPresent();
    }

    /** What an entity can be set to be, whatever its kind; the host applies the ones its kind has. */
    public record Flags(boolean customNameVisible, boolean aware, boolean ai, boolean glowing, boolean gravity,
                        boolean invulnerable, boolean silent, boolean pickupItems, boolean removable,
                        boolean keepEntity, boolean baby) {
    }

    /** The items an entity wears and holds; empty for an empty slot. */
    public record Equipment(Optional<ItemDrop> helmet, Optional<ItemDrop> chestplate, Optional<ItemDrop> leggings,
                            Optional<ItemDrop> boots, Optional<ItemDrop> mainHand, Optional<ItemDrop> offHand) {
        public static final Equipment NONE = new Equipment(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());

        public Equipment {
            Objects.requireNonNull(helmet, "helmet");
            Objects.requireNonNull(chestplate, "chestplate");
            Objects.requireNonNull(leggings, "leggings");
            Objects.requireNonNull(boots, "boots");
            Objects.requireNonNull(mainHand, "mainHand");
            Objects.requireNonNull(offHand, "offHand");
        }
    }

    /**
     * Sound and particles shown around an entity that appears. Offsets and the randomness of the particle motion are
     * as in an effect of the pack; the host makes the random choices.
     *
     * @param sound    the platform's sound key; empty for no sound
     * @param particle the platform's particle key; empty for no particles
     */
    public record SpawnEffect(Optional<String> sound, int soundDistance, double minPitch, double maxPitch, double volume,
                              Optional<String> particle, int particleOffset, int particleCount, double altX, double altY,
                              double altZ, boolean randomAltX, boolean randomAltY, boolean randomAltZ, double extra) {
        public SpawnEffect {
            Objects.requireNonNull(sound, "sound");
            Objects.requireNonNull(particle, "particle");
        }
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
