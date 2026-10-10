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

package art.arcane.iris.builtin.spawn;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

/**
 * A sound, particles, potion effect or commands that play now and then around the players in an area. The same
 * settings make the effect an entity shows when it appears ({@link EntityDefinition#spawnEffect()}).
 */
@Snippet("effect")
@Desc("An iris effect")
public final class AmbientEffect {
    @Ref(Reference.POTION_EFFECT)
    @Desc("The potion effect to apply in this area")
    private String potionEffect = "";

    @Desc("The particle effect to apply in the area")
    private String particleEffect = null;

    @MinNumber(-32)
    @MaxNumber(32)
    @Desc("Randomly offset from the surface to this surface+value")
    private int particleOffset = 0;

    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt x, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double particleAltX = 0;

    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt y, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double particleAltY = 0;

    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt z, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double particleAltZ = 0;

    @Desc("Randomize the altX by -altX to altX")
    private boolean randomAltX = true;

    @Desc("Randomize the altY by -altY to altY")
    private boolean randomAltY = false;

    @Desc("Randomize the altZ by -altZ to altZ")
    private boolean randomAltZ = true;

    @Desc("The sound to play")
    private String sound = null;

    @MinNumber(0)
    @MaxNumber(512)
    @Desc("The max distance from the player the sound will play")
    private int soundDistance = 12;

    @MinNumber(0.01)
    @MaxNumber(1.99)
    @Desc("The minimum sound pitch")
    private double minPitch = 0.5D;

    @MinNumber(0.01)
    @MaxNumber(1.99)
    @Desc("The max sound pitch")
    private double maxPitch = 1.5D;

    @MinNumber(0.001)
    @MaxNumber(512)
    @Desc("The sound volume.")
    private double volume = 1.5D;

    @MinNumber(0)
    @MaxNumber(512)
    @Desc("The particle count. Try setting to zero for using the alt xyz to a motion value instead of an offset")
    private int particleCount = 0;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("How far away from the player particles can play")
    private int particleDistance = 20;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("How wide the particles can play (player's view left and right) RADIUS")
    private int particleDistanceWidth = 24;

    @Desc("An extra value for some particles... Which bukkit doesn't even document.")
    private double extra = 0;

    @MinNumber(-1)
    @MaxNumber(1024)
    @Desc("The Potion Strength or -1 to disable")
    private int potionStrength = -1;

    @MinNumber(1)
    @Desc("The max time the potion will last for")
    private int potionTicksMax = 155;

    @MinNumber(1)
    @Desc("The min time the potion will last for")
    private int potionTicksMin = 75;

    @Required
    @MinNumber(0)
    @Desc("The effect interval in milliseconds")
    private int interval = 150;

    @MinNumber(0)
    @MaxNumber(16)
    @Desc("The effect distance start away")
    private int particleAway = 5;

    @Required
    @MinNumber(1)
    @Desc("The chance is 1 in CHANCE per interval")
    private int chance = 50;

    @Desc("Run commands, with configurable location parameters")
    private EffectCommands commandRegistry = null;

    public AmbientEffect() {
    }

    public String potionEffect() {
        return potionEffect;
    }

    public String particleEffect() {
        return particleEffect;
    }

    public int particleOffset() {
        return particleOffset;
    }

    public double particleAltX() {
        return particleAltX;
    }

    public double particleAltY() {
        return particleAltY;
    }

    public double particleAltZ() {
        return particleAltZ;
    }

    public boolean randomAltX() {
        return randomAltX;
    }

    public boolean randomAltY() {
        return randomAltY;
    }

    public boolean randomAltZ() {
        return randomAltZ;
    }

    public String sound() {
        return sound;
    }

    public int soundDistance() {
        return soundDistance;
    }

    public double minPitch() {
        return minPitch;
    }

    public double maxPitch() {
        return maxPitch;
    }

    public double volume() {
        return volume;
    }

    public int particleCount() {
        return particleCount;
    }

    public int particleDistance() {
        return particleDistance;
    }

    public int particleDistanceWidth() {
        return particleDistanceWidth;
    }

    public double extra() {
        return extra;
    }

    public int potionStrength() {
        return potionStrength;
    }

    public int potionTicksMax() {
        return potionTicksMax;
    }

    public int potionTicksMin() {
        return potionTicksMin;
    }

    public int interval() {
        return interval;
    }

    public int particleAway() {
        return particleAway;
    }

    public int chance() {
        return chance;
    }

    /** The commands the effect runs; null for none. */
    public EffectCommands commandRegistry() {
        return commandRegistry;
    }
}
