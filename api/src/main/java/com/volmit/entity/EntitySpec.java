package com.volmit.entity;

import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;
import com.volmit.resource.ContentKey;
import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An entity a module decided to spawn, with every random choice made: the host creates it on the platform and applies
 * what is set. A part the entity cannot have, such as equipment on an item frame, is left out. A host may leave a
 * {@link ContentKey.Provided} mob as its provider set it up.
 *
 * @param type       what the entity is: an entity type, such as {@code minecraft:zombie}, or a mob of a provider
 * @param appearance how it looks
 * @param behaviour  how it acts
 * @param equipment  what it wears and holds
 * @param attributes attribute modifiers to apply
 * @param company    the entities that come with it
 * @param drops      what it drops when it dies
 * @param arrival    what happens where it appears
 * @param adjustsProvidedMob whether the rest of the spec also applies to a {@link ContentKey.Provided} mob, which is
 *                   otherwise left as its provider set it up
 * @param extensions values for a particular host, which other hosts ignore; see {@link #extension}
 */
public record EntitySpec(ContentKey type, Appearance appearance, Behaviour behaviour, Equipment equipment,
                         List<ItemAttribute> attributes, Company company, Drops drops, Arrival arrival,
                         boolean adjustsProvidedMob, List<Object> extensions) {
    public EntitySpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(behaviour, "behaviour");
        Objects.requireNonNull(equipment, "equipment");
        attributes = List.copyOf(attributes);
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(drops, "drops");
        Objects.requireNonNull(arrival, "arrival");
        extensions = List.copyOf(extensions);
    }

    /** The extension of type {@code type} the module added for its host; empty when it added none. */
    public <T> Optional<T> extension(Class<T> type) {
        return extensions.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    /**
     * How the entity looks.
     *
     * @param customName      the name shown above the entity, as authored in the pack with {@code &} colour codes; empty for none
     * @param nameVisible     whether the name shows even when nobody looks at the entity
     * @param pandaMainGene   for a panda, its main gene by the platform's name for it; empty to leave it to the platform
     * @param pandaHiddenGene for a panda, its hidden gene by the platform's name for it; empty to leave it to the platform
     */
    public record Appearance(Optional<String> customName, boolean nameVisible, boolean glowing, boolean baby,
                             Optional<String> pandaMainGene, Optional<String> pandaHiddenGene) {
        public static final Appearance PLAIN = new Appearance(Optional.empty(), false, false, false, Optional.empty(),
                Optional.empty());

        public Appearance {
            Objects.requireNonNull(customName, "customName");
            Objects.requireNonNull(pandaMainGene, "pandaMainGene");
            Objects.requireNonNull(pandaHiddenGene, "pandaHiddenGene");
        }
    }

    /**
     * How the entity acts; the host applies what the entity's kind has.
     *
     * @param aware       whether a mob notices its surroundings
     * @param ai          whether a mob follows its goals
     * @param removable   whether the entity despawns when far from players
     * @param keepEntity  whether the entity is never culled
     */
    public record Behaviour(boolean aware, boolean ai, boolean gravity, boolean invulnerable, boolean silent,
                            boolean pickupItems, boolean removable, boolean keepEntity) {
        public static final Behaviour NATURAL = new Behaviour(true, true, true, false, false, false, false, false);
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
     * The entities that come with this one.
     *
     * @param passengers  entities that ride this one
     * @param leashHolder the entity that holds this one on a leash; empty for none
     */
    public record Company(List<EntitySpec> passengers, Optional<EntitySpec> leashHolder) {
        public static final Company NONE = new Company(List.of(), Optional.empty());

        public Company {
            passengers = List.copyOf(passengers);
            Objects.requireNonNull(leashHolder, "leashHolder");
        }
    }

    /**
     * What the entity drops when it dies.
     *
     * @param lootTables  the pack's loot tables it drops instead of the platform's drops; none keeps the platform's
     * @param shuffleSeed seeds how the drops of {@code lootTables} are shuffled
     */
    public record Drops(List<ResourceId> lootTables, long shuffleSeed) {
        public static final Drops PLATFORM = new Drops(List.of(), 0L);

        public Drops {
            lootTables = List.copyOf(lootTables);
        }
    }

    /**
     * What happens where the entity appears.
     *
     * @param effect         an effect to show; empty for none
     * @param commands       console commands to run
     * @param reason         why the entity appears, by the platform's name for the reason, such as {@code COMMAND}; empty
     *                       for a natural spawn
     * @param risesFromGround whether the entity slowly climbs out of the ground when players are near
     */
    public record Arrival(Optional<SpawnEffect> effect, List<Command> commands, Optional<String> reason,
                          boolean risesFromGround) {
        public static final Arrival QUIET = new Arrival(Optional.empty(), List.of(), Optional.empty(), false);

        public Arrival {
            Objects.requireNonNull(effect, "effect");
            commands = List.copyOf(commands);
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Sound and particles shown around an entity that appears. The host makes the random choices: where in range the
     * sound plays, its pitch, and the particles' random motion.
     */
    public record SpawnEffect(Optional<Sound> sound, Optional<Burst> particles) {
        public SpawnEffect {
            Objects.requireNonNull(sound, "sound");
            Objects.requireNonNull(particles, "particles");
        }

        /**
         * A sound played near the entity, at a pitch between {@code minPitch} and {@code maxPitch}.
         *
         * @param key      the sound, such as {@code minecraft:entity.zombie.ambient}
         * @param distance how many blocks from the entity, at most, along each axis it seems to come from
         */
        public record Sound(ResourceId key, int distance, double minPitch, double maxPitch, double volume) {
            public Sound {
                Objects.requireNonNull(key, "key");
            }
        }

        /**
         * Particles shown around the entity.
         *
         * @param key    the particle, such as {@code minecraft:smoke}
         * @param offset how many blocks up, at most, they appear
         * @param count  how many particles; none makes {@code motion} their motion
         * @param extra  an extra value some particles take; 0 for none
         */
        public record Burst(ResourceId key, int offset, int count, Motion motion, double extra) {
            public Burst {
                Objects.requireNonNull(key, "key");
                Objects.requireNonNull(motion, "motion");
            }
        }

        /** How far the particles spread or move along each axis; a random axis takes a random amount between minus and plus its value. */
        public record Motion(double x, double y, double z, boolean randomX, boolean randomY, boolean randomZ) {
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
