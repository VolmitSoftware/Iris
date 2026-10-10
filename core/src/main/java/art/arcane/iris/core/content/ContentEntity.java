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
 * An entity a module decided on, in host terms: what {@code com.volmit.entity.EntitySpec} says, for adapters that cannot
 * name IrisApi types. Everything random was decided by the module; the adapter creates and configures the entity.
 */
public record ContentEntity(String type, String specialType, String reason, String customName, Flags flags, Equipment equipment,
                            List<ContentEntity> passengers, List<ContentItem.Attribute> attributes, List<String> lootTables,
                            long lootShuffleSeed, ContentEntity leashHolder, SpawnEffect spawnEffect, boolean riseOutOfGround,
                            String pandaMainGene, String pandaHiddenGene, boolean applySettingsToSpecial,
                            List<Command> commands) {
    /** Whether the entity is a mob of another plugin. */
    public boolean isSpecial() {
        return !specialType.isEmpty();
    }

    /** What an entity can be set to be, whatever its kind. */
    public record Flags(boolean customNameVisible, boolean aware, boolean ai, boolean glowing, boolean gravity,
                        boolean invulnerable, boolean silent, boolean pickupItems, boolean removable,
                        boolean keepEntity, boolean baby) {
    }

    /** The items an entity wears and holds; null for an empty slot. */
    public record Equipment(ContentItem helmet, ContentItem chestplate, ContentItem leggings, ContentItem boots,
                            ContentItem mainHand, ContentItem offHand) {
    }

    /** Sound and particles shown around an entity that appears; the adapter makes the random choices of the particle motion. */
    public record SpawnEffect(String sound, int soundDistance, double minPitch, double maxPitch, double volume,
                              String particle, int particleOffset, int particleCount, double altX, double altY,
                              double altZ, boolean randomAltX, boolean randomAltY, boolean randomAltZ, double extra) {
    }

    /** Console commands run when an entity appears; {@code {x}}, {@code {y}} and {@code {z}} stand for its block position. */
    public record Command(List<String> commands, long delay, boolean repeat, long repeatDelay) {
    }
}
