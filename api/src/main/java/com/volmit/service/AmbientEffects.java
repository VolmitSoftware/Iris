package com.volmit.service;

import com.volmit.entity.EntitySpec;
import com.volmit.geometry.BlockPosition;
import com.volmit.world.PackWorld;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The ambient effects of an area: sounds, particles, potion effects and commands that play around the players who stay
 * in it. The host asks, now and then, for the players it keeps; the module says which effects of the area are due, and
 * every random choice that goes with them (where the sound seems to come from, how the particles scatter, how long the
 * potion lasts), so that the host only has to carry them out. An effect is due only now and then, as its interval and
 * chance say, so most answers are empty. The host asks every provider and carries out all answers.
 */
@Service(Providers.ALL)
public interface AmbientEffects {
    /**
     * The effects due for a player standing at {@code at}.
     *
     * @param at world X and Z, Y the height above the dimension's bottom
     */
    List<Effect> due(PackWorld world, BlockPosition at);

    /**
     * One effect that plays now. The host carries out the parts it has, in the order sound, particles, commands, potion.
     * A part the platform lacks, such as an unknown sound, is skipped.
     *
     * @param sound     a sound to play; empty for none
     * @param particles particles to show; empty for none
     * @param commands  console commands to run, none when the effect has no commands that may run now
     * @param potion    a potion effect to give; empty for none
     */
    record Effect(Optional<Sound> sound, Optional<Particles> particles, List<Command> commands, Optional<Potion> potion) {
        public Effect {
            Objects.requireNonNull(sound, "sound");
            Objects.requireNonNull(particles, "particles");
            commands = List.copyOf(commands);
            Objects.requireNonNull(potion, "potion");
        }
    }

    /**
     * A sound played to the player.
     *
     * @param key     the platform's sound key
     * @param offsetX blocks from the player where the sound seems to come from, as the other offsets
     * @param volume  the sound's volume
     * @param pitch   the sound's pitch
     */
    record Sound(String key, int offsetX, int offsetY, int offsetZ, float volume, float pitch) {
        public Sound {
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * Particles shown to the player. They appear {@code forward} blocks ahead of the player, {@code sideways} blocks to
     * the right (negative for the left) and on the surface at that spot, one block up, moved by {@code jitterX} and
     * {@code jitterZ} and by {@code lift} blocks up.
     *
     * @param key     the platform's particle key
     * @param count   how many particles; none makes {@code altX}, {@code altY} and {@code altZ} their motion
     * @param altX    the particles' offset or motion, as the other two
     * @param extra   an extra value some particles take; 0 for none
     */
    record Particles(String key, double forward, double sideways, double jitterX, double jitterZ, int lift, int count,
                     double altX, double altY, double altZ, double extra) {
        public Particles {
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * A command to run where the effect plays.
     *
     * @param command the commands, their delay and repeats; {@code {x}}, {@code {y}} and {@code {z}} stand for the block
     *                position
     * @param offsetX blocks from the player where the command runs, as the other two
     */
    record Command(EntitySpec.Command command, double offsetX, double offsetY, double offsetZ) {
        public Command {
            Objects.requireNonNull(command, "command");
        }
    }

    /**
     * A potion effect for the player. A player who has the same potion effect with a stronger amplifier keeps it.
     *
     * @param key      the platform's potion effect key
     * @param strength the amplifier
     * @param ticks    how long it lasts, in server ticks
     */
    record Potion(String key, int strength, int ticks) {
        public Potion {
            Objects.requireNonNull(key, "key");
        }
    }
}
