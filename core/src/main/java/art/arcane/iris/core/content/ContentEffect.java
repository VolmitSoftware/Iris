/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.content;

import java.util.List;

/**
 * An ambient effect that plays now, in host terms: what {@code com.volmit.service.AmbientEffects.Effect} says. The
 * adapter carries out the parts it has, in the order sound, particles, commands, potion.
 */
public record ContentEffect(Sound sound, Particles particles, List<Command> commands, Potion potion) {
    /** A sound, with blocks offset from the player and the volume and pitch decided. */
    public record Sound(String key, int offsetX, int offsetY, int offsetZ, float volume, float pitch) {
    }

    /** Particles {@code forward} blocks ahead of the player and {@code sideways} to the right, on the surface there. */
    public record Particles(String key, double forward, double sideways, double jitterX, double jitterZ, int lift, int count,
                            double altX, double altY, double altZ, double extra) {
    }

    /** Commands run offset from the player. */
    public record Command(ContentEntity.Command command, double offsetX, double offsetY, double offsetZ) {
    }

    /** A potion effect for the player; one with a stronger amplifier already on the player stays. */
    public record Potion(String key, int strength, int ticks) {
    }
}
